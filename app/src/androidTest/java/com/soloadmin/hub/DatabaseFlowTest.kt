package com.soloadmin.hub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soloadmin.hub.data.database.SoloDatabase
import com.soloadmin.hub.data.repository.AttendanceRepository
import com.soloadmin.hub.data.repository.DataTransferRepository
import com.soloadmin.hub.data.repository.ExpenseRepository
import com.soloadmin.hub.data.repository.OfficialDutyRepository
import com.soloadmin.hub.data.repository.ProfileRepository
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.DutyMode
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import com.soloadmin.hub.domain.model.ProfileRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * On-device database contract tests.
 *
 * These run against a real in-memory Room database and cover the pieces the JVM tests cannot:
 * DAO queries, the attendance <-> official-duty link, and the snapshot round-trip that backs up
 * and restores every table.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseFlowTest {

    private lateinit var db: SoloDatabase
    private lateinit var attendance: AttendanceRepository
    private lateinit var duties: OfficialDutyRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var profile: ProfileRepository
    private lateinit var transfer: DataTransferRepository

    private val today = LocalDate.of(2026, 11, 23)
    private val todayKey = "2026-11-23"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SoloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        attendance = AttendanceRepository(db)
        duties = OfficialDutyRepository(db)
        expenses = ExpenseRepository(db)
        profile = ProfileRepository(db, com.soloadmin.hub.data.files.MediaStore(context))
        transfer = DataTransferRepository(db, com.soloadmin.hub.data.files.MediaStore(context), profile)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun attendanceUpsertKeepsOneRowPerDate() = runTest {
        attendance.upsert(AttendanceRecord(todayKey, AttendanceStatus.PRESENT, inTime = "09:30", outTime = "17:30"))
        attendance.upsert(AttendanceRecord(todayKey, AttendanceStatus.LATE, inTime = "09:55", outTime = "18:00"))

        val stored = attendance.get(todayKey)
        assertNotNull(stored)
        assertEquals(AttendanceStatus.LATE, stored!!.status)
        assertEquals("09:55", stored.inTime)
        assertEquals(1, attendance.observeAll().first().size)
    }

    @Test
    fun officialDutyWritesCalendarLinkBothWays() = runTest {
        val duty = OfficialDutyRecord(
            id = "duty-1",
            dateKey = todayKey,
            mode = DutyMode.FULL_DAY,
            startTime = "10:00",
            endTime = "16:00",
            meetingWith = "DEO Office",
            location = "Civil Lines"
        )
        duties.saveWithAttendance(duty)

        val record = attendance.get(todayKey)
        assertNotNull(record)
        assertEquals(AttendanceStatus.OUTSTATION, record!!.status)
        assertEquals("duty-1", record.officialDutyId)
        assertTrue(record.note.contains("DEO Office"))

        // Deleting the duty record removes the calendar link but keeps the day editable.
        duties.delete(duty)
        assertNull(duties.get("duty-1"))
    }

    @Test
    fun monthQueryOnlyReturnsThatMonth() = runTest {
        attendance.upsert(AttendanceRecord("2026-11-01", AttendanceStatus.PRESENT))
        attendance.upsert(AttendanceRecord("2026-11-30", AttendanceStatus.LEAVE, leaveType = "Casual Leave"))
        attendance.upsert(AttendanceRecord("2026-10-31", AttendanceStatus.PRESENT))

        val november = attendance.observeMonth("2026-11").first()
        assertEquals(2, november.size)
        assertTrue(november.all { it.dateKey.startsWith("2026-11") })
    }

    @Test
    fun snapshotRoundTripRestoresEveryGroup() = runTest {
        profile.save(ProfileRecord(name = "Test Admin", bio = "Clerk", shiftStart = "10:00", weeklyOffDay = 0))
        attendance.upsert(AttendanceRecord(todayKey, AttendanceStatus.LATE, inTime = "10:15", outTime = "18:00"))
        expenses.add(
            ExpenseRecord(
                id = "e1",
                dateKey = todayKey,
                title = "Stationery",
                category = "Office",
                amount = Money.ofRupees(1450),
                owner = ExpenseOwner.HEADMASTER
            )
        )

        val snapshot: BackupSnapshot = transfer.snapshot()
        assertEquals(1, snapshot.attendance.size)
        assertEquals(1, snapshot.expenses.size)
        assertEquals("Test Admin", snapshot.profile.name)

        transfer.wipeAll()
        assertTrue(transfer.snapshot().attendance.isEmpty())

        val decoded = com.soloadmin.hub.data.backup.BackupCodec.decode(
            com.soloadmin.hub.data.backup.BackupCodec.encode(snapshot, today)
        )
        val result = transfer.apply(decoded, com.soloadmin.hub.domain.model.ImportOptions())
        assertEquals(1, result.preview.items[com.soloadmin.hub.domain.model.ImportGroup.ATTENDANCE]?.imported)
        assertEquals("Test Admin", profile.get().name)
        assertEquals(1, attendance.observeAll().first().size)
    }

    @Test
    fun reimportingTheSameBackupDoesNotDuplicate() = runTest {
        attendance.upsert(AttendanceRecord(todayKey, AttendanceStatus.PRESENT, inTime = "09:30"))
        val snapshot = transfer.snapshot()
        val decoded = com.soloadmin.hub.data.backup.BackupCodec.decode(
            com.soloadmin.hub.data.backup.BackupCodec.encode(snapshot, today)
        )

        // Second import of identical data must be skipped, not duplicated.
        val again = transfer.apply(decoded, com.soloadmin.hub.domain.model.ImportOptions())
        assertTrue(again.preview.totalImported == 0)
        assertEquals(1, attendance.observeAll().first().size)
    }

    @Test
    fun headmasterDueMatchesStoredBills() = runTest {
        expenses.add(ExpenseRecord("a", todayKey, "Paper", "Office", Money.ofRupees(1200), ExpenseOwner.HEADMASTER))
        expenses.add(
            ExpenseRecord("b", todayKey, "Bus", "Travel", Money.ofRupees(320), ExpenseOwner.HEADMASTER, recovered = true)
        )
        expenses.add(ExpenseRecord("c", todayKey, "Tea", "Food", Money.ofRupees(180), ExpenseOwner.PERSONAL))

        val all = expenses.observeAll().first()
        val figures = all.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
        assertEquals("\u20B91,200", formatInr(FinancialCalculator.headmasterDue(figures)))
        assertEquals("\u20B9320", formatInr(FinancialCalculator.headmasterRecovered(figures)))
        assertEquals("\u20B9180", formatInr(FinancialCalculator.personalSpent(figures)))
    }
}

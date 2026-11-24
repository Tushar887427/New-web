package com.soloadmin.hub.domain

import com.soloadmin.hub.domain.logic.BackupMapper
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.ColleagueRecord
import com.soloadmin.hub.domain.model.DecodedBackup
import com.soloadmin.hub.domain.model.DutyMode
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.FinanceStatus
import com.soloadmin.hub.domain.model.ImportConflictPolicy
import com.soloadmin.hub.domain.model.ImportGroup
import com.soloadmin.hub.domain.model.ImportOptions
import com.soloadmin.hub.domain.model.ImportedAttendance
import com.soloadmin.hub.domain.model.ImportedColleague
import com.soloadmin.hub.domain.model.ImportedDuty
import com.soloadmin.hub.domain.model.ImportedExpense
import com.soloadmin.hub.domain.model.ImportedFinance
import com.soloadmin.hub.domain.model.ImportedKeyDate
import com.soloadmin.hub.domain.model.ImportedProfile
import com.soloadmin.hub.domain.model.ImportedTask
import com.soloadmin.hub.domain.model.NumOrText
import com.soloadmin.hub.domain.model.ProfileRecord
import com.soloadmin.hub.domain.model.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupMapperTest {

    private val today = LocalDate.of(2026, 11, 23)

    private val emptySnapshot = BackupSnapshot(
        profile = ProfileRecord(),
        attendance = emptyList(),
        duties = emptyList(),
        expenses = emptyList(),
        finances = emptyList(),
        keyDates = emptyList(),
        tasks = emptyList(),
        colleagues = emptyList(),
        workSessions = emptyList()
    )

    private fun decoded(
        attendance: List<ImportedAttendance> = emptyList(),
        expenses: List<ImportedExpense> = emptyList()
    ) = DecodedBackup(
        isNativeFormat = false,
        sourceVersion = null,
        profile = null,
        attendance = attendance,
        duties = emptyList(),
        expenses = expenses,
        finances = emptyList(),
        keyDates = emptyList(),
        tasks = emptyList(),
        colleagues = emptyList(),
        workSessions = emptyList()
    )

    @Test
    fun `parses a realistic legacy attendance payload`() {
        val d = decoded(
            attendance = listOf(
                ImportedAttendance("2026-11-02", "Present", null, "09:20", "17:05", ""),
                ImportedAttendance("2026-11-03", "Late", null, "09:55", "17:10", "Traffic delay"),
                ImportedAttendance("2026-11-04", "Leave", "Casual Leave", "", "", "Family errand"),
                ImportedAttendance("2026-11-06", "Outstation", null, "10:00", "16:30", "DEO Office Visit")
            )
        )
        val mapped = BackupMapper.toDomain(d, emptySnapshot, ImportOptions())
        val attendance = decodedAttendanceFrom(d)
        assertEquals(4, attendance.size)
        assertEquals(AttendanceStatus.LATE, attendance.first { it.dateKey == "2026-11-03" }.status)
        assertEquals("Casual Leave", attendance.first { it.dateKey == "2026-11-04" }.leaveType)
        assertNull(attendance.first { it.dateKey == "2026-11-04" }.inTime)
        assertNotNull(mapped)
    }

    @Test
    fun `invalid rows are skipped rather than guessed`() {
        val d = decoded(
            attendance = listOf(
                ImportedAttendance("2026-02-30", "Present", null, "09:20", "17:05", ""),
                ImportedAttendance("not-a-date", "Present", null, "09:20", "17:05", ""),
                ImportedAttendance("2026-11-05", "Nonsense", null, "", "", ""),
                ImportedAttendance("2026-11-06", "Present", null, "09:20", "17:05", "")
            )
        )
        val preview = BackupMapper.preview(d, emptySnapshot, ImportOptions(), today)
        assertEquals(1, preview.totalImported)
        assertEquals(3, preview.totalSkipped)
        val reasons = preview.skipped.map { it.reason }.toSet()
        assertTrue(reasons.contains(SkipReason.INVALID_DATE))
        assertTrue(reasons.contains(SkipReason.UNKNOWN_STATUS))
    }

    @Test
    fun `existing attendance is kept by default and updated on demand`() {
        val existing = emptySnapshot.copy(
            attendance = listOf(AttendanceRecord("2026-11-02", AttendanceStatus.PRESENT, inTime = "09:20"))
        )
        val d = decoded(
            attendance = listOf(ImportedAttendance("2026-11-02", "Late", null, "09:55", "17:05", ""))
        )
        val keep = BackupMapper.preview(d, existing, ImportOptions(), today)
        assertEquals(0, keep.totalImported)
        assertEquals(1, keep.countsFor(ImportGroup.ATTENDANCE).skipped)

        val overwrite = BackupMapper.preview(
            d, existing,
            ImportOptions(conflictPolicy = ImportConflictPolicy.OVERWRITE), today
        )
        assertEquals(1, overwrite.totalUpdated)
        assertEquals(0, overwrite.countsFor(ImportGroup.ATTENDANCE).skipped)
    }

    @Test
    fun `duplicate keys inside the same file are counted once`() {
        val d = decoded(
            attendance = listOf(
                ImportedAttendance("2026-11-02", "Present", null, "09:20", "17:05", ""),
                ImportedAttendance("2026-11-02", "Present", null, "09:20", "17:05", "")
            )
        )
        val preview = BackupMapper.preview(d, emptySnapshot, ImportOptions(), today)
        assertEquals(1, preview.totalImported)
        assertEquals(1, preview.totalSkipped)
    }

    @Test
    fun `expense amounts accept numbers and strings`() {
        val d = decoded(
            expenses = listOf(
                ImportedExpense("exp-1", "2026-11-06", "Stationery", "Office", NumOrText.Number(1450.0), "headmaster", false, "Voucher #112"),
                ImportedExpense("exp-2", "2026-11-09", "Auto fare", "Travel", NumOrText.Text("320"), "headmaster", true, "Treasury visit"),
                ImportedExpense("exp-3", "2026-11-23", "Lunch", "Food", NumOrText.Number(180.5), "personal", false, "")
            )
        )
        val mapped = BackupMapper.toDomain(d, emptySnapshot, ImportOptions())
        assertEquals(3, mapped.expenses.size)
        assertEquals("\u20B91,450", formatInr(mapped.expenses.first { it.id == "exp-1" }.amount))
        assertEquals(Money.ofRupees(320), mapped.expenses.first { it.id == "exp-2" }.amount)
        assertEquals(ExpenseOwner.PERSONAL, mapped.expenses.first { it.id == "exp-3" }.owner)
        assertTrue(mapped.expenses.first { it.id == "exp-2" }.recovered)
        assertEquals("\u20B9180.50", formatInr(mapped.expenses.first { it.id == "exp-3" }.amount))
    }

    @Test
    fun `zero and junk amounts are skipped`() {
        val d = decoded(
            expenses = listOf(
                ImportedExpense("exp-1", "2026-11-06", "Free sample", "Office", NumOrText.Number(0.0), "headmaster", false, ""),
                ImportedExpense("exp-2", "2026-11-06", "Broken", "Office", NumOrText.Text("lots"), "headmaster", false, ""),
                ImportedExpense("exp-3", "2026-11-06", "Negative", "Office", NumOrText.Number(-50.0), "headmaster", false, "")
            )
        )
        val preview = BackupMapper.preview(d, emptySnapshot, ImportOptions(), today)
        assertEquals(0, preview.totalImported)
        assertEquals(3, preview.totalSkipped)
        assertEquals(3, preview.skipped.count { it.reason == SkipReason.INVALID_AMOUNT })
    }

    @Test
    fun `finance status only received when explicitly received`() {
        val d = DecodedBackup(
            false, null, null, emptyList(), emptyList(), emptyList(),
            listOf(
                ImportedFinance("fin-1", "2026-11-01", "Salary", "income", NumOrText.Number(45000.0), "received"),
                ImportedFinance("fin-2", "2026-11-23", "TA/DA", "advance", NumOrText.Number(5000.0), "pending"),
                ImportedFinance("fin-3", "2026-11-23", "Weird", "income", NumOrText.Number(100.0), "whatever")
            ),
            emptyList(), emptyList(), emptyList(), emptyList()
        )
        val mapped = BackupMapper.toDomain(d, emptySnapshot, ImportOptions())
        assertEquals(3, mapped.finances.size)
        assertEquals(FinanceStatus.RECEIVED, mapped.finances.first { it.id == "fin-1" }.status)
        assertEquals(FinanceStatus.PENDING, mapped.finances.first { it.id == "fin-2" }.status)
        assertEquals(FinanceStatus.PENDING, mapped.finances.first { it.id == "fin-3" }.status)
        assertEquals("advance", mapped.finances.first { it.id == "fin-2" }.legacyType)
    }

    @Test
    fun `key dates reject inverted ranges`() {
        val d = DecodedBackup(
            false, null, null, emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(
                ImportedKeyDate("rem-1", "Fire NOC", "Compliance", "2025-12-05", "2026-12-05", "note"),
                ImportedKeyDate("rem-2", "Bad range", "Document", "2027-01-01", "2026-01-01", ""),
                ImportedKeyDate("rem-3", "No expiry", "Document", "2026-01-01", null, "")
            ),
            emptyList(), emptyList(), emptyList()
        )
        val mapped = BackupMapper.toDomain(d, emptySnapshot, ImportOptions())
        assertEquals(1, mapped.keyDates.size)
        val preview = BackupMapper.preview(d, emptySnapshot, ImportOptions(), today)
        assertEquals(2, preview.totalSkipped)
    }

    @Test
    fun `duty times are canonicalised and mode defaults safely`() {
        val d = DecodedBackup(
            false, null, null, emptyList(),
            listOf(
                ImportedDuty("oow-1", "2026-11-06", "Full Day", "9:00", "16:30", "DEO Office", "Collectorate", "Report", ""),
                ImportedDuty("oow-2", "2026-11-07", "Unknown", "10:00", "16:00", "BDO", "", "", null)
            ),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
        )
        val mapped = BackupMapper.toDomain(d, emptySnapshot, ImportOptions())
        assertEquals(2, mapped.duties.size)
        assertEquals("09:00", mapped.duties.first { it.id == "oow-1" }.startTime)
        assertEquals(DutyMode.FULL_DAY, mapped.duties.first { it.id == "oow-1" }.mode)
        assertEquals(DutyMode.FULL_DAY, mapped.duties.first { it.id == "oow-2" }.mode)
        assertEquals(450, mapped.duties.first { it.id == "oow-1" }.durationMinutes)
    }

    @Test
    fun `tasks and colleagues need text`() {
        val d = DecodedBackup(
            false, null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(ImportedTask("tsk-1", "Verify register", true), ImportedTask("tsk-2", "   ", false)),
            listOf(
                ImportedColleague("col-1", "Rajesh Sharma", "Headmaster", "sky", null, null, emptyList()),
                ImportedColleague("col-2", "", "No name", "mint", null, null, emptyList())
            ),
            emptyList()
        )
        val preview = BackupMapper.preview(d, emptySnapshot, ImportOptions(), today)
        assertEquals(1, preview.countsFor(ImportGroup.TASKS).imported)
        assertEquals(1, preview.countsFor(ImportGroup.COLLEAGUES).imported)
        val mapped = BackupMapper.toDomain(d, emptySnapshot, ImportOptions())
        assertEquals("RS", mapped.colleagues.first().initials)
    }

    @Test
    fun `profile defaults are safe and shift times validated`() {
        val d = DecodedBackup(
            false, null,
            ImportedProfile("Meera Patil", "Head Clerk", null, NumOrText.Number(6.0), "25:00",
                listOf(com.soloadmin.hub.domain.model.ImportedInfo("ID", "GOV-1042"))),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
        )
        val profile = BackupMapper.mapProfile(d)
        assertNotNull(profile)
        assertEquals("Meera Patil", profile!!.name)
        assertEquals(6, profile.weeklyOffDay)
        assertEquals("09:30", profile.shiftStart)
        assertEquals("GOV-1042", profile.info.first().value)
    }

    @Test
    fun `weekly off is clamped to valid days`() {
        val d = DecodedBackup(
            false, null,
            ImportedProfile("X", null, null, NumOrText.Number(42.0), "09:30", emptyList()),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
        )
        assertEquals(0, BackupMapper.mapProfile(d)!!.weeklyOffDay)
    }

    @Test
    fun `data urls decode only image payloads`() {
        val png = "data:image/png;base64,iVBORw0KGgo="
        val decodedBytes = BackupMapper.decodeDataUrl(png)
        assertNotNull(decodedBytes)
        assertTrue(decodedBytes!!.size > 5)
        assertNull(BackupMapper.decodeDataUrl("data:text/plain;base64,aGVsbG8="))
        assertNull(BackupMapper.decodeDataUrl("not a data url"))
        assertNull(BackupMapper.decodeDataUrl(null))
    }

    @Test
    fun `time sanitiser accepts lenient formats`() {
        assertEquals("09:05", BackupMapper.sanitizeTime("9:05"))
        assertEquals("09:05", BackupMapper.sanitizeTime("09:05:00"))
        assertEquals("23:59", BackupMapper.sanitizeTime("23:59"))
        assertNull(BackupMapper.sanitizeTime("24:00"))
        assertNull(BackupMapper.sanitizeTime(null))
    }

    private fun decodedAttendanceFrom(d: DecodedBackup): List<AttendanceRecord> =
        d.attendance.mapNotNull { row ->
            val date = com.soloadmin.hub.domain.logic.DateKeys.parseOrNull(row.dateKey) ?: return@mapNotNull null
            val status = AttendanceStatus.fromStorage(row.status) ?: return@mapNotNull null
            AttendanceRecord(
                dateKey = com.soloadmin.hub.domain.logic.DateKeys.of(date),
                status = status,
                leaveType = row.leaveType?.takeIf { it.isNotBlank() },
                inTime = row.inTime?.takeIf { it.isNotBlank() },
                outTime = row.outTime?.takeIf { it.isNotBlank() },
                note = row.note.orEmpty()
            )
        }

    @Test
    fun `expense owner defaults to personal for unknown values`() {
        val d = decoded(
            expenses = listOf(
                ImportedExpense("exp-9", "2026-11-06", "Mystery", "Office", NumOrText.Number(10.0), "who", false, "")
            )
        )
        val mapped = BackupMapper.toDomain(d, emptySnapshot, ImportOptions())
        assertEquals(ExpenseOwner.PERSONAL, mapped.expenses.first().owner)
    }

    @Test
    fun `snapshot with existing ids marks imported records as updates only when overwriting`() {
        val existing = emptySnapshot.copy(
            expenses = listOf(
                ExpenseRecord("exp-1", "2026-11-06", "Stationery", "Office", Money.ofRupees(1450), ExpenseOwner.HEADMASTER)
            )
        )
        val d = decoded(
            expenses = listOf(
                ImportedExpense("exp-1", "2026-11-06", "Stationery v2", "Office", NumOrText.Number(1500.0), "headmaster", true, "")
            )
        )
        val keep = BackupMapper.preview(d, existing, ImportOptions(), today)
        assertEquals(1, keep.countsFor(ImportGroup.EXPENSES).skipped)
        val overwrite = BackupMapper.preview(d, existing, ImportOptions(conflictPolicy = ImportConflictPolicy.OVERWRITE), today)
        assertEquals(1, overwrite.countsFor(ImportGroup.EXPENSES).updated)
    }

    @Test
    fun `colleagues carry optional info fields`() {
        val d = DecodedBackup(
            false, null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(
                ImportedColleague(
                    "col-1", "Ananya Das", "Senior Clerk", "mint", null, null,
                    listOf(
                        com.soloadmin.hub.domain.model.ImportedInfo("Desk", "B-04"),
                        com.soloadmin.hub.domain.model.ImportedInfo("", "")
                    )
                )
            ),
            emptyList()
        )
        val colleague: ColleagueRecord = BackupMapper.toDomain(d, emptySnapshot, ImportOptions()).colleagues.first()
        assertEquals(1, colleague.info.size)
        assertEquals("B-04", colleague.info.first().value)
    }
}

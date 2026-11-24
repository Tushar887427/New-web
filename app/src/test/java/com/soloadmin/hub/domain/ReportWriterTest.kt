package com.soloadmin.hub.domain

import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.DailyTaskRecord
import com.soloadmin.hub.domain.model.DutyMode
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.FinanceStatus
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import com.soloadmin.hub.domain.model.ProfileRecord
import com.soloadmin.hub.domain.model.WorkSession
import com.soloadmin.hub.util.ReportWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * CSV report contracts. The PdfDocument path needs the Android framework so it is exercised on a
 * device, but every CSV builder is pure Kotlin and is verified here.
 */
class ReportWriterTest {

    private val today = LocalDate.of(2026, 11, 23)

    private val snapshot = BackupSnapshot(
        profile = ProfileRecord(name = "Test Admin"),
        attendance = listOf(
            AttendanceRecord("2026-11-20", AttendanceStatus.LATE, inTime = "09:55", outTime = "17:30", note = "Bus delay"),
            AttendanceRecord("2026-11-19", AttendanceStatus.LEAVE, leaveType = "Casual Leave")
        ),
        duties = listOf(
            OfficialDutyRecord(
                id = "d1", dateKey = "2026-11-18", mode = DutyMode.FULL_DAY,
                startTime = "10:00", endTime = "16:30",
                meetingWith = "DEO Office, Nagpur", location = "Civil Lines",
                remarks = "Verification, \"urgent\"", attachmentPath = null
            )
        ),
        expenses = listOf(
            ExpenseRecord("e1", "2026-11-06", "Stationery", "Office", Money.ofRupees(1450), ExpenseOwner.HEADMASTER)
        ),
        finances = listOf(
            FinanceRecord("f1", "2026-11-01", "Salary", Money.ofRupees(45_000), FinanceStatus.RECEIVED)
        ),
        keyDates = listOf(
            KeyDateRecord("k1", "Trade licence", "License", "2026-01-01", "2026-12-10"),
            KeyDateRecord("k2", "Lease deed", "Contract", "2026-01-01", "2026-11-01")
        ),
        tasks = listOf(DailyTaskRecord("t1", "Verify register")),
        colleagues = emptyList(),
        workSessions = listOf(WorkSession("2026-11-20", inTime = "09:55", outTime = "17:30"))
    )

    @Test
    fun `duty csv lists visits with durations and a total row`() {
        val csv = ReportWriter.dutyCsv(snapshot)
        val lines = csv.split("\r\n")
        assertEquals("Date,Mode,Start,End,DurationMinutes,OfficeVisited,Location,Remarks,Attachment", lines.first())
        assertTrue(csv.contains("2026-11-18,Full Day,10:00,16:30,390"))
        assertTrue(csv.contains("DEO Office, Nagpur"))
        assertTrue(csv.contains("\"Verification, \"\"urgent\"\"\""))
        assertTrue(csv.contains("Total,1 visits,,,,390 minutes"))
    }

    @Test
    fun `duty csv keeps an empty snapshot valid`() {
        val empty = snapshot.copy(duties = emptyList())
        val lines = ReportWriter.dutyCsv(empty).split("\r\n")
        assertEquals(3, lines.size)
        assertEquals("Date,Mode,Start,End,DurationMinutes,OfficeVisited,Location,Remarks,Attachment", lines[0])
        assertEquals("", lines[1])
        assertTrue(lines[2].startsWith("Total,0 visits"))
    }

    @Test
    fun `reminders csv orders by urgency and labels status`() {
        val csv = ReportWriter.remindersCsv(snapshot, today)
        val lines = csv.split("\r\n")
        assertEquals("Status,Title,Category,ValidFrom,ValidUntil,DaysRemaining,Note", lines.first())
        val lease = lines.first { it.startsWith("Expired,Lease deed") }
        assertTrue(lease.contains(",2026-11-01,-22"))
        val licence = lines.first { it.startsWith("Due soon,Trade licence") }
        assertTrue(licence.contains(",2026-12-10,17"))
    }

    @Test
    fun `attendance csv carries legacy headers`() {
        val csv = ReportWriter.attendanceCsv(snapshot.attendance)
        assertTrue(csv.isNotBlank())
        assertTrue(csv.contains("2026-11-20"))
        assertTrue(csv.contains("Late"))
    }

    @Test
    fun `expense csv includes the recoverable summary`() {
        val csv = ReportWriter.expenseCsv(snapshot)
        assertTrue(csv.contains("Stationery"))
        assertTrue(csv.contains("Headmaster due,1,450"))
        assertTrue(csv.contains("Personal spent,0"))
    }

    @Test
    fun `finance csv marks pending bills`() {
        val csv = ReportWriter.financeCsv(snapshot.copy(finances = snapshot.finances + FinanceRecord(
            "f2", "2026-11-20", "TA bill", Money.ofRupees(5_000), FinanceStatus.PENDING
        )))
        assertTrue(csv.contains("Salary"))
        assertTrue(csv.contains("Pending"))
    }

    @Test
    fun `key date csv reports days remaining`() {
        val csv = ReportWriter.keyDatesCsv(snapshot, today)
        assertTrue(csv.contains("Trade licence"))
        assertTrue(csv.contains("17"))
    }

    @Test
    fun `snapshot record count matches the sum of groups`() {
        assertEquals(8, snapshot.recordCount)
    }
}

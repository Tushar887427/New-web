package com.soloadmin.hub.data.seed

import com.soloadmin.hub.data.database.AttendanceEntity
import com.soloadmin.hub.data.database.ColleagueEntity
import com.soloadmin.hub.data.database.ColleagueInfoEntity
import com.soloadmin.hub.data.database.DailyTaskEntity
import com.soloadmin.hub.data.database.ExpenseEntity
import com.soloadmin.hub.data.database.FinanceEntity
import com.soloadmin.hub.data.database.KeyDateEntity
import com.soloadmin.hub.data.database.OfficialDutyEntity
import com.soloadmin.hub.data.database.ProfileEntity
import com.soloadmin.hub.data.database.ProfileInfoEntity
import com.soloadmin.hub.data.database.SoloDatabase
import java.time.LocalDate
import java.time.YearMonth

/**
 * The exact sample content of the legacy application's `createDefaultState()`.
 *
 * It exists for one reason: a brand-new install should look like the user's current app instead
 * of an empty shell, and "Reset sample data" must restore exactly what the old app restored.
 * Day numbers follow the legacy `dKey(day)` helper (days are clamped to 28 of the current month).
 */
object SampleData {

    fun seed(db: SoloDatabase, today: LocalDate) {
        val month = YearMonth.from(today)
        fun day(dayOfMonth: Int): String =
            LocalDate.of(month.year, month.monthValue, dayOfMonth.coerceIn(1, 28)).toString()

        val attendance = listOf(
            AttendanceEntity(day(2), "Present", inTime = "09:20", outTime = "17:05"),
            AttendanceEntity(day(3), "Late", inTime = "09:55", outTime = "17:10", note = "Traffic delay"),
            AttendanceEntity(day(4), "Leave", leaveType = "Casual Leave", note = "Family errand"),
            AttendanceEntity(day(5), "Present", inTime = "09:15", outTime = "17:00"),
            AttendanceEntity(day(6), "Outstation", inTime = "10:00", outTime = "16:30", note = "DEO Office Visit", officialDutyId = "oow-1"),
            AttendanceEntity(day(9), "Present", inTime = "09:22", outTime = "17:02"),
            AttendanceEntity(day(10), "Early_Going", inTime = "09:18", outTime = "15:30", note = "Bank work"),
            AttendanceEntity(day(11), "Leave", leaveType = "Earned Leave", note = "Planned leave")
        )

        val duty = OfficialDutyEntity(
            id = "oow-1",
            dateKey = day(6),
            mode = "Full Day",
            startTime = "10:00",
            endTime = "16:30",
            meetingWith = "District Education Office (DEO)",
            location = "Collectorate Complex",
            remarks = "Submitted quarterly grant utilization report.",
            createdAt = System.currentTimeMillis()
        )

        val expenses = listOf(
            ExpenseEntity("exp-1", day(6), "Office Stationery & Registers", "Office", 145_000, "headmaster", false, "Voucher #112"),
            ExpenseEntity("exp-2", day(9), "Official Travel Auto Fare", "Travel", 32_000, "headmaster", true, "Treasury visit"),
            ExpenseEntity("exp-3", today.toString(), "Lunch & Tea", "Food", 18_000, "personal", false, "")
        )

        val finances = listOf(
            FinanceEntity("fin-1", day(1), "Monthly Salary", 4_500_000, "received"),
            FinanceEntity("fin-2", today.toString(), "TA / DA & Duty Allowance", 500_000, "pending")
        )

        val reminders = listOf(
            KeyDateEntity("rem-1", "Institution Registration Certificate", "Document", "2026-01-01", "2027-02-03", "Renew 30 days before expiry with updated audit report."),
            KeyDateEntity("rem-2", "Fire Safety NOC", "Compliance", "2025-12-05", "2026-12-05", "Schedule inspection with local fire department."),
            KeyDateEntity("rem-3", "Broadband & Domain Contract", "Contract", "2025-11-10", "2026-11-10", "Invoice pending renewal.")
        )

        val tasks = listOf(
            DailyTaskEntity("tsk-1", "Verify monthly attendance register", true, completedAt = System.currentTimeMillis(), sortOrder = 0),
            DailyTaskEntity("tsk-2", "Submit Headmaster expense vouchers for reimbursement", false, sortOrder = 1)
        )

        val colleagues = listOf(
            ColleagueEntity("col-1", "Rajesh Sharma", "Headmaster — Oversees school & office administration.", "sky", sortOrder = 0),
            ColleagueEntity("col-2", "Ananya Das", "Senior Clerk — Accounts, vouchers & official records.", "mint", sortOrder = 1)
        )
        val colleagueInfo = listOf(
            ColleagueInfoEntity("col-1", 0, "ID", "HM-001"),
            ColleagueInfoEntity("col-1", 1, "Room", "101"),
            ColleagueInfoEntity("col-1", 2, "Mobile", "+91 98765 43210"),
            ColleagueInfoEntity("col-2", 0, "Desk", "B-04"),
            ColleagueInfoEntity("col-2", 1, "Mobile", "+91 91234 56789"),
            ColleagueInfoEntity("col-2", 2, "Note", "Handles monthly reimbursement vouchers every Friday.")
        )

        val profile = ProfileEntity(
            name = "Admin",
            bio = "Government Office Staff managing daily attendance, official bills & records.",
            weeklyOffDay = 0,
            shiftStart = "09:30"
        )
        val profileInfo = listOf(
            ProfileInfoEntity(1, 0, "ID", "GOV-1042"),
            ProfileInfoEntity(1, 1, "Office", "Main Block"),
            ProfileInfoEntity(1, 2, "Time", "09:30 AM"),
            ProfileInfoEntity(1, 3, "Mobile", "+91 98000 00000")
        )

        db.runInTransaction {
            db.attendanceDao().upsertAll(attendance)
            db.officialDutyDao().upsert(duty)
            db.expenseDao().upsertAll(expenses)
            db.financeDao().upsertAll(finances)
            db.keyDateDao().upsertAll(reminders)
            db.dailyTaskDao().upsertAll(tasks)
            db.colleagueDao().upsertAll(colleagues)
            db.colleagueDao().insertInfo(colleagueInfo)
            db.profileDao().upsert(profile)
            db.profileDao().insertInfo(profileInfo)
        }
    }
}

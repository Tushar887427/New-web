package com.soloadmin.hub.domain.logic

import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.ExpenseOwner

/** Minimal shapes the calculator needs; keeps it free of Room and Compose types. */
data class ExpenseFigures(
    val owner: ExpenseOwner,
    val amount: Money,
    val recovered: Boolean,
    val dateKey: String,
    val category: String
)

data class FinanceFigures(
    val amount: Money,
    val received: Boolean,
    val dateKey: String
)

/**
 * Every money rule in the legacy app, in one auditable place.
 *
 * Legacy reference (`renderHome`, `renderExpenses`, `renderFinance`):
 *   hmDue        = Σ headmaster && !recovered
 *   hmRecovered  = Σ headmaster && recovered
 *   personalSpent= Σ personal                        (recovered flag ignored)
 *   totalReceived= Σ finances where status == received
 *   totalPending = Σ finances where status != received
 *   netBalance   = totalReceived − personalSpent
 */
object FinancialCalculator {

    fun headmasterDue(expenses: List<ExpenseFigures>): Money =
        expenses.filter { it.owner == ExpenseOwner.HEADMASTER && !it.recovered }
            .fold(Money.ZERO) { acc, item -> acc + item.amount }

    fun headmasterRecovered(expenses: List<ExpenseFigures>): Money =
        expenses.filter { it.owner == ExpenseOwner.HEADMASTER && it.recovered }
            .fold(Money.ZERO) { acc, item -> acc + item.amount }

    fun headmasterTotal(expenses: List<ExpenseFigures>): Money =
        expenses.filter { it.owner == ExpenseOwner.HEADMASTER }
            .fold(Money.ZERO) { acc, item -> acc + item.amount }

    fun personalSpent(expenses: List<ExpenseFigures>): Money =
        expenses.filter { it.owner == ExpenseOwner.PERSONAL }
            .fold(Money.ZERO) { acc, item -> acc + item.amount }

    fun totalExpenses(expenses: List<ExpenseFigures>): Money =
        expenses.fold(Money.ZERO) { acc, item -> acc + item.amount }

    fun totalReceived(finances: List<FinanceFigures>): Money =
        finances.filter { it.received }.fold(Money.ZERO) { acc, item -> acc + item.amount }

    fun totalPending(finances: List<FinanceFigures>): Money =
        finances.filter { !it.received }.fold(Money.ZERO) { acc, item -> acc + item.amount }

    fun netBalance(finances: List<FinanceFigures>, expenses: List<ExpenseFigures>): Money =
        totalReceived(finances) - personalSpent(expenses)

    fun categoryTotals(expenses: List<ExpenseFigures>): List<Pair<String, Money>> =
        expenses.groupBy { it.category.ifBlank { "Other" } }
            .map { (category, items) -> category to items.fold(Money.ZERO) { acc, e -> acc + e.amount } }
            .sortedByDescending { it.second.paise }

    /** Highest single expense amount, useful to scale chart axes. */
    fun maxAmount(expenses: List<ExpenseFigures>): Money =
        expenses.maxOfOrNull { it.amount.paise }?.let { Money(it) } ?: Money.ZERO
}

/** Attendance counters used by Home and the Activity Ledger. */
data class AttendanceCounters(
    val present: Int,
    val officialDuty: Int,
    val casualLeave: Int,
    val earnedLeave: Int,
    val commutedLeave: Int,
    val absent: Int,
    val late: Int,
    val earlyGoing: Int,
    val holiday: Int,
    val offday: Int,
    val missPunch: Int
) {
    val totalLeaves: Int get() = casualLeave + earnedLeave + commutedLeave
    /** `present` already includes late and early-going days, matching the legacy home counter. */
    val totalTrackedDays: Int
        get() = present + officialDuty + totalLeaves + absent + holiday + offday + missPunch
}

object AttendanceCalculator {

    /**
     * Legacy home widget counted `Present`, `Late`, `Early_Going` as one "present" bucket and
     * `Outstation` separately. `late`/`earlyGoing` are reported both inside [AttendanceCounters.present]
     * and individually so screens can drill down without re-scanning records.
     */
    fun counters(records: List<Pair<String, AttendanceStatus?>>, leaveTypes: List<Pair<String, String?>>): AttendanceCounters {
        var present = 0
        var official = 0
        var casual = 0
        var earned = 0
        var commuted = 0
        var absent = 0
        var late = 0
        var early = 0
        var holiday = 0
        var offday = 0
        var missPunch = 0
        records.forEach { (_, status) ->
            when (status) {
                AttendanceStatus.PRESENT -> present++
                AttendanceStatus.LATE -> { present++; late++ }
                AttendanceStatus.EARLY_GOING -> { present++; early++ }
                AttendanceStatus.OUTSTATION -> official++
                AttendanceStatus.ABSENT -> absent++
                AttendanceStatus.HOLIDAY -> holiday++
                AttendanceStatus.OFFDAY -> offday++
                AttendanceStatus.MISS_PUNCH -> missPunch++
                AttendanceStatus.LEAVE -> Unit
                null -> Unit
            }
        }
        leaveTypes.forEach { (_, type) ->
            when (type) {
                com.soloadmin.hub.domain.model.LeaveTypes.CASUAL -> casual++
                com.soloadmin.hub.domain.model.LeaveTypes.EARNED -> earned++
                com.soloadmin.hub.domain.model.LeaveTypes.COMMUTED -> commuted++
                else -> casual++
            }
        }
        return AttendanceCounters(
            present = present,
            officialDuty = official,
            casualLeave = casual,
            earnedLeave = earned,
            commutedLeave = commuted,
            absent = absent,
            late = late,
            earlyGoing = early,
            holiday = holiday,
            offday = offday,
            missPunch = missPunch
        )
    }

    /** Ledger "Total Logged Records" figure from the legacy app. */
    fun ledgerTotal(counters: AttendanceCounters, officialDutyRecords: Int, expenseCount: Int): Int =
        counters.present + officialDutyRecords + counters.casualLeave + counters.earnedLeave +
            counters.commutedLeave + expenseCount

    fun workedMinutes(inTime: String?, outTime: String?, breakMinutes: Int): Int? {
        val start = com.soloadmin.hub.domain.logic.DateKeys.minutesOfDay(inTime) ?: return null
        val end = com.soloadmin.hub.domain.logic.DateKeys.minutesOfDay(outTime) ?: return null
        if (end <= start) return null
        return (end - start - breakMinutes.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours == 0 -> "${mins}m"
            mins == 0 -> "${hours}h"
            else -> "${hours}h ${mins}m"
        }
    }
}

package com.soloadmin.hub.domain

import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.AttendanceCounters
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinanceFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.LeaveTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCalculatorTest {

    private val expenses = listOf(
        ExpenseFigures(ExpenseOwner.HEADMASTER, Money.ofRupees(1450), false, "2026-11-06", "Office"),
        ExpenseFigures(ExpenseOwner.HEADMASTER, Money.ofRupees(320), true, "2026-11-09", "Travel"),
        ExpenseFigures(ExpenseOwner.PERSONAL, Money.ofRupees(180), false, "2026-11-23", "Food"),
        ExpenseFigures(ExpenseOwner.HEADMASTER, Money(49950), false, "2026-11-20", "Printing")
    )

    private val finances = listOf(
        FinanceFigures(Money.ofRupees(45_000), true, "2026-11-01"),
        FinanceFigures(Money.ofRupees(5_000), false, "2026-11-23")
    )

    @Test
    fun `headmaster due excludes recovered bills`() {
        assertEquals("\u20B91,949.50", formatInr(FinancialCalculator.headmasterDue(expenses)))
    }

    @Test
    fun `headmaster recovered sums only settled bills`() {
        assertEquals("\u20B9320", formatInr(FinancialCalculator.headmasterRecovered(expenses)))
    }

    @Test
    fun `personal spend ignores the recovered flag`() {
        assertEquals("\u20B9180", formatInr(FinancialCalculator.personalSpent(expenses)))
        val flagged = expenses.map { if (it.owner == ExpenseOwner.PERSONAL) it.copy(recovered = true) else it }
        assertEquals("\u20B9180", formatInr(FinancialCalculator.personalSpent(flagged)))
    }

    @Test
    fun `received and pending split matches legacy rules`() {
        assertEquals("\u20B945,000", formatInr(FinancialCalculator.totalReceived(finances)))
        assertEquals("\u20B95,000", formatInr(FinancialCalculator.totalPending(finances)))
    }

    @Test
    fun `net balance is received minus personal spend`() {
        assertEquals("\u20B944,820", formatInr(FinancialCalculator.netBalance(finances, expenses)))
    }

    @Test
    fun `net balance can be negative`() {
        val small = listOf(FinanceFigures(Money.ofRupees(100), true, "2026-11-01"))
        assertEquals("\u2212\u20B980", formatInr(FinancialCalculator.netBalance(small, expenses)))
    }

    @Test
    fun `empty ledgers are zero not null`() {
        assertEquals(Money.ZERO, FinancialCalculator.totalReceived(emptyList()))
        assertEquals(Money.ZERO, FinancialCalculator.headmasterDue(emptyList()))
        assertEquals(Money.ZERO, FinancialCalculator.netBalance(emptyList(), emptyList()))
    }

    @Test
    fun `category totals are grouped and sorted`() {
        val totals = FinancialCalculator.categoryTotals(expenses)
        assertEquals("Office", totals.first().first)
        assertEquals(Money.ofRupees(1450), totals.first().second)
        assertEquals(4, totals.size)
    }

    @Test
    fun `attendance counters follow legacy buckets`() {
        val records = listOf(
            "2026-11-02" to AttendanceStatus.PRESENT,
            "2026-11-03" to AttendanceStatus.LATE,
            "2026-11-10" to AttendanceStatus.EARLY_GOING,
            "2026-11-06" to AttendanceStatus.OUTSTATION,
            "2026-11-04" to AttendanceStatus.LEAVE,
            "2026-11-11" to AttendanceStatus.LEAVE,
            "2026-11-15" to AttendanceStatus.ABSENT,
            "2026-11-16" to AttendanceStatus.HOLIDAY,
            "2026-11-17" to AttendanceStatus.MISS_PUNCH
        )
        val leaves = listOf(
            "2026-11-04" to LeaveTypes.CASUAL,
            "2026-11-11" to LeaveTypes.EARNED
        )
        val counters = AttendanceCalculator.counters(records, leaves)
        assertEquals(3, counters.present)
        assertEquals(1, counters.officialDuty)
        assertEquals(1, counters.casualLeave)
        assertEquals(1, counters.earnedLeave)
        assertEquals(0, counters.commutedLeave)
        assertEquals(1, counters.absent)
        assertEquals(1, counters.late)
        assertEquals(1, counters.earlyGoing)
        assertEquals(1, counters.holiday)
        assertEquals(1, counters.missPunch)
        assertEquals(9, counters.totalTrackedDays)
    }

    @Test
    fun `ledger total matches legacy formula`() {
        val counters = AttendanceCounters(10, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(10 + 2 + 1 + 1 + 0 + 3, AttendanceCalculator.ledgerTotal(counters, 2, 3))
    }

    @Test
    fun `worked minutes handles invalid input`() {
        assertEquals(450, AttendanceCalculator.workedMinutes("09:30", "17:00", 0))
        assertEquals(420, AttendanceCalculator.workedMinutes("09:30", "17:00", 30))
        assertEquals(null, AttendanceCalculator.workedMinutes("", "17:00", 0))
        assertEquals(null, AttendanceCalculator.workedMinutes("17:00", "09:30", 0))
        assertEquals("7h 30m", AttendanceCalculator.formatDuration(450))
        assertEquals("45m", AttendanceCalculator.formatDuration(45))
        assertEquals("2h", AttendanceCalculator.formatDuration(120))
    }
}

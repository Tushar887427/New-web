package com.soloadmin.hub.domain

import com.soloadmin.hub.domain.logic.DashboardMath
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.domain.model.ProfileRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The derived dashboard figures the user acts on: weekly-off detection, the monthly net balance and
 * the key-date attention window. All three are pinned to the legacy app's behaviour.
 */
class DashboardStateTest {

    /** A Monday. */
    private val monday = LocalDate.of(2026, 11, 23)

    @Test
    fun `weekly off uses the legacy sunday first index`() {
        assertTrue(DashboardMath.isWeeklyOff(monday, 1))
        assertFalse(DashboardMath.isWeeklyOff(monday, 0))
        assertEquals("Sun", ProfileRecord(weeklyOffDay = 0).weeklyOffLabel)
    }

    @Test
    fun `an out of range weekly off index does not crash`() {
        assertFalse(DashboardMath.isWeeklyOff(monday, 42))
        assertFalse(DashboardMath.isWeeklyOff(monday, -3))
    }

    @Test
    fun `monthly net balance subtracts personal spend from received pay`() {
        val net = DashboardMath.monthNet(Money.ofRupees(45_000), Money.ofRupees(180))
        assertEquals(Money.ofRupees(44_820), net)
        assertFalse(net.isNegative)
    }

    @Test
    fun `overspending shows a negative net balance`() {
        val net = DashboardMath.monthNet(Money.ofRupees(1_000), Money.ofRupees(2_500))
        assertTrue(net.isNegative)
        assertEquals(Money.ofRupees(-1_500), net)
    }

    @Test
    fun `attention window matches the legacy sixty day badge`() {
        assertTrue(DashboardMath.needsAttention(-1))
        assertTrue(DashboardMath.needsAttention(0))
        assertTrue(DashboardMath.needsAttention(60))
        assertFalse(DashboardMath.needsAttention(61))
        assertEquals(60, DashboardMath.ATTENTION_WINDOW_DAYS)
    }

    @Test
    fun `expiring key dates are ordered by expiry`() {
        val records = listOf(
            KeyDateRecord("k1", "Later", "Document", "2026-01-01", "2027-03-01"),
            KeyDateRecord("k2", "Sooner", "Document", "2026-01-01", "2026-12-05"),
            KeyDateRecord("k3", "Expired", "Document", "2026-01-01", "2026-11-01")
        )
        val withinWindow = records
            .filter { DashboardMath.needsAttention(it.daysUntilExpiry(monday)) }
            .sortedBy { it.validUntil }
        assertEquals(listOf("k3", "k2"), withinWindow.map { it.id })
        assertEquals(-22, records[2].daysUntilExpiry(monday))
        assertEquals(12, records[1].daysUntilExpiry(monday))
    }
}

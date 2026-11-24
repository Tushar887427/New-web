package com.soloadmin.hub.domain

import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.logic.formatInrCompact
import com.soloadmin.hub.domain.logic.groupIndian
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `indian grouping matches en-IN formatting`() {
        assertEquals("0", groupIndian(0))
        assertEquals("999", groupIndian(999))
        assertEquals("1,450", groupIndian(1450))
        assertEquals("45,000", groupIndian(45000))
        assertEquals("1,23,456", groupIndian(123456))
        assertEquals("12,34,567", groupIndian(1234567))
        assertEquals("1,23,45,678", groupIndian(12345678))
    }

    @Test
    fun `formatInr renders rupees without decimals when whole`() {
        assertEquals("\u20B91,450", formatInr(Money.ofRupees(1450)))
        assertEquals("\u20B945,000", formatInr(Money.ofRupees(45_000)))
        assertEquals("\u20B91,23,456", formatInr(Money.ofRupees(123_456)))
    }

    @Test
    fun `formatInr keeps non zero paise`() {
        assertEquals("\u20B91,450.50", formatInr(Money(145050)))
        assertEquals("\u20B91,450.05", formatInr(Money(145005)))
        assertEquals("\u20B90.01", formatInr(Money(1)))
    }

    @Test
    fun `negative amounts use a true minus sign`() {
        val value = formatInr(Money.ofRupees(45_000) - Money.ofRupees(46_200))
        assertEquals("\u2212\u20B91,200", value)
    }

    @Test
    fun `parse accepts messy user input`() {
        assertEquals(Money.ofRupees(1450), Money.parseRupees("1450"))
        assertEquals(Money.ofRupees(1450), Money.parseRupees(" 1,450 "))
        assertEquals(Money(145050), Money.parseRupees("₹1,450.50"))
        assertEquals(Money(145050), Money.parseRupees("1450.5"))
        assertEquals(Money(-32000), Money.parseRupees("-320"))
        assertNull(Money.parseRupees("abc"))
        assertNull(Money.parseRupees(""))
        assertNull(Money.parseRupees("1.2.3"))
        assertNull(Money.parseRupees("12.345"))
    }

    @Test
    fun `arithmetic never loses paise`() {
        val a = Money(1)
        val sum = (1..100).fold(Money.ZERO) { acc, _ -> acc + a }
        assertEquals(Money(100), sum)
        assertEquals("₹1", formatInr(sum))
    }

    @Test
    fun `compact formatting for charts`() {
        assertEquals("₹45k", formatInrCompact(Money.ofRupees(45_000)))
        assertEquals("₹1.5L", formatInrCompact(Money.ofRupees(150_000)))
        assertEquals("₹2Cr", formatInrCompact(Money.ofRupees(20_000_000)))
        assertTrue(formatInrCompact(Money.ofRupees(500)).startsWith("₹"))
    }

    @Test
    fun `fromRupeeDouble rounds to nearest paise`() {
        assertEquals(Money(145000), Money.fromRupeeDouble(1450.0))
        assertEquals(Money(145050), Money.fromRupeeDouble(1450.5))
        assertEquals(Money.ZERO, Money.fromRupeeDouble(Double.NaN))
    }
}

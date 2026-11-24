package com.soloadmin.hub.domain

import com.soloadmin.hub.domain.logic.DateKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DateKeysTest {

    @Test
    fun `date keys round trip`() {
        val date = LocalDate.of(2026, 11, 23)
        assertEquals("2026-11-23", DateKeys.of(date))
        assertEquals(date, DateKeys.parseOrNull("2026-11-23"))
    }

    @Test
    fun `invalid dates are rejected not guessed`() {
        assertNull(DateKeys.parseOrNull("2026-02-30"))
        assertNull(DateKeys.parseOrNull("2026-13-01"))
        assertNull(DateKeys.parseOrNull(""))
        assertNull(DateKeys.parseOrNull(null))
        assertNull(DateKeys.parseOrNull("23-11-2026"))
        assertEquals(LocalDate.of(2026, 11, 23), DateKeys.parseOrNull("2026-11-23"))
    }

    @Test
    fun `days until matches legacy rounding across month boundaries`() {
        val today = LocalDate.of(2026, 11, 23)
        assertEquals(0, DateKeys.daysUntil(LocalDate.of(2026, 11, 23), today))
        assertEquals(1, DateKeys.daysUntil(LocalDate.of(2026, 11, 24), today))
        assertEquals(8, DateKeys.daysUntil(LocalDate.of(2026, 12, 1), today))
        assertEquals(-1, DateKeys.daysUntil(LocalDate.of(2026, 11, 22), today))
        assertEquals(-23, DateKeys.daysUntil(LocalDate.of(2026, 10, 31), today))
    }

    @Test
    fun `days until from string is safe for blanks`() {
        val today = LocalDate.of(2026, 11, 23)
        assertEquals(Int.MAX_VALUE, DateKeys.daysUntil(null, today))
        assertEquals(Int.MAX_VALUE, DateKeys.daysUntil("not-a-date", today))
    }

    @Test
    fun `12 hour formatting matches legacy output`() {
        assertEquals("09:30 AM", DateKeys.formatTime12h("09:30"))
        assertEquals("12:00 PM", DateKeys.formatTime12h("12:00"))
        assertEquals("12:05 AM", DateKeys.formatTime12h("00:05"))
        assertEquals("05:05 PM", DateKeys.formatTime12h("17:05"))
        assertEquals("--:--", DateKeys.formatTime12h(null))
        assertEquals("--:--", DateKeys.formatTime12h(""))
    }

    @Test
    fun `time validation`() {
        assertTrue(DateKeys.isValidTime("00:00"))
        assertTrue(DateKeys.isValidTime("23:59"))
        assertFalse(DateKeys.isValidTime("24:00"))
        assertFalse(DateKeys.isValidTime("12:60"))
        assertFalse(DateKeys.isValidTime("noon"))
        assertEquals(570, DateKeys.minutesOfDay("09:30"))
        assertEquals(570, DateKeys.minutesOfDay("9:30"))
    }

    @Test
    fun `pretty and month labels`() {
        assertEquals("23 Nov 2026", DateKeys.formatPretty(LocalDate.of(2026, 11, 23)))
        assertEquals("23 Nov 2026", DateKeys.formatPretty("2026-11-23"))
        assertEquals("--", DateKeys.formatPretty("bogus"))
        assertEquals("Nov, 2026", DateKeys.formatMonthYearComma(YearMonth.of(2026, 11)))
        assertEquals("Sun", DateKeys.dayShort(LocalDate.of(2026, 11, 22)))
        assertEquals(0, DateKeys.weekdayIndexSundayFirst(LocalDate.of(2026, 11, 22)))
        assertEquals(1, DateKeys.weekdayIndexSundayFirst(LocalDate.of(2026, 11, 23)))
    }
}

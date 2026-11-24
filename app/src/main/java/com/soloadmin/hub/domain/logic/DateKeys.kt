package com.soloadmin.hub.domain.logic

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The legacy app stored dates as `yyyy-MM-dd` strings and counted days with
 * `Math.round((target - now) / 86400000)`. Both behaviours are reproduced here with
 * `java.time`, which is timezone-stable and available from API 26.
 */
object DateKeys {

    const val DAY_MS = 24L * 60L * 60L * 1000L

    fun of(date: LocalDate): String = "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

    fun parseOrNull(value: String?): LocalDate? {
        val raw = value?.trim().orEmpty()
        if (raw.length < 8) return null
        val parts = raw.split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year !in 1900..2999 || month !in 1..12) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    fun parseOrToday(value: String?, today: LocalDate): LocalDate = parseOrNull(value) ?: today

    /** `getDaysUntil` in the legacy app: whole calendar days from today to [target]. */
    fun daysUntil(target: LocalDate, today: LocalDate): Int =
        Math.round(
            (target.toEpochDay() * DAY_MS - today.toEpochDay() * DAY_MS).toDouble() / DAY_MS.toDouble()
        ).toInt()

    fun daysUntil(targetKey: String?, today: LocalDate): Int {
        val target = parseOrNull(targetKey) ?: return Int.MAX_VALUE
        return daysUntil(target, today)
    }

    /** 1234 ms after midnight -> "12:20" style 12-hour label. */
    fun formatTime12h(time24: String?): String {
        val (hour, minute) = parseTimeOrNull(time24) ?: return "--:--"
        val suffix = if (hour >= 12) "PM" else "AM"
        val display = if (hour % 12 == 0) 12 else hour % 12
        return "%02d:%02d %s".format(display, minute, suffix)
    }

    fun parseTimeOrNull(value: String?): Pair<Int, Int>? {
        val raw = value?.trim().orEmpty()
        if (raw.length < 4 || !raw.contains(':')) return null
        val parts = raw.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    fun isValidTime(value: String?): Boolean = parseTimeOrNull(value) != null

    /**
     * Minutes represented by the *raw* `HH:mm` strings, so comparisons such as
     * "in time later than shift start" behave exactly like the legacy string comparison while
     * tolerating values such as "9:05".
     */
    fun minutesOfDay(value: String?): Int? {
        val (hour, minute) = parseTimeOrNull(value) ?: return null
        return hour * 60 + minute
    }

    fun nowTimeKey(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun currentTimeKey(): String = nowTimeKey(LocalTime.now())

    /** "02 Nov 2026" */
    fun formatPretty(value: LocalDate): String =
        "%02d %s %d".format(value.dayOfMonth, monthShort(value.monthValue), value.year)

    fun formatPretty(value: String?): String {
        val parsed = parseOrNull(value) ?: return "--"
        return formatPretty(parsed)
    }

    /** "Nov 2026" */
    fun formatMonthYear(month: YearMonth): String =
        "%s %d".format(monthShort(month.monthValue), month.year)

    /** "Nov, 2026" — legacy calendar label. */
    fun formatMonthYearComma(month: YearMonth): String =
        "%s, %d".format(monthShort(month.monthValue), month.year)

    fun monthShort(month: Int): String = MONTHS[(month - 1).coerceIn(0, 11)]

    fun dayShort(date: LocalDate): String = DAYS[date.dayOfWeek.value % 7]

    val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    /** Sunday-first, matching the legacy calendar header. */
    val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun weekdayIndexSundayFirst(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun weekdayName(index: Int): String = DAYS[index.coerceIn(0, 6)]

    fun localeName(): Locale = Locale.forLanguageTag("en-IN")

    fun dayOfMonthStyle(date: LocalDate): String = date.dayOfMonth.toString().padStart(2, '0')

    fun monthKey(date: LocalDate): String = "%04d-%02d".format(date.year, date.monthValue)

    fun prettyWithWeekday(date: LocalDate): String =
        "${dayShort(date)}, ${formatPretty(date)}"

    fun monthFullName(month: Int): String =
        java.time.Month.of(month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}

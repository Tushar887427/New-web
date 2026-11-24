package com.soloadmin.hub.domain.logic

import java.time.LocalDate

/**
 * The handful of derived figures the dashboard shows that are pure arithmetic.
 *
 * They live here rather than inside the ViewModel so they can be unit-tested without an Android
 * runtime, and so the "weekly off" rule has exactly one definition in the app.
 */
object DashboardMath {

    /**
     * Legacy numbering is Sunday-first: 0 = Sunday … 6 = Saturday, matching
     * [DateKeys.weekdayIndexSundayFirst].
     */
    fun isWeeklyOff(date: LocalDate, weeklyOffDayIndex: Int): Boolean =
        DateKeys.weekdayIndexSundayFirst(date) == weeklyOffDayIndex.coerceIn(0, 6)

    /** Monthly take-home shown on Home: salary received minus personal spending. */
    fun monthNet(received: Money, personalSpent: Money): Money = received - personalSpent

    /** A document is "needing attention" inside the same 60-day window the legacy badge used. */
    fun needsAttention(daysUntilExpiry: Int): Boolean = daysUntilExpiry <= ATTENTION_WINDOW_DAYS

    const val ATTENTION_WINDOW_DAYS = 60
}

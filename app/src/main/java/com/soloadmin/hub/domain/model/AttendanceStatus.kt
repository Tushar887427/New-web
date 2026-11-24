package com.soloadmin.hub.domain.model

/**
 * Attendance states, preserved one-to-one from the legacy application's `STATUS_CONFIG`
 * map (same storage keys, same labels, same ordering used by the day editor).
 *
 * [storageKey] is the value written by the HTML app and therefore the value kept in Room so
 * that legacy JSON imports and exports stay byte-compatible.
 */
enum class AttendanceStatus(
    val storageKey: String,
    val label: String,
    val shortLabel: String
) {
    PRESENT("Present", "Present", "P"),
    LEAVE("Leave", "Leave", "L"),
    ABSENT("Absent", "Absent", "A"),
    HOLIDAY("Holiday", "Holiday", "H"),
    OFFDAY("Offday", "Offday", "O"),
    OUTSTATION("Outstation", "Official Duty", "OD"),
    LATE("Late", "Late", "LT"),
    EARLY_GOING("Early_Going", "Early Out", "EO"),
    MISS_PUNCH("MissPunch", "MissPunch", "MP");

    /** Present, Late and Early-Going all count as a physically present day. */
    val countsAsPresentDay: Boolean
        get() = this == PRESENT || this == LATE || this == EARLY_GOING

    val isLeave: Boolean get() = this == LEAVE
    val isOfficialDuty: Boolean get() = this == OUTSTATION

    companion object {
        /** Legacy order of the day-editor dropdown. */
        val editorOrder: List<AttendanceStatus> = entries.toList()

        fun fromStorage(value: String?): AttendanceStatus? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.storageKey.equals(value.trim(), ignoreCase = true) }
        }

        fun labelOf(value: String?): String = fromStorage(value)?.label ?: (value ?: "")
    }
}

/** The three leave categories the office tracks. */
object LeaveTypes {
    const val CASUAL = "Casual Leave"
    const val EARNED = "Earned Leave"
    const val COMMUTED = "Commuted Leave"

    val all: List<String> = listOf(CASUAL, EARNED, COMMUTED)
    val default: String = CASUAL

    fun normalize(value: String?): String =
        all.firstOrNull { it.equals(value?.trim(), ignoreCase = true) } ?: default
}

/** Official duty modes offered by the legacy "Day Mode" selector. */
enum class DutyMode(val label: String) {
    HALF_DAY("Half Day"),
    FULL_DAY("Full Day"),
    QUARTER_DAY("Quarter Day");

    companion object {
        val default: DutyMode = FULL_DAY

        fun fromLabel(value: String?): DutyMode =
            entries.firstOrNull { it.label.equals(value?.trim(), ignoreCase = true) } ?: default
    }
}

/** Who paid for an expense. Headmaster expenses are recoverable, personal ones are not. */
enum class ExpenseOwner(val storageKey: String, val label: String) {
    HEADMASTER("headmaster", "Headmaster"),
    PERSONAL("personal", "Personal");

    companion object {
        fun fromStorage(value: String?): ExpenseOwner =
            entries.firstOrNull { it.storageKey.equals(value?.trim(), ignoreCase = true) } ?: PERSONAL
    }
}

enum class FinanceStatus(val storageKey: String, val label: String) {
    RECEIVED("received", "Received"),
    PENDING("pending", "Pending");

    companion object {
        fun fromStorage(value: String?): FinanceStatus =
            entries.firstOrNull { it.storageKey.equals(value?.trim(), ignoreCase = true) } ?: RECEIVED

        /**
         * The legacy toggle treats anything that is not exactly `received` as pending, so an
         * unknown status imported from JSON must not silently become "received".
         */
        fun isReceived(value: String?): Boolean = value?.trim()?.equals("received", true) == true
    }
}

/** Banner tint used by colleague cards when no cover photo is set. */
enum class ColleagueTheme(val key: String, val label: String) {
    SKY("sky", "Sky"),
    MINT("mint", "Mint"),
    SUNSET("sunset", "Sunset"),
    LAVENDER("lavender", "Lavender");

    companion object {
        fun fromKey(value: String?): ColleagueTheme =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: SKY
    }
}

object ExpenseCategories {
    val all: List<String> = listOf("Office", "Travel", "Food", "Printing", "Other")
    val default: String = "Office"
    fun normalize(value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() } ?: default
}

object KeyDateCategories {
    val all: List<String> = listOf("Document", "Compliance", "Contract", "License", "Other")
    val default: String = "Document"
    fun normalize(value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() } ?: default
}

/** A single label/value chip on a colleague or profile card. */
data class InfoField(val label: String, val value: String) {
    val isBlank: Boolean get() = label.isBlank() && value.isBlank()
}

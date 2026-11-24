package com.soloadmin.hub.domain.model

/**
 * Decoded, platform-neutral view of an inbound backup file.
 *
 * `data/backup/BackupDecoder` is the only component that touches JSON tokens; everything below
 * is plain Kotlin so the mapping rules (the risky part) are unit-testable on the JVM.
 *
 * Numbers are kept in their original JSON-ish shape ([NumOrText]) because the legacy app wrote
 * amounts sometimes as numbers and sometimes as strings.
 */
sealed interface NumOrText {
    data class Number(val value: Double) : NumOrText
    data class Text(val value: String) : NumOrText
    data object Absent : NumOrText

    fun asDoubleOrNull(): Double? = when (this) {
        is Number -> value.takeIf { it.isFinite() }
        is Text -> value.trim().replace(",", "").replace("₹", "").toDoubleOrNull()
        Absent -> null
    }

    fun asIntOrNull(): Int? = asDoubleOrNull()?.let { Math.round(it).toInt() }
}

data class ImportedInfo(val label: String?, val value: String?)

data class ImportedProfile(
    val name: String?,
    val bio: String?,
    /** Legacy base64 data URL; the importer writes it to app storage. */
    val photoDataUrl: String?,
    val weeklyOff: NumOrText,
    val shiftStart: String?,
    val info: List<ImportedInfo>
)

data class ImportedAttendance(
    val dateKey: String,
    val status: String?,
    val leaveType: String?,
    val inTime: String?,
    val outTime: String?,
    val note: String?
)

data class ImportedDuty(
    val id: String?,
    val dateKey: String?,
    val mode: String?,
    val startTime: String?,
    val endTime: String?,
    val meetingWith: String?,
    val location: String?,
    val remarks: String?,
    val attachment: String?
)

data class ImportedExpense(
    val id: String?,
    val dateKey: String?,
    val title: String?,
    val category: String?,
    val amount: NumOrText,
    val owner: String?,
    val recovered: Boolean?,
    val note: String?
)

data class ImportedFinance(
    val id: String?,
    val dateKey: String?,
    val title: String?,
    val type: String?,
    val amount: NumOrText,
    val status: String?
)

data class ImportedKeyDate(
    val id: String?,
    val title: String?,
    val category: String?,
    val validFrom: String?,
    val validUntil: String?,
    val note: String?
)

data class ImportedTask(val id: String?, val text: String?, val done: Boolean?)

data class ImportedColleague(
    val id: String?,
    val name: String?,
    val role: String?,
    val theme: String?,
    val photoDataUrl: String?,
    val coverDataUrl: String?,
    val info: List<ImportedInfo>
)

data class ImportedWorkSession(
    val dateKey: String,
    val inTime: String?,
    val outTime: String?,
    val onBreak: Boolean,
    val breakStartedAt: Long?,
    val breakMinutes: NumOrText,
    val sessionsCount: NumOrText
)

/** Everything a backup file can carry, already decoded. */
data class DecodedBackup(
    val isNativeFormat: Boolean,
    val sourceVersion: Int?,
    val profile: ImportedProfile?,
    val attendance: List<ImportedAttendance>,
    val duties: List<ImportedDuty>,
    val expenses: List<ImportedExpense>,
    val finances: List<ImportedFinance>,
    val keyDates: List<ImportedKeyDate>,
    val tasks: List<ImportedTask>,
    val colleagues: List<ImportedColleague>,
    val workSessions: List<ImportedWorkSession>
) {
    val totalRecords: Int
        get() = attendance.size + duties.size + expenses.size + finances.size +
            keyDates.size + tasks.size + colleagues.size + (if (profile != null) 1 else 0)
}

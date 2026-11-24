package com.soloadmin.hub.domain.model

/**
 * A complete, platform-neutral copy of everything the user owns. Produced by repositories and
 * consumed by the JSON writer, the CSV/PDF report builders and the importer.
 */
data class BackupSnapshot(
    val profile: ProfileRecord,
    val attendance: List<AttendanceRecord>,
    val duties: List<OfficialDutyRecord>,
    val expenses: List<ExpenseRecord>,
    val finances: List<FinanceRecord>,
    val keyDates: List<KeyDateRecord>,
    val tasks: List<DailyTaskRecord>,
    val colleagues: List<ColleagueRecord>,
    val workSessions: List<WorkSession>
) {
    val recordCount: Int
        get() = attendance.size + duties.size + expenses.size + finances.size +
            keyDates.size + tasks.size + colleagues.size

    fun isEmpty(): Boolean = recordCount == 0
}

/** How a conflict with an existing record is resolved during import. */
enum class ImportConflictPolicy {
    /** Leave the record already stored in the database untouched. */
    KEEP_EXISTING,

    /** Overwrite the stored record with the incoming one. */
    OVERWRITE
}

data class ImportOptions(
    val conflictPolicy: ImportConflictPolicy = ImportConflictPolicy.KEEP_EXISTING,
    /** Replace the whole database with the backup instead of merging. */
    val replaceAll: Boolean = false,
    val importProfile: Boolean = true
)

enum class ImportGroup(val label: String) {
    ATTENDANCE("Attendance"),
    OFFICIAL_DUTY("Official duty"),
    EXPENSES("Expenses"),
    FINANCE("Pay & finance"),
    KEY_DATES("Key dates"),
    TASKS("Daily tasks"),
    COLLEAGUES("Colleagues"),
    PROFILE("My profile"),
    WORK_SESSIONS("Punch sessions")
}

/** Why a record was not imported. */
enum class SkipReason(val readable: String) {
    INVALID_DATE("Invalid or missing date"),
    MISSING_REQUIRED_FIELD("Required details missing"),
    INVALID_AMOUNT("Invalid amount"),
    DUPLICATE("Already present — kept existing"),
    UNKNOWN_STATUS("Unrecognised status"),
    TOO_LONG("Text too long"),
    UNSUPPORTED("Unsupported record")
}

data class SkippedRecord(
    val group: ImportGroup,
    val identifier: String,
    val reason: SkipReason
)

data class ImportCounts(
    val imported: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0
)

data class ImportPreview(
    val decoded: DecodedBackup,
    val items: Map<ImportGroup, ImportCounts>,
    val skipped: List<SkippedRecord>,
    val profileIncluded: Boolean
) {
    val totalImported: Int get() = items.values.sumOf { it.imported }
    val totalUpdated: Int get() = items.values.sumOf { it.updated }
    val totalSkipped: Int get() = items.values.sumOf { it.skipped }

    val hasAnythingToImport: Boolean
        get() = totalImported > 0 || totalUpdated > 0 || (profileIncluded)

    fun countsFor(group: ImportGroup): ImportCounts = items[group] ?: ImportCounts()

    val skippedSummary: List<Pair<SkipReason, Int>>
        get() = skipped.groupingBy { it.reason }.eachCount().toList().sortedByDescending { it.second }
}

data class ImportResult(
    val preview: ImportPreview,
    val replacedExisting: Boolean,
    /** Relative file names of legacy base64 images that were written to storage. */
    val savedImages: Int
)

/** User-facing summary of what a restore wrote. */
data class RestoreSummary(
    val imported: Int,
    val updated: Int,
    val skipped: Int,
    val images: Int,
    val skippedReasons: List<Pair<SkipReason, Int>>
)

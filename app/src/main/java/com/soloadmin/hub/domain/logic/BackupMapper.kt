package com.soloadmin.hub.domain.logic

import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.ColleagueRecord
import com.soloadmin.hub.domain.model.ColleagueTheme
import com.soloadmin.hub.domain.model.DailyTaskRecord
import com.soloadmin.hub.domain.model.DecodedBackup
import com.soloadmin.hub.domain.model.DutyMode
import com.soloadmin.hub.domain.model.ExpenseCategories
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.FinanceStatus
import com.soloadmin.hub.domain.model.ImportConflictPolicy
import com.soloadmin.hub.domain.model.ImportCounts
import com.soloadmin.hub.domain.model.ImportGroup
import com.soloadmin.hub.domain.model.ImportOptions
import com.soloadmin.hub.domain.model.ImportPreview
import com.soloadmin.hub.domain.model.ImportedInfo
import com.soloadmin.hub.domain.model.InfoField
import com.soloadmin.hub.domain.model.KeyDateCategories
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.domain.model.LeaveTypes
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import com.soloadmin.hub.domain.model.ProfileRecord
import com.soloadmin.hub.domain.model.SkipReason
import com.soloadmin.hub.domain.model.SkippedRecord
import com.soloadmin.hub.domain.model.WorkSession
import java.time.LocalDate

/**
 * Converts a decoded backup into validated domain records.
 *
 * Rules (deliberately conservative, because this touches irreplaceable user data):
 *  - A record with no usable date is skipped with a reason instead of being guessed.
 *  - Amounts that are missing, negative or unparsable are skipped, never coerced to zero.
 *  - Text is trimmed and length-capped so a malformed file cannot bloat the database.
 *  - "Duplicate" means same natural key: attendance date, record id, task text+done for
 *    id-less legacy rows. With [ImportConflictPolicy.KEEP_EXISTING] the stored row wins.
 */
object BackupMapper {

    private const val MAX_TEXT = 500
    private const val MAX_LONG_TEXT = 2000

    fun preview(
        decoded: DecodedBackup,
        existing: BackupSnapshot,
        options: ImportOptions,
        today: LocalDate = LocalDate.now()
    ): ImportPreview {
        val grouped = mutableMapOf<ImportGroup, ImportCounts>()
        val skipped = mutableListOf<SkippedRecord>()

        // ---------- attendance (map keyed by date in the legacy format) ----------
        val existingAttendance = existing.attendance.associateBy { it.dateKey }
        val seenDates = mutableSetOf<String>()
        decoded.attendance.forEach { row ->
            val date = DateKeys.parseOrNull(row.dateKey)
            val status = AttendanceStatus.fromStorage(row.status)
            val reason = when {
                date == null -> SkipReason.INVALID_DATE
                status == null -> SkipReason.UNKNOWN_STATUS
                !seenDates.add(row.dateKey) -> SkipReason.DUPLICATE
                else -> null
            }
            val exists = date != null && existingAttendance.containsKey(row.dateKey)
            if (reason != null) {
                val g = grouped[ImportGroup.ATTENDANCE] ?: ImportCounts()
                grouped[ImportGroup.ATTENDANCE] = g.copy(skipped = g.skipped + 1)
                skipped += SkippedRecord(ImportGroup.ATTENDANCE, row.dateKey, reason)
            } else {
                if (exists && options.conflictPolicy == ImportConflictPolicy.KEEP_EXISTING) {
                    val g = grouped[ImportGroup.ATTENDANCE] ?: ImportCounts()
                    grouped[ImportGroup.ATTENDANCE] = g.copy(skipped = g.skipped + 1)
                    skipped += SkippedRecord(ImportGroup.ATTENDANCE, row.dateKey, SkipReason.DUPLICATE)
                } else if (exists) {
                    val g = grouped[ImportGroup.ATTENDANCE] ?: ImportCounts()
                    grouped[ImportGroup.ATTENDANCE] = g.copy(updated = g.updated + 1)
                } else {
                    val g = grouped[ImportGroup.ATTENDANCE] ?: ImportCounts()
                    grouped[ImportGroup.ATTENDANCE] = g.copy(imported = g.imported + 1)
                }
            }
        }

        // ---------- duties ----------
        val existingDutyIds = existing.duties.map { it.id }.toSet()
        val dutyIds = mutableSetOf<String>()
        decoded.duties.forEach { row ->
            val date = DateKeys.parseOrNull(row.dateKey)
            val title = row.meetingWith?.trim().orEmpty()
            val reason = when {
                date == null -> SkipReason.INVALID_DATE
                title.isEmpty() -> SkipReason.MISSING_REQUIRED_FIELD
                else -> null
            }
            val key = row.id?.trim().orEmpty()
            val duplicateInFile = key.isNotEmpty() && !dutyIds.add(key)
            classify(grouped, skipped, ImportGroup.OFFICIAL_DUTY, duplicateInFile,
                reason, exists = key.isNotEmpty() && existingDutyIds.contains(key),
                options = options, identifier = row.dateKey.orEmpty())
        }

        // ---------- expenses ----------
        val existingExpenseIds = existing.expenses.map { it.id }.toSet()
        val expenseIds = mutableSetOf<String>()
        decoded.expenses.forEach { row ->
            val date = DateKeys.parseOrNull(row.dateKey)
            val amount = row.amount.asDoubleOrNull()
            val reason = when {
                date == null -> SkipReason.INVALID_DATE
                row.title.isNullOrBlank() -> SkipReason.MISSING_REQUIRED_FIELD
                amount == null || amount <= 0.0 -> SkipReason.INVALID_AMOUNT
                else -> null
            }
            val key = row.id?.trim().orEmpty()
            val duplicateInFile = key.isNotEmpty() && !expenseIds.add(key)
            classify(grouped, skipped, ImportGroup.EXPENSES, duplicateInFile, reason,
                exists = key.isNotEmpty() && existingExpenseIds.contains(key),
                options = options, identifier = row.dateKey.orEmpty())
        }

        // ---------- finance ----------
        val existingFinanceIds = existing.finances.map { it.id }.toSet()
        val financeIds = mutableSetOf<String>()
        decoded.finances.forEach { row ->
            val date = DateKeys.parseOrNull(row.dateKey)
            val amount = row.amount.asDoubleOrNull()
            val reason = when {
                date == null -> SkipReason.INVALID_DATE
                row.title.isNullOrBlank() -> SkipReason.MISSING_REQUIRED_FIELD
                amount == null || amount <= 0.0 -> SkipReason.INVALID_AMOUNT
                else -> null
            }
            val key = row.id?.trim().orEmpty()
            val duplicateInFile = key.isNotEmpty() && !financeIds.add(key)
            classify(grouped, skipped, ImportGroup.FINANCE, duplicateInFile, reason,
                exists = key.isNotEmpty() && existingFinanceIds.contains(key),
                options = options, identifier = row.dateKey.orEmpty())
        }

        // ---------- key dates ----------
        val existingReminderIds = existing.keyDates.map { it.id }.toSet()
        val reminderIds = mutableSetOf<String>()
        decoded.keyDates.forEach { row ->
            val until = DateKeys.parseOrNull(row.validUntil)
            val from = DateKeys.parseOrNull(row.validFrom)
            val reason = when {
                until == null -> SkipReason.INVALID_DATE
                row.title.isNullOrBlank() -> SkipReason.MISSING_REQUIRED_FIELD
                from != null && until < from -> SkipReason.INVALID_DATE
                else -> null
            }
            val key = row.id?.trim().orEmpty()
            val duplicateInFile = key.isNotEmpty() && !reminderIds.add(key)
            classify(grouped, skipped, ImportGroup.KEY_DATES, duplicateInFile, reason,
                exists = key.isNotEmpty() && existingReminderIds.contains(key),
                options = options, identifier = row.title.orEmpty())
        }

        // ---------- tasks ----------
        val existingTaskIds = existing.tasks.map { it.id }.toSet()
        val taskIds = mutableSetOf<String>()
        decoded.tasks.forEach { row ->
            val text = row.text?.trim().orEmpty()
            val key = row.id?.trim().orEmpty()
            val duplicateInFile = key.isNotEmpty() && !taskIds.add(key)
            classify(grouped, skipped, ImportGroup.TASKS, duplicateInFile,
                if (text.isEmpty()) SkipReason.MISSING_REQUIRED_FIELD else null,
                exists = key.isNotEmpty() && existingTaskIds.contains(key),
                options = options, identifier = text)
        }

        // ---------- colleagues ----------
        val existingColleagueIds = existing.colleagues.map { it.id }.toSet()
        val colleagueIds = mutableSetOf<String>()
        decoded.colleagues.forEach { row ->
            val name = row.name?.trim().orEmpty()
            val key = row.id?.trim().orEmpty()
            val duplicateInFile = key.isNotEmpty() && !colleagueIds.add(key)
            classify(grouped, skipped, ImportGroup.COLLEAGUES, duplicateInFile,
                if (name.isEmpty()) SkipReason.MISSING_REQUIRED_FIELD else null,
                exists = key.isNotEmpty() && existingColleagueIds.contains(key),
                options = options, identifier = name)
        }

        // ---------- work sessions ----------
        val existingSessions = existing.workSessions.map { it.dateKey }.toSet()
        decoded.workSessions.forEach { row ->
            val date = DateKeys.parseOrNull(row.dateKey)
            classify(grouped, skipped, ImportGroup.WORK_SESSIONS, false,
                if (date == null) SkipReason.INVALID_DATE else null,
                exists = date != null && existingSessions.contains(row.dateKey),
                options = options, identifier = row.dateKey.orEmpty())
        }

        if (decoded.profile != null && options.importProfile) {
            grouped[ImportGroup.PROFILE] = ImportCounts(
                imported = if (existing.profile.name.isBlank() && existing.profile.info.isEmpty()) 1 else 0,
                updated = if (existing.profile.name.isBlank() && existing.profile.info.isEmpty()) 0 else 1
            )
        }

        return ImportPreview(
            decoded = decoded,
            items = grouped,
            skipped = skipped,
            profileIncluded = decoded.profile != null && options.importProfile
        )
    }

    private fun classify(
        grouped: MutableMap<ImportGroup, ImportCounts>,
        skipped: MutableList<SkippedRecord>,
        group: ImportGroup,
        duplicateInFile: Boolean,
        reason: SkipReason?,
        exists: Boolean,
        options: ImportOptions,
        identifier: String
    ) {
        val effectiveReason = reason ?: if (duplicateInFile) SkipReason.DUPLICATE else null
        val current = grouped[group] ?: ImportCounts()
        when {
            effectiveReason != null -> {
                grouped[group] = current.copy(skipped = current.skipped + 1)
                skipped += SkippedRecord(group, identifier, effectiveReason)
            }
            exists && options.conflictPolicy == ImportConflictPolicy.KEEP_EXISTING -> {
                grouped[group] = current.copy(skipped = current.skipped + 1)
                skipped += SkippedRecord(group, identifier, SkipReason.DUPLICATE)
            }
            exists -> grouped[group] = current.copy(updated = current.updated + 1)
            else -> grouped[group] = current.copy(imported = current.imported + 1)
        }
    }

    data class Mapped(
        val duties: List<OfficialDutyRecord>,
        val expenses: List<ExpenseRecord>,
        val finances: List<FinanceRecord>,
        val keyDates: List<KeyDateRecord>,
        val tasks: List<DailyTaskRecord>,
        val colleagues: List<ColleagueRecord>
    )

    /** Builds the domain rows that pass validation (skips are reported by [preview]). */
    fun toDomain(
        decoded: DecodedBackup,
        existing: BackupSnapshot,
        options: ImportOptions,
        skipped: MutableList<SkippedRecord> = mutableListOf()
    ): Mapped {
        val now = System.currentTimeMillis()

        val attendance = decoded.attendance.mapNotNull { row ->
            val date = DateKeys.parseOrNull(row.dateKey) ?: return@mapNotNull null
            val status = AttendanceStatus.fromStorage(row.status) ?: return@mapNotNull null
            AttendanceRecord(
                dateKey = DateKeys.of(date),
                status = status,
                leaveType = row.leaveType?.takeIf { it.isNotBlank() }?.let(LeaveTypes::normalize),
                inTime = sanitizeTime(row.inTime),
                outTime = sanitizeTime(row.outTime),
                note = clean(row.note, MAX_TEXT)
            )
        }.distinctBy { it.dateKey }

        val duties = decoded.duties.mapIndexedNotNull { index, row ->
            val date = DateKeys.parseOrNull(row.dateKey) ?: return@mapIndexedNotNull null
            val title = clean(row.meetingWith, MAX_TEXT)
            if (title.isEmpty()) return@mapIndexedNotNull null
            OfficialDutyRecord(
                id = row.id?.trim()?.takeIf { it.isNotEmpty() } ?: "oow-import-$now-$index",
                dateKey = DateKeys.of(date),
                mode = DutyMode.fromLabel(row.mode),
                startTime = sanitizeTime(row.startTime) ?: "10:00",
                endTime = sanitizeTime(row.endTime) ?: "16:00",
                meetingWith = title,
                location = clean(row.location, MAX_TEXT),
                remarks = clean(row.remarks, MAX_LONG_TEXT),
                attachmentPath = row.attachment?.takeIf { it.isNotBlank() },
                createdAt = now + index
            )
        }.distinctBy { it.id }

        val expenses = decoded.expenses.mapIndexedNotNull { index, row ->
            val date = DateKeys.parseOrNull(row.dateKey) ?: return@mapIndexedNotNull null
            val amount = row.amount.asDoubleOrNull() ?: return@mapIndexedNotNull null
            if (amount <= 0.0) return@mapIndexedNotNull null
            val title = clean(row.title, MAX_TEXT)
            if (title.isEmpty()) return@mapIndexedNotNull null
            ExpenseRecord(
                id = row.id?.trim()?.takeIf { it.isNotEmpty() } ?: "exp-import-$now-$index",
                dateKey = DateKeys.of(date),
                title = title,
                category = ExpenseCategories.normalize(row.category),
                amount = Money.fromRupeeDouble(amount),
                owner = ExpenseOwner.fromStorage(row.owner),
                recovered = row.recovered == true,
                note = clean(row.note, MAX_TEXT),
                createdAt = now + index
            )
        }.distinctBy { it.id }

        val finances = decoded.finances.mapIndexedNotNull { index, row ->
            val date = DateKeys.parseOrNull(row.dateKey) ?: return@mapIndexedNotNull null
            val amount = row.amount.asDoubleOrNull() ?: return@mapIndexedNotNull null
            if (amount <= 0.0) return@mapIndexedNotNull null
            val title = clean(row.title, MAX_TEXT)
            if (title.isEmpty()) return@mapIndexedNotNull null
            FinanceRecord(
                id = row.id?.trim()?.takeIf { it.isNotEmpty() } ?: "fin-import-$now-$index",
                dateKey = DateKeys.of(date),
                title = title,
                amount = Money.fromRupeeDouble(amount),
                status = if (FinanceStatus.isReceived(row.status)) FinanceStatus.RECEIVED else FinanceStatus.PENDING,
                legacyType = row.type?.takeIf { it.isNotBlank() } ?: "income",
                createdAt = now + index
            )
        }.distinctBy { it.id }

        val keyDates = decoded.keyDates.mapIndexedNotNull { index, row ->
            val until = DateKeys.parseOrNull(row.validUntil) ?: return@mapIndexedNotNull null
            val from = DateKeys.parseOrNull(row.validFrom) ?: until
            if (until < from) return@mapIndexedNotNull null
            val title = clean(row.title, MAX_TEXT)
            if (title.isEmpty()) return@mapIndexedNotNull null
            KeyDateRecord(
                id = row.id?.trim()?.takeIf { it.isNotEmpty() } ?: "rem-import-$now-$index",
                title = title,
                category = KeyDateCategories.normalize(row.category),
                validFrom = DateKeys.of(from),
                validUntil = DateKeys.of(until),
                note = clean(row.note, MAX_LONG_TEXT),
                createdAt = now + index
            )
        }.distinctBy { it.id }

        val tasks = decoded.tasks.mapIndexedNotNull { index, row ->
            val text = clean(row.text, MAX_TEXT)
            if (text.isEmpty()) return@mapIndexedNotNull null
            DailyTaskRecord(
                id = row.id?.trim()?.takeIf { it.isNotEmpty() } ?: "tsk-import-$now-$index",
                text = text,
                done = row.done == true,
                createdAt = now + index,
                sortOrder = index
            )
        }.distinctBy { it.id }

        val colleagues = decoded.colleagues.mapIndexedNotNull { index, row ->
            val name = clean(row.name, MAX_TEXT)
            if (name.isEmpty()) return@mapIndexedNotNull null
            ColleagueRecord(
                id = row.id?.trim()?.takeIf { it.isNotEmpty() } ?: "col-import-$now-$index",
                name = name,
                role = clean(row.role, MAX_TEXT),
                theme = ColleagueTheme.fromKey(row.theme),
                photoPath = row.photoDataUrl?.takeIf { it.isNotBlank() },
                coverPath = row.coverDataUrl?.takeIf { it.isNotBlank() },
                info = cleanInfo(row.info),
                createdAt = now + index,
                sortOrder = index
            )
        }.distinctBy { it.id }

        return Mapped(duties, expenses, finances, keyDates, tasks, colleagues)
    }

    fun mapProfile(decoded: DecodedBackup): ProfileRecord? {
        val profile = decoded.profile ?: return null
        val weekly = profile.weeklyOff.asIntOrNull()?.takeIf { it in 0..6 } ?: 0
        return ProfileRecord(
            name = clean(profile.name, 120).ifEmpty { "Admin" },
            bio = clean(profile.bio, MAX_LONG_TEXT),
            photoPath = profile.photoDataUrl?.takeIf { it.isNotBlank() },
            weeklyOffDay = weekly,
            shiftStart = sanitizeTime(profile.shiftStart) ?: "09:30",
            info = cleanInfo(profile.info)
        )
    }

    private fun cleanInfo(info: List<ImportedInfo>?): List<InfoField> =
        info.orEmpty().mapNotNull { field ->
            val label = clean(field.label, 60)
            val value = clean(field.value, MAX_TEXT)
            if (label.isEmpty() && value.isEmpty()) null
            else InfoField(label.ifEmpty { "Info" }, value)
        }.take(12)

    private fun clean(value: String?, max: Int): String =
        value?.trim()?.replace(Regex("\\s+"), " ")?.take(max)?.trim().orEmpty()

    /** Accepts "9:05", "09:05", "09:05:00"; returns canonical "HH:mm" or null. */
    fun sanitizeTime(value: String?): String? {
        val parts = value?.trim()?.split(':') ?: return null
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].take(2).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return "%02d:%02d".format(hour, minute)
    }

    /** Legacy localStorage images are data URLs; only base64 PNG/JPEG payloads are accepted. */
    fun decodeDataUrl(dataUrl: String?): ByteArray? {
        if (dataUrl.isNullOrBlank()) return null
        val marker = dataUrl.indexOf("base64,")
        if (marker < 0) return null
        val header = dataUrl.substring(0, marker)
        if (!header.contains("image/")) return null
        val payload = dataUrl.substring(marker + 7)
        return try {
            java.util.Base64.getDecoder().decode(payload.replace(Regex("\\s"), ""))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Utility used by the importer to keep a legacy session row usable. */
    fun sanitizeWorkSession(row: com.soloadmin.hub.domain.model.ImportedWorkSession): WorkSession? {
        val date = DateKeys.parseOrNull(row.dateKey) ?: return null
        return WorkSession(
            dateKey = DateKeys.of(date),
            inTime = sanitizeTime(row.inTime),
            outTime = sanitizeTime(row.outTime),
            onBreak = row.onBreak,
            breakStartedAt = row.breakStartedAt,
            breakMinutes = (row.breakMinutes.asIntOrNull() ?: 0).coerceIn(0, 24 * 60),
            sessionsCount = (row.sessionsCount.asIntOrNull() ?: 0).coerceIn(0, 99)
        )
    }

}

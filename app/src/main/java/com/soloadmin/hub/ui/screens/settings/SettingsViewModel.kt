package com.soloadmin.hub.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.backup.BackupCodec
import com.soloadmin.hub.data.files.BackupFilesService
import com.soloadmin.hub.data.preferences.AppSettings
import com.soloadmin.hub.data.preferences.SettingsRepository
import com.soloadmin.hub.data.preferences.StartScreen
import com.soloadmin.hub.data.preferences.ThemeChoice
import com.soloadmin.hub.data.repository.DataTransferRepository
import com.soloadmin.hub.domain.model.DecodedBackup
import com.soloadmin.hub.domain.model.ImportConflictPolicy
import com.soloadmin.hub.domain.model.ImportGroup
import com.soloadmin.hub.domain.model.ImportOptions
import com.soloadmin.hub.domain.model.ImportPreview
import com.soloadmin.hub.domain.model.RestoreSummary
import com.soloadmin.hub.domain.model.SkipReason
import com.soloadmin.hub.notifications.ReminderScheduler
import com.soloadmin.hub.util.ReportWriter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** In-progress import once the file has been parsed and previewed. */
data class PendingImport(
    val fileName: String,
    val decoded: DecodedBackup,
    val preview: ImportPreview,
    val policy: ImportConflictPolicy = ImportConflictPolicy.KEEP_EXISTING
)

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val recordCounts: Map<ImportGroup, Int> = emptyMap(),
    val busy: Boolean = false,
    val busyLabel: String? = null,
    val pendingImport: PendingImport? = null,
    val lastRestore: RestoreSummary? = null,
    val storageBytes: Long = 0L,
    val fileToShare: File? = null,
    val shareMime: String? = null
) {
    val totalRecords: Int get() = recordCounts.values.sum()
    val dataGroups: List<Pair<ImportGroup, Int>>
        get() = recordCounts.entries.sortedBy { it.key.ordinal }.map { it.key to it.value }
}

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class Share(val file: File, val mime: String, val title: String) : SettingsEvent
    data class ImportFailed(val message: String) : SettingsEvent
    data object RequestNotificationsPermission : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val dataTransferRepository: DataTransferRepository,
    private val backupFiles: BackupFilesService,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val busy = MutableStateFlow<Pair<Boolean, String?>>(false to null)
    private val counts = MutableStateFlow<Map<ImportGroup, Int>>(emptyMap())
    private val pendingImport = MutableStateFlow<PendingImport?>(null)
    private val lastRestore = MutableStateFlow<RestoreSummary?>(null)
    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow = kotlinx.coroutines.flow.receiveAsFlow(events)

    val uiState: StateFlow<SettingsUiState> = combine(
        listOf(
            settingsRepository.settings,
            counts,
            busy,
            pendingImport,
            lastRestore
        )
    ) { values ->
        SettingsUiState(
            loading = false,
            settings = values[0] as AppSettings,
            recordCounts = values[1] as Map<ImportGroup, Int>,
            busy = (values[2] as Pair<Boolean, String?>).first,
            busyLabel = (values[2] as Pair<Boolean, String?>).second,
            pendingImport = values[3] as PendingImport?,
            lastRestore = values[4] as RestoreSummary?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCounts()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                reminderScheduler.setLeadDays(settings.reminderLeadDays)
                reminderScheduler.scheduleDailyCheck(settings.remindersEnabled)
            }
        }
    }

    fun refreshCounts() = viewModelScope.launch {
        counts.value = dataTransferRepository.counts()
    }

    // ---------------------------------------------------------------- preferences

    fun setTheme(theme: ThemeChoice) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setDynamicColour(value: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColour(value) }
    fun setStartScreen(value: StartScreen) = viewModelScope.launch { settingsRepository.setStartScreen(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { settingsRepository.setHaptics(value) }
    fun setCharts(value: Boolean) = viewModelScope.launch { settingsRepository.setDashboardCharts(value) }
    fun setCompactCards(value: Boolean) = viewModelScope.launch { settingsRepository.setCompactCards(value) }

    fun setRemindersEnabled(value: Boolean) = viewModelScope.launch {
        settingsRepository.setRemindersEnabled(value)
        if (value) {
            events.send(SettingsEvent.RequestNotificationsPermission)
            events.send(SettingsEvent.Message("Expiry reminders enabled"))
        } else {
            reminderScheduler.clearNotifications()
            events.send(SettingsEvent.Message("Expiry reminders turned off"))
        }
    }

    fun setReminderLeadDays(days: Int) = viewModelScope.launch {
        settingsRepository.setReminderLeadDays(days)
        reminderScheduler.setLeadDays(days)
        reminderScheduler.scheduleDailyCheck(true)
    }

    fun runReminderCheckNow() = viewModelScope.launch {
        reminderScheduler.checkSoon()
        events.send(SettingsEvent.Message("Reminder check scheduled in about a minute"))
    }

    // ---------------------------------------------------------------- backup / export

    fun exportBackup() = viewModelScope.launch {
        setBusy("Preparing backup…")
        try {
            val snapshot = dataTransferRepository.snapshot()
            val file = backupFiles.writeJsonBackup(snapshot)
            events.send(SettingsEvent.Share(file, "application/json", "Solo Admin backup"))
            events.send(SettingsEvent.Message("Backup ready — ${snapshot.recordCount} records"))
        } catch (error: Exception) {
            events.send(SettingsEvent.Message("Could not create the backup"))
        } finally {
            clearBusy()
        }
    }

    fun exportCsv(kind: CsvKind) = viewModelScope.launch {
        setBusy("Building CSV…")
        try {
            val snapshot = dataTransferRepository.snapshot()
            val today = LocalDate.now()
            val (name, content) = when (kind) {
                CsvKind.ATTENDANCE -> "solo_admin_attendance_$today.csv" to ReportWriter.attendanceCsv(snapshot.attendance)
                CsvKind.EXPENSES -> "solo_admin_expenses_$today.csv" to ReportWriter.expenseCsv(snapshot)
                CsvKind.FINANCE -> "solo_admin_finance_$today.csv" to ReportWriter.financeCsv(snapshot)
                CsvKind.KEY_DATES -> "solo_admin_key_dates_$today.csv" to ReportWriter.keyDatesCsv(snapshot, today)
                CsvKind.DUTY -> "solo_admin_official_duty_$today.csv" to ReportWriter.dutyCsv(snapshot)
                CsvKind.REMINDERS -> "solo_admin_reminders_$today.csv" to ReportWriter.remindersCsv(snapshot, today)
            }
            val file = backupFiles.writeCsv(name, content)
            events.send(SettingsEvent.Share(file, "text/csv", name))
        } catch (error: Exception) {
            events.send(SettingsEvent.Message("Could not build the CSV export"))
        } finally {
            clearBusy()
        }
    }

    fun exportPdf(month: YearMonth) = viewModelScope.launch {
        setBusy("Building PDF report…")
        try {
            val snapshot = dataTransferRepository.snapshot()
            val bytes = ReportWriter.monthlySummaryPdf(snapshot, month.toString())
            val name = "solo_admin_summary_$month.pdf"
            val file = backupFiles.writePdf(name, bytes)
            events.send(SettingsEvent.Share(file, "application/pdf", "Monthly summary"))
        } catch (error: Exception) {
            events.send(SettingsEvent.Message("Could not build the PDF report"))
        } finally {
            clearBusy()
        }
    }

    // ---------------------------------------------------------------- import

    fun stageImport(uri: Uri, fileName: String?) = viewModelScope.launch {
        setBusy("Reading backup…")
        try {
            val text = backupFiles.readText(uri)
            val decoded = BackupCodec.decode(text)
            val preview = dataTransferRepository.preview(decoded, ImportOptions())
            pendingImport.value = PendingImport(
                fileName = fileName ?: "Backup file",
                decoded = decoded,
                preview = preview
            )
        } catch (error: BackupCodec.DecodeException) {
            events.send(SettingsEvent.ImportFailed(error.message ?: "This file could not be read."))
        } catch (error: Exception) {
            events.send(SettingsEvent.ImportFailed(error.message ?: "This file could not be read."))
        } finally {
            clearBusy()
        }
    }

    fun setImportPolicy(policy: ImportConflictPolicy) {
        val current = pendingImport.value ?: return
        viewModelScope.launch {
            val preview = dataTransferRepository.preview(current.decoded, ImportOptions(conflictPolicy = policy))
            pendingImport.value = current.copy(policy = policy, preview = preview)
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val current = pendingImport.value ?: return@launch
        setBusy("Restoring records…")
        try {
            val result = dataTransferRepository.apply(
                current.decoded,
                ImportOptions(conflictPolicy = current.policy)
            )
            val summary = RestoreSummary(
                imported = result.preview.totalImported,
                updated = result.preview.totalUpdated,
                skipped = result.preview.totalSkipped,
                images = result.savedImages,
                skippedReasons = result.preview.skippedSummary
            )
            lastRestore.value = summary
            pendingImport.value = null
            refreshCounts()
            reminderScheduler.checkSoon()
            events.send(SettingsEvent.Message("Restore complete — ${summary.imported} imported, ${summary.skipped} skipped"))
        } catch (error: Exception) {
            events.send(SettingsEvent.ImportFailed("Restore failed and no records were changed. ${error.message.orEmpty()}"))
        } finally {
            clearBusy()
        }
    }

    fun cancelImport() { pendingImport.value = null }

    fun dismissRestoreSummary() { lastRestore.value = null }

    // ---------------------------------------------------------------- danger zone

    fun resetToSampleData() = viewModelScope.launch {
        setBusy("Restoring sample data…")
        try {
            dataTransferRepository.resetToSample(LocalDate.now())
            refreshCounts()
            events.send(SettingsEvent.Message("Sample data restored"))
        } catch (error: Exception) {
            events.send(SettingsEvent.Message("Could not reset the sample data"))
        } finally {
            clearBusy()
        }
    }

    fun deleteAllData() = viewModelScope.launch {
        setBusy("Deleting all records…")
        try {
            dataTransferRepository.wipeAll()
            refreshCounts()
            events.send(SettingsEvent.Message("All records deleted"))
        } catch (error: Exception) {
            events.send(SettingsEvent.Message("Could not delete the records"))
        } finally {
            clearBusy()
        }
    }

    /** Shareable URI for an exported file, produced by the file service's FileProvider. */
    fun uriFor(file: File): Uri = backupFiles.uriFor(file)

    /** Cache used by exports; cleared by the system as needed. */
    fun clearExportCache() = viewModelScope.launch {
        backupFiles.clearCache()
        events.send(SettingsEvent.Message("Export cache cleared"))
    }

    private fun setBusy(label: String) { busy.value = true to label }
    private fun clearBusy() { busy.value = false to null }
}

enum class CsvKind(val label: String) {
    ATTENDANCE("Attendance CSV"),
    EXPENSES("Expenses CSV"),
    FINANCE("Pay & finance CSV"),
    KEY_DATES("Key dates CSV"),
    DUTY("Official duty history CSV"),
    REMINDERS("Reminder list CSV")
}

/** Human-readable line for the restore dialog. */
fun skipReasonLabel(reason: SkipReason): String = when (reason) {
    SkipReason.INVALID_DATE -> "invalid dates"
    SkipReason.MISSING_REQUIRED_FIELD -> "missing required details"
    SkipReason.INVALID_AMOUNT -> "invalid amounts"
    SkipReason.DUPLICATE -> "already present"
    SkipReason.UNKNOWN_STATUS -> "unrecognised status"
    SkipReason.TOO_LONG -> "text too long"
    SkipReason.UNSUPPORTED -> "unsupported records"
}

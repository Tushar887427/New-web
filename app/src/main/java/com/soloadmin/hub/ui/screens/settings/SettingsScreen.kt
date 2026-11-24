package com.soloadmin.hub.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soloadmin.hub.data.preferences.StartScreen
import com.soloadmin.hub.data.preferences.ThemeChoice
import com.soloadmin.hub.domain.model.ImportGroup
import com.soloadmin.hub.domain.model.ImportConflictPolicy
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.GroupDivider
import com.soloadmin.hub.ui.components.GroupedList
import com.soloadmin.hub.ui.components.IconTile
import com.soloadmin.hub.ui.components.ListRow
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.screens.home.SheetContent
import com.soloadmin.hub.ui.theme.SoloTheme
import java.time.YearMonth
import kotlinx.coroutines.flow.collect

/**
 * Admin settings: appearance, behaviour, reminders, exports, migration and the danger zone.
 * Every control here writes real preferences or real files — there are no decorative rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (String) -> Unit,
    onSnackbar: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCsvSheet by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showLeadDays by remember { mutableStateOf(false) }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')
            viewModel.stageImport(uri, name)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.Message -> onSnackbar(event.text)
                is SettingsEvent.ImportFailed -> onSnackbar(event.message)
                is SettingsEvent.RequestNotificationsPermission -> onRequestNotificationPermission()
                is SettingsEvent.Share -> {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = event.mime
                                    putExtra(Intent.EXTRA_STREAM, viewModel.uriFor(event.file))
                                    putExtra(Intent.EXTRA_SUBJECT, event.title)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                event.title
                            )
                        )
                    }.onFailure { onSnackbar("No app available to share the file") }
                }
            }
        }
    }

    SoloScreen(
        title = "Admin settings",
        subtitle = "${state.totalRecords} records on this device"
    ) {
        item {
            SectionTitle("Appearance")
            GroupedList {
                SettingRow(
                    icon = Icons.Outlined.Palette,
                    tint = SoloTheme.colors.tileLilac,
                    title = "Theme",
                    subtitle = state.settings.theme.label
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeChoice.entries.forEach { choice ->
                            FilterChip(choice.label, state.settings.theme == choice) { viewModel.setTheme(choice) }
                        }
                    }
                }
                GroupDivider()
                ToggleRow(
                    icon = Icons.Outlined.Tune,
                    tint = SoloTheme.colors.tileSky,
                    title = "Dynamic colour",
                    subtitle = "Tint the app with the system wallpaper palette",
                    checked = state.settings.dynamicColour,
                    onCheckedChange = viewModel::setDynamicColour
                )
                GroupDivider()
                ToggleRow(
                    icon = Icons.Outlined.Description,
                    tint = SoloTheme.colors.presentSoft,
                    title = "Dashboard charts",
                    subtitle = "Show the trend and category charts",
                    checked = state.settings.dashboardCharts,
                    onCheckedChange = viewModel::setCharts
                )
                GroupDivider()
                ToggleRow(
                    icon = Icons.Outlined.Tune,
                    tint = SoloTheme.colors.warningSoft,
                    title = "Compact cards",
                    subtitle = "Tighter spacing, more rows per screen",
                    checked = state.settings.compactCards,
                    onCheckedChange = viewModel::setCompactCards
                )
                GroupDivider()
                ToggleRow(
                    icon = Icons.Outlined.Tune,
                    tint = SoloTheme.colors.earlySoft,
                    title = "Haptic feedback",
                    subtitle = "Vibrate on punches and task toggles",
                    checked = state.settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHaptics
                )
            }
        }

        item {
            SectionTitle("Start screen")
            GroupedList {
                SettingRow(
                    icon = Icons.Outlined.Person,
                    tint = SoloTheme.colors.presentSoft,
                    title = "Open app on",
                    subtitle = "Where a cold start lands"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StartScreen.entries.forEach { screen ->
                            FilterChip(screen.label, state.settings.startScreen == screen) {
                                viewModel.setStartScreen(screen)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Reminders")
            GroupedList {
                ToggleRow(
                    icon = Icons.Outlined.NotificationsActive,
                    tint = SoloTheme.colors.warningSoft,
                    title = "Expiry reminders",
                    subtitle = "Daily check for documents nearing expiry",
                    checked = state.settings.remindersEnabled,
                    onCheckedChange = viewModel::setRemindersEnabled
                )
                GroupDivider()
                ListRow(
                    title = "Remind me ahead by",
                    subtitle = "${state.settings.reminderLeadDays} days before expiry",
                    leading = { IconTile(Icons.Outlined.NotificationsActive, SoloTheme.colors.warning, SoloTheme.colors.warningSoft) },
                    showChevron = true,
                    onClick = { showLeadDays = true }
                )
                GroupDivider()
                ListRow(
                    title = "Run a reminder check now",
                    subtitle = "Fires within a minute if anything is due",
                    leading = { IconTile(Icons.Outlined.RestartAlt, MaterialTheme.colorScheme.primary, SoloTheme.colors.subtleSurface) },
                    showChevron = true,
                    onClick = viewModel::runReminderCheckNow
                )
            }
        }

        item {
            SectionTitle("Backup & migration")
            GroupedList {
                ListRow(
                    title = "Create JSON backup",
                    subtitle = "Complete snapshot you can re-import or archive",
                    leading = { IconTile(Icons.Outlined.CloudUpload, MaterialTheme.colorScheme.primary, SoloTheme.colors.presentSoft) },
                    showChevron = true,
                    onClick = viewModel::exportBackup
                )
                GroupDivider()
                ListRow(
                    title = "Import from the old HTML app",
                    subtitle = "Choose the legacy JSON backup file",
                    leading = { IconTile(Icons.Outlined.UploadFile, SoloTheme.colors.tileSky, SoloTheme.colors.subtleSurface) },
                    showChevron = true,
                    onClick = {
                        pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                    }
                )
                GroupDivider()
                ListRow(
                    title = "Restore a Solo Admin backup",
                    subtitle = "Same importer, validated before writing",
                    leading = { IconTile(Icons.Outlined.FileDownload, SoloTheme.colors.duty, SoloTheme.colors.dutySoft) },
                    showChevron = true,
                    onClick = { onNavigate("backup") }
                )
                GroupDivider()
                ListRow(
                    title = "Export reports",
                    subtitle = "CSV spreadsheets and the monthly PDF summary",
                    leading = { IconTile(Icons.Outlined.Description, SoloTheme.colors.leave, SoloTheme.colors.leaveSoft) },
                    showChevron = true,
                    onClick = { showCsvSheet = true }
                )
                GroupDivider()
                ListRow(
                    title = "Clear export cache",
                    subtitle = "Removes generated files from app storage",
                    leading = { IconTile(Icons.Outlined.Storage, SoloTheme.colors.textTertiary, SoloTheme.colors.subtleSurface) },
                    showChevron = true,
                    onClick = viewModel::clearExportCache
                )
            }
        }

        item {
            SectionTitle("Data on this device")
            SoloCard {
                state.dataGroups.forEach { (group, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            text = groupLabel(group),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Danger zone")
            GroupedList {
                ListRow(
                    title = "Reset to sample data",
                    subtitle = "Replaces everything with the demo records",
                    leading = { IconTile(Icons.Outlined.RestartAlt, SoloTheme.colors.warning, SoloTheme.colors.warningSoft) },
                    showChevron = true,
                    onClick = { showResetDialog = true }
                )
                GroupDivider()
                ListRow(
                    title = "Delete all records",
                    subtitle = "Cannot be undone — export a backup first",
                    leading = { IconTile(Icons.Outlined.DeleteForever, SoloTheme.colors.danger, SoloTheme.colors.absentSoft) },
                    titleColor = SoloTheme.colors.danger,
                    showChevron = true,
                    onClick = { showWipeDialog = true }
                )
            }
        }

        item {
            GroupedList {
                ListRow(
                    title = "About Solo Admin",
                    subtitle = "Version, storage notes and migration details",
                    leading = { IconTile(Icons.Outlined.Info, MaterialTheme.colorScheme.primary, SoloTheme.colors.subtleSurface) },
                    showChevron = true,
                    onClick = { onNavigate("about") }
                )
            }
        }
    }

    if (showCsvSheet) {
        ExportSheet(
            onDismiss = { showCsvSheet = false },
            onCsv = { kind -> viewModel.exportCsv(kind); showCsvSheet = false },
            onPdf = { viewModel.exportPdf(YearMonth.now()); showCsvSheet = false }
        )
    }

    if (showLeadDays) {
        LeadDaysDialog(
            current = state.settings.reminderLeadDays,
            onDismiss = { showLeadDays = false },
            onPick = { viewModel.setReminderLeadDays(it); showLeadDays = false }
        )
    }

    state.pendingImport?.let { pending ->
        ImportPreviewDialog(
            pending = pending,
            onPolicy = viewModel::setImportPolicy,
            onConfirm = viewModel::confirmImport,
            onCancel = viewModel::cancelImport
        )
    }

    state.lastRestore?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreSummary,
            title = { Text("Restore finished") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Imported ${summary.imported} · Updated ${summary.updated} · Skipped ${summary.skipped}")
                    if (summary.images > 0) Text("${summary.images} photos restored")
                    if (summary.skippedReasons.isNotEmpty()) {
                        Text(
                            text = "Skipped because: " + summary.skippedReasons.joinToString { (reason, count) ->
                                "${skipReasonLabel(reason)} ($count)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SoloTheme.colors.textTertiary
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissRestoreSummary) { Text("Done") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to sample data?") },
            text = { Text("All current records will be deleted and replaced with the sample set. Export a backup first if you need them.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetToSampleData(); showResetDialog = false }) {
                    Text("Reset", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Delete all records?") },
            text = { Text("Attendance, duty, expenses, pay entries, key dates, tasks and colleagues will be permanently removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllData(); showWipeDialog = false }) {
                    Text("Delete everything", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = SoloTheme.colors.textTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, MaterialTheme.colorScheme.primary, tint, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SoloTheme.colors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        trailing()
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, MaterialTheme.colorScheme.primary, tint, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SoloTheme.colors.textTertiary,
                maxLines = 2
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun groupLabel(group: ImportGroup): String = when (group) {
    ImportGroup.PROFILE -> "Profile"
    ImportGroup.ATTENDANCE -> "Attendance days"
    ImportGroup.OFFICIAL_DUTY -> "Official duty"
    ImportGroup.EXPENSES -> "Expenses"
    ImportGroup.FINANCE -> "Pay records"
    ImportGroup.KEY_DATES -> "Key dates"
    ImportGroup.TASKS -> "Daily tasks"
    ImportGroup.COLLEAGUES -> "Colleagues"
    ImportGroup.WORK_SESSIONS -> "Punch sessions"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(onDismiss: () -> Unit, onCsv: (CsvKind) -> Unit, onPdf: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        SheetContent(title = "Export reports", subtitle = "Files are written to app cache and shared") {
            CsvKind.entries.forEach { kind ->
                ListRow(
                    title = kind.label,
                    subtitle = "Formatted for Excel and Sheets",
                    leading = { IconTile(Icons.Outlined.Description, MaterialTheme.colorScheme.primary, SoloTheme.colors.subtleSurface) },
                    showChevron = true,
                    onClick = { onCsv(kind) }
                )
            }
            ListRow(
                title = "This month's PDF summary",
                subtitle = "Attendance, duty and finance in one page",
                leading = { IconTile(Icons.Outlined.Description, SoloTheme.colors.leave, SoloTheme.colors.leaveSoft) },
                showChevron = true,
                onClick = onPdf
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadDaysDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(current) }
    val options = listOf(7, 15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder lead time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "A daily check notifies you when a document is within this window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoloTheme.colors.textTertiary
                )
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { days ->
                            FilterChip("$days days", selected == days) { selected = days }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ImportPreviewDialog(
    pending: PendingImport,
    onPolicy: (ImportConflictPolicy) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val preview = pending.preview
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Review import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pending.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = SoloTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (pending.decoded.isNativeFormat) {
                        "Solo Admin backup · version " + (pending.decoded.sourceVersion ?: 1)
                    } else {
                        "Legacy HTML app backup · version " + (pending.decoded.sourceVersion ?: 6)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ImportStat("Import", preview.totalImported)
                    ImportStat("Update", preview.totalUpdated)
                    ImportStat("Skip", preview.totalSkipped)
                }
                Text(
                    text = "If a record already exists:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("Keep existing", pending.policy == ImportConflictPolicy.KEEP_EXISTING) {
                        onPolicy(ImportConflictPolicy.KEEP_EXISTING)
                    }
                    FilterChip("Overwrite", pending.policy == ImportConflictPolicy.OVERWRITE) {
                        onPolicy(ImportConflictPolicy.OVERWRITE)
                    }
                }
                if (preview.skippedSummary.isNotEmpty()) {
                    Text(
                        text = "Skipped: " + preview.skippedSummary.joinToString { (reason, count) ->
                            "${skipReasonLabel(reason)} ($count)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SoloTheme.colors.textTertiary
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Import now") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun ImportStat(label: String, value: Int) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SoloTheme.colors.textTertiary)
    }
}

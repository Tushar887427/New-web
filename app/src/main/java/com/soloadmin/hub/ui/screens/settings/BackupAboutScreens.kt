package com.soloadmin.hub.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soloadmin.hub.domain.model.ImportGroup
import com.soloadmin.hub.ui.components.GroupDivider
import com.soloadmin.hub.ui.components.GroupedList
import com.soloadmin.hub.ui.components.IconTile
import com.soloadmin.hub.ui.components.InlineNotice
import com.soloadmin.hub.ui.components.ListRow
import com.soloadmin.hub.ui.components.NoticeTone
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.theme.SoloTheme

/**
 * Backup & restore: one place to export a full snapshot, import either a native or a legacy
 * backup, and see what is stored. Import always previews before writing.
 */
@Composable
fun BackupScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    importUri: android.net.Uri? = null,
    onImportConsumed: () -> Unit = {},
    onSnackbar: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.stageImport(uri, uri.lastPathSegment?.substringAfterLast('/'))
    }

    LaunchedEffect(importUri) {
        if (importUri != null) {
            viewModel.stageImport(importUri, importUri.lastPathSegment?.substringAfterLast('/'))
            onImportConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.Message -> onSnackbar(event.text)
                is SettingsEvent.ImportFailed -> onSnackbar(event.message)
                is SettingsEvent.Share -> {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = event.mime
                        putExtra(android.content.Intent.EXTRA_STREAM, viewModel.uriFor(event.file))
                        putExtra(android.content.Intent.EXTRA_SUBJECT, event.title)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(android.content.Intent.createChooser(share, event.title))
                    }.onFailure { onSnackbar("No app available to share the file") }
                }
            }
        }
    }

    SoloScreen(title = "Backup & restore", onBack = onBack, subtitle = "Move data safely between installs") {
        item {
            InlineNotice(
                text = "Backups are plain JSON. Photos inside an old HTML backup are restored as real files.",
                tone = NoticeTone.INFO
            )
        }

        item {
            GroupedList {
                ListRow(
                    title = "Export backup file",
                    subtitle = "Full snapshot: profile, attendance, duty, money, dates, tasks, colleagues",
                    leading = { IconTile(Icons.Outlined.CloudUpload, MaterialTheme.colorScheme.primary, SoloTheme.colors.presentSoft) },
                    showChevron = true,
                    onClick = viewModel::exportBackup
                )
                GroupDivider()
                ListRow(
                    title = "Import legacy HTML backup",
                    subtitle = "solo_gov_office_hub_v6 export, validated against the original schema",
                    leading = { IconTile(Icons.Outlined.UploadFile, SoloTheme.colors.tileSky, SoloTheme.colors.subtleSurface) },
                    showChevron = true,
                    onClick = { pick.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )
                GroupDivider()
                ListRow(
                    title = "Import native backup",
                    subtitle = "Restore a file exported by this app",
                    leading = { IconTile(Icons.Outlined.FileDownload, SoloTheme.colors.duty, SoloTheme.colors.dutySoft) },
                    showChevron = true,
                    onClick = { pick.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )
            }
        }

        item {
            Text(
                text = "What a backup contains",
                style = MaterialTheme.typography.labelLarge,
                color = SoloTheme.colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            SoloCard {
                state.dataGroups.forEach { (group, count) ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = backupGroupLabel(group),
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Photos and duty attachments are copied into app-private storage on import, so deleting " +
                        "the original from your gallery will not break a record.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoloTheme.colors.textTertiary
                )
            }
        }
    }

    state.pendingImport?.let { pending ->
        ImportPreviewDialog(
            pending = pending,
            onPolicy = viewModel::setImportPolicy,
            onConfirm = viewModel::confirmImport,
            onCancel = viewModel::cancelImport
        )
    }
}

private fun backupGroupLabel(group: ImportGroup): String = when (group) {
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

/** About: version, privacy posture and the exact behaviour ported from the HTML app. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    SoloScreen(title = "About", onBack = onBack, subtitle = "Solo Admin — Gov Office Hub") {
        item {
            SoloCard {
                Text(
                    text = "Solo Admin",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "A native Android rebuild of the Gov Office Hub HTML app: attendance, official " +
                        "duty, expenses, pay and document renewals in one offline app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Version 1.0 · Kotlin, Jetpack Compose, Room, DataStore, WorkManager",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoloTheme.colors.textTertiary
                )
            }
        }

        item {
            GroupedList {
                ListRow(
                    title = "Works offline",
                    subtitle = "Every record is stored in a local Room database",
                    leading = { IconTile(Icons.Outlined.Shield, SoloTheme.colors.positive, SoloTheme.colors.positiveSoft) }
                )
                GroupDivider()
                ListRow(
                    title = "No account, no upload",
                    subtitle = "Nothing leaves the device unless you share a file yourself",
                    leading = { IconTile(Icons.Outlined.Public, SoloTheme.colors.tileSky, SoloTheme.colors.subtleSurface) }
                )
                GroupDivider()
                ListRow(
                    title = "Reports & backup",
                    subtitle = "CSV, PDF and JSON exports generated on device",
                    leading = { IconTile(Icons.Outlined.FolderZip, SoloTheme.colors.warning, SoloTheme.colors.warningSoft) }
                )
                GroupDivider()
                ListRow(
                    title = "Ported calculations",
                    subtitle = "Late marking, duty duration, headmaster recovery and net pay match the original app",
                    leading = { IconTile(Icons.Outlined.Info, MaterialTheme.colorScheme.primary, SoloTheme.colors.subtleSurface) }
                )
            }
        }

        item {
            InlineNotice(
                text = "Late arrival is any punch-in after your shift start time. Net pay is salary received " +
                    "minus personal expenses. Headmaster bills count as recoverable until you mark them recovered.",
                tone = NoticeTone.INFO
            )
        }
    }
}

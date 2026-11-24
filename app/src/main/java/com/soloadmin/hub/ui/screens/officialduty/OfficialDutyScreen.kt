package com.soloadmin.hub.ui.screens.officialduty

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.DutyMode
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import com.soloadmin.hub.ui.components.DateField
import com.soloadmin.hub.ui.components.EmptyState
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.IconTile
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.components.TimeField
import com.soloadmin.hub.ui.theme.SoloTheme

/**
 * Official duty log: the form (date, mode, times, office, location, remarks, attachment) sits
 * above the saved records, so creating and reviewing never needs a round trip.
 */
@Composable
fun OfficialDutyScreen(
    viewModel: OfficialDutyViewModel,
    onBack: () -> Unit,
    onSnackbar: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var recordToDelete by remember { mutableStateOf<OfficialDutyRecord?>(null) }
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.attachPhoto(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { onSnackbar(it) }
    }

    SoloScreen(
        title = "Official duty",
        onBack = onBack,
        subtitle = if (state.records.isEmpty()) "No records yet" else "${state.records.size} records"
    ) {
        item {
            DutyFormCard(
                state = state,
                onDateChange = { viewModel.updateForm { f -> f.copy(dateKey = it) } },
                onShiftDate = viewModel::shiftDate,
                onModeChange = { viewModel.updateForm { f -> f.copy(mode = it) } },
                onStartChange = { viewModel.updateForm { f -> f.copy(startTime = it) } },
                onEndChange = { viewModel.updateForm { f -> f.copy(endTime = it) } },
                onMeetingChange = { viewModel.updateForm { f -> f.copy(meetingWith = it.take(120)) } },
                onLocationChange = { viewModel.updateForm { f -> f.copy(location = it.take(160)) } },
                onRemarksChange = { viewModel.updateForm { f -> f.copy(remarks = it.take(600)) } },
                onPickAttachment = { attachLauncher.launch(arrayOf("image/*", "application/pdf")) },
                onClearAttachment = viewModel::clearAttachment,
                onSave = viewModel::save,
                onCancelEdit = viewModel::resetForm
            )
        }

        item {
            Text(
                text = "Saved records",
                style = MaterialTheme.typography.labelLarge,
                color = SoloTheme.colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        if (state.records.isEmpty()) {
            item {
                SoloCard(padding = 0.dp) {
                    EmptyState(
                        icon = Icons.Outlined.Badge,
                        title = "No duty records",
                        description = "Field visits you save appear here and are marked on the attendance calendar."
                    )
                }
            }
        } else {
            items(state.records.size) { index ->
                val record = state.records[index]
                DutyRecordCard(
                    record = record,
                    onEdit = { viewModel.edit(record) },
                    onDelete = { recordToDelete = record }
                )
            }
        }
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete this duty record?") },
            text = {
                Text(
                    "${record.meetingWith} on ${DateKeys.formatPretty(record.dateKey)} will be removed and the " +
                        "calendar day returned to a normal working state."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(record); recordToDelete = null }) {
                    Text("Delete", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun DutyFormCard(
    state: DutyUiState,
    onDateChange: (String) -> Unit,
    onShiftDate: (Long) -> Unit,
    onModeChange: (DutyMode) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onMeetingChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onPickAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit
) {
    val form = state.form
    val colors = SoloTheme.colors
    SoloCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = Icons.Outlined.Badge,
                tint = colors.duty,
                background = colors.dutySoft,
                size = 34.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (form.isEditing) "Editing record" else "New duty record",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Saving also marks the day as Official Duty",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
            if (form.isEditing) {
                TextButton(onClick = onCancelEdit) { Text("Cancel") }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(
                label = "Date",
                value = form.dateKey,
                onChange = onDateChange,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.padding(bottom = 1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DutyStepButton("−") { onShiftDate(-1) }
                    DutyStepButton("+") { onShiftDate(1) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Day mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DutyMode.entries.forEach { mode ->
                FilterChip(
                    label = mode.label,
                    selected = form.mode == mode,
                    onClick = { onModeChange(mode) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField("Start", form.startTime, onStartChange, Modifier.weight(1f), form.timeError)
            TimeField("End", form.endTime, onEndChange, Modifier.weight(1f), form.timeError)
        }
        if (form.timeError) {
            Text(
                text = "End time must be after start time",
                style = MaterialTheme.typography.labelSmall,
                color = colors.danger
            )
        }

        Spacer(Modifier.height(12.dp))
        SoloTextField(
            value = form.meetingWith,
            onValueChange = onMeetingChange,
            label = "Office / department visited",
            placeholder = "e.g. DEO Office, Treasury, BDO",
            isError = form.meetingError
        )

        Spacer(Modifier.height(12.dp))
        SoloTextField(
            value = form.location,
            onValueChange = onLocationChange,
            label = "Location",
            placeholder = "Office address or landmark",
            leadingIcon = Icons.Outlined.LocationOn
        )

        Spacer(Modifier.height(12.dp))
        SoloTextField(
            value = form.remarks,
            onValueChange = onRemarksChange,
            label = "Remarks",
            placeholder = "Memo number, purpose, outcome",
            singleLine = false,
            minLines = 2,
            maxLines = 4
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(onClick = onPickAttachment)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = form.attachmentPath?.let { "Attached: ${it.substringAfter('_')}" }
                    ?: "Attach order or photo (optional)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (form.attachmentPath != null) MaterialTheme.colorScheme.onSurface else colors.textTertiary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (form.attachmentPath != null) {
                TextButton(onClick = onClearAttachment) { Text("Remove") }
            }
        }

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = if (form.isEditing) "Save changes" else "Save official duty",
            icon = Icons.Outlined.Check,
            onClick = onSave
        )
    }
}

@Composable
private fun DutyStepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SoloTheme.colors.subtleSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DutyRecordCard(record: OfficialDutyRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = SoloTheme.colors
    SoloCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(
                icon = Icons.Outlined.Place,
                tint = colors.duty,
                background = colors.dutySoft,
                size = 36.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.meetingWith,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${DateKeys.formatPretty(record.dateKey)} · ${record.mode.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
                Text(
                    text = "${DateKeys.formatTime12h(record.startTime)} – ${DateKeys.formatTime12h(record.endTime)} · ${AttendanceCalculator.formatDuration(record.durationMinutes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.duty
                )
            }
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Edit",
                tint = colors.textTertiary,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(6.dp)
            )
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete",
                tint = colors.danger,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(6.dp)
            )
        }
        if (record.location.isNotBlank() || record.remarks.isNotBlank() || record.attachmentPath != null) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.subtleSurface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (record.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = record.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (record.remarks.isNotBlank()) {
                    Text(
                        text = record.remarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.attachmentPath != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Attachment saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

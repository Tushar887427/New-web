package com.soloadmin.hub.ui.screens.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.LeaveTypes
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SecondaryButton
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.components.TimeField
import com.soloadmin.hub.ui.screens.home.SheetContent
import com.soloadmin.hub.ui.theme.SoloTheme

/** Edits a single calendar day: status, leave type, times and notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorSheet(
    dateKey: String,
    existing: AttendanceRecord?,
    defaultLeaveType: String?,
    onDismiss: () -> Unit,
    onSave: (AttendanceStatus, String?, String?, String?, String) -> Unit,
    onClear: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var status by remember { mutableStateOf(existing?.status ?: AttendanceStatus.PRESENT) }
    var leaveType by remember { mutableStateOf(defaultLeaveType ?: LeaveTypes.default) }
    var inTime by remember { mutableStateOf(existing?.inTime ?: "") }
    var outTime by remember { mutableStateOf(existing?.outTime ?: "") }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var confirmClear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        SheetContent(
            title = DateKeys.formatPretty(dateKey),
            subtitle = DateKeys.parseOrNull(dateKey)?.let { DateKeys.dayShort(it) }
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                AttendanceStatus.editorOrder.forEach { option ->
                    FilterChip(
                        label = option.label,
                        selected = status == option,
                        onClick = { status = option; error = null }
                    )
                }
            }

            if (status == AttendanceStatus.LEAVE) {
                Text(
                    text = "Leave type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LeaveTypes.all.forEach { type ->
                        FilterChip(
                            label = type.removeSuffix(" Leave"),
                            selected = leaveType == type,
                            onClick = { leaveType = type }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    label = "In time",
                    value = inTime.ifBlank { "09:30" },
                    onChange = { inTime = it; error = null },
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    label = "Out time",
                    value = outTime.ifBlank { "17:00" },
                    onChange = { outTime = it; error = null },
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(onClick = { inTime = ""; outTime = "" }) {
                Text("Clear times", style = MaterialTheme.typography.labelMedium)
            }

            SoloTextField(
                value = note,
                onValueChange = { note = it.take(300) },
                label = "Note",
                placeholder = "Optional reason or remark",
                singleLine = false,
                minLines = 2,
                maxLines = 3
            )

            error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = SoloTheme.colors.danger)
            }

            PrimaryButton(
                text = "Save day",
                onClick = {
                    val s = inTime.takeIf { it.isNotBlank() }?.let { DateKeys.minutesOfDay(it) }
                    val e = outTime.takeIf { it.isNotBlank() }?.let { DateKeys.minutesOfDay(it) }
                    when {
                        (inTime.isNotBlank() && s == null) || (outTime.isNotBlank() && e == null) ->
                            error = "Enter valid in and out times"
                        s != null && e != null && e <= s ->
                            error = "Out time must be after in time"
                        else -> onSave(
                            status,
                            if (status == AttendanceStatus.LEAVE) leaveType else null,
                            inTime.takeIf { it.isNotBlank() },
                            outTime.takeIf { it.isNotBlank() },
                            note
                        )
                    }
                }
            )
            if (existing != null) {
                SecondaryButton(
                    text = "Clear this day",
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = { confirmClear = true }
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear ${DateKeys.formatPretty(dateKey)}?") },
            text = { Text("The attendance entry for this day will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) {
                    Text("Clear", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

package com.soloadmin.hub.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.LeaveTypes
import com.soloadmin.hub.ui.components.DateField
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.TimeField
import com.soloadmin.hub.ui.screens.attendance.AttendanceViewModel
import com.soloadmin.hub.ui.theme.SoloTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedPunchSheet(
    todayKey: String,
    defaultIn: String,
    defaultOut: String,
    defaultNote: String,
    shiftStart: String,
    onDismiss: () -> Unit,
    onSave: (dateKey: String, inTime: String, outTime: String, note: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dateKey by remember { mutableStateOf(todayKey) }
    var inTime by remember { mutableStateOf(defaultIn.ifBlank { "09:30" }) }
    var outTime by remember { mutableStateOf(defaultOut.ifBlank { "17:00" }) }
    var note by remember { mutableStateOf(defaultNote) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        SheetContent(title = "Log or correct office time", subtitle = "Shift starts ${DateKeys.formatTime12h(shiftStart)}") {
            DateField(label = "Date", value = dateKey, onChange = { dateKey = it; error = null })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(label = "In time", value = inTime, onChange = { inTime = it }, modifier = Modifier.weight(1f))
                TimeField(label = "Out time", value = outTime, onChange = { outTime = it }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. Forgot biometric punch") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            val start = DateKeys.minutesOfDay(inTime)
            val end = DateKeys.minutesOfDay(outTime)
            val shift = DateKeys.minutesOfDay(shiftStart) ?: 570
            if (start != null && end != null) {
                val valid = end > start
                val late = start > shift
                if (!valid) {
                    Text(
                        text = "Out time must be after in time",
                        style = MaterialTheme.typography.labelMedium,
                        color = SoloTheme.colors.danger
                    )
                } else {
                    Text(
                        text = buildString {
                            append("Worked ${AttendanceCalculator.formatDuration(end - start)}")
                            append(if (late) " · will be marked Late" else " · will be marked Present")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SoloTheme.colors.textTertiary
                    )
                }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = SoloTheme.colors.danger)
            }
            PrimaryButton(
                text = "Save time entry",
                onClick = {
                    val s = DateKeys.minutesOfDay(inTime)
                    val e = DateKeys.minutesOfDay(outTime)
                    when {
                        DateKeys.parseOrNull(dateKey) == null -> error = "Choose a valid date"
                        s == null || e == null -> error = "Enter valid in and out times"
                        e <= s -> error = "Out time must be after in time"
                        else -> onSave(dateKey, inTime, outTime, note.trim())
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLeaveSheet(
    todayKey: String,
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val vm = viewModel
    var leaveType by remember { mutableStateOf(LeaveTypes.default) }
    var fromDate by remember { mutableStateOf(todayKey) }
    var toDate by remember { mutableStateOf(todayKey) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        SheetContent(title = "Log leave", subtitle = "Casual, earned or commuted leave") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeaveTypes.all.forEach { type ->
                    FilterChip(
                        label = type.removeSuffix(" Leave"),
                        selected = leaveType == type,
                        onClick = { leaveType = type; error = null }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(label = "From", value = fromDate, onChange = { fromDate = it; error = null }, modifier = Modifier.weight(1f))
                DateField(label = "To", value = toDate, onChange = { toDate = it; error = null }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("Reason (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            val start = DateKeys.parseOrNull(fromDate)
            val end = DateKeys.parseOrNull(toDate)
            if (start != null && end != null) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
                if (days > 0) {
                    Text(
                        text = "$days day${if (days == 1L) "" else "s"} will be marked as $leaveType",
                        style = MaterialTheme.typography.labelMedium,
                        color = SoloTheme.colors.textTertiary
                    )
                } else {
                    Text(
                        text = "End date cannot be before start date",
                        style = MaterialTheme.typography.labelMedium,
                        color = SoloTheme.colors.danger
                    )
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = SoloTheme.colors.danger) }
            PrimaryButton(
                text = "Save leave",
                onClick = {
                    val startDate = DateKeys.parseOrNull(fromDate)
                    val endDate = DateKeys.parseOrNull(toDate)
                    when {
                        startDate == null || endDate == null -> error = "Choose valid dates"
                        endDate < startDate -> error = "End date cannot be before start date"
                        else -> {
                            vm.saveLeaveRange(leaveType, fromDate, toDate, note)
                            onSaved()
                        }
                    }
                }
            )
        }
    }
}

/** Shared sheet body: title, optional subtitle, then children with consistent spacing. */
@Composable
fun SheetContent(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 28.dp)
    ) {
        Box(
            Modifier
                .padding(bottom = 12.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SoloTheme.colors.textTertiary
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

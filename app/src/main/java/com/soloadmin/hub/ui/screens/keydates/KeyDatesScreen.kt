package com.soloadmin.hub.ui.screens.keydates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.ExpiryState
import com.soloadmin.hub.domain.model.KeyDateCategories
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.ui.components.CircleIconButton
import com.soloadmin.hub.ui.components.DateField
import com.soloadmin.hub.ui.components.EmptyState
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.InlineNotice
import com.soloadmin.hub.ui.components.NoticeTone
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.components.ValueBar
import com.soloadmin.hub.ui.screens.home.SheetContent
import com.soloadmin.hub.ui.theme.SoloTheme
import java.time.LocalDate

/**
 * Key dates: documents and licences with validity windows. Each card shows remaining days, a
 * validity progress bar and the expiry tone so renewals are impossible to miss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyDatesScreen(viewModel: KeyDatesViewModel, onBack: () -> Unit, onSnackbar: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = remember(state.records, state.today) { keyDateStats(state.records, state.today) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<KeyDateRecord?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { onSnackbar(it) } }

    SoloScreen(
        title = "Key dates",
        onBack = onBack,
        subtitle = "${stats.total} tracked · ${stats.needsAttention} need attention",
        actions = {
            CircleIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Add key date",
                onClick = { viewModel.startAdd(); showEditor = true },
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 36.dp
            )
        }
    ) {
        item {
            InlineNotice(
                text = "Reminders fire ${state.remindersEnabled.let { if (it) "on schedule" else "when enabled in settings" }} · " +
                    "alerts begin ${state.leadDays} days before expiry",
                tone = if (stats.needsAttention > 0) NoticeTone.WARNING else NoticeTone.INFO
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                KeyDateFilter.entries.forEach { filter ->
                    FilterChip(filter.label, state.filter == filter) { viewModel.setFilter(filter) }
                }
            }
        }

        if (state.visible.isEmpty()) {
            item {
                SoloCard(padding = 0.dp) {
                    EmptyState(
                        icon = Icons.Outlined.Event,
                        title = "No documents tracked",
                        description = "Add licences, contracts and compliance papers to get renewal alerts.",
                        actionLabel = "Add key date",
                        onAction = { viewModel.startAdd(); showEditor = true }
                    )
                }
            }
        } else {
            items(state.visible.size) { index ->
                KeyDateCard(
                    record = state.visible[index],
                    today = state.today,
                    onClick = { viewModel.startEdit(state.visible[index]); showEditor = true },
                    onDelete = { deleting = state.visible[index] }
                )
            }
        }
    }

    if (showEditor) {
        KeyDateEditorSheet(
            form = state.form,
            isEditing = state.form.id != null,
            onUpdate = viewModel::updateForm,
            onDismiss = { showEditor = false; viewModel.resetForm() },
            onSave = { viewModel.save(); showEditor = false }
        )
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete key date?") },
            text = { Text("\"${record.title}\" and its renewal history will be removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(record); deleting = null }) {
                    Text("Delete", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }
        )
    }
}

private data class KeyDateStats(val total: Int, val needsAttention: Int)

private fun keyDateStats(records: List<KeyDateRecord>, today: LocalDate): KeyDateStats = KeyDateStats(
    total = records.size,
    needsAttention = records.count { it.daysUntilExpiry(today) <= 60 }
)

@Composable
private fun KeyDateCard(record: KeyDateRecord, today: LocalDate, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = SoloTheme.colors
    val expiry = record.expiryState(today)
    val tone = when (expiry) {
        ExpiryState.EXPIRED -> colors.danger
        ExpiryState.EXPIRING -> colors.warning
        ExpiryState.VALID -> colors.positive
        ExpiryState.NO_EXPIRY -> colors.textTertiary
    }
    val toneSoft = when (expiry) {
        ExpiryState.EXPIRED -> colors.absentSoft
        ExpiryState.EXPIRING -> colors.warningSoft
        ExpiryState.VALID -> colors.positiveSoft
        ExpiryState.NO_EXPIRY -> colors.subtleSurface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
            Text(
                text = expiry.badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = tone,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(toneSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = buildString {
                    append("From ${DateKeys.formatPretty(record.validFrom)}")
                    append(if (record.validUntil.isBlank()) " · no expiry" else " · until ${DateKeys.formatPretty(record.validUntil)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }
        if (record.validUntil.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ValueBar(fraction = record.validityFraction(today), color = tone, height = 5.dp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.daysUntilExpiry(today)?.let { days ->
                        when {
                            days < 0 -> "Expired ${-days} day${if (days == -1L) "" else "s"} ago"
                            days == 0L -> "Expires today"
                            else -> "$days day${if (days == 1L) "" else "s"} remaining"
                        }
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = colors.danger.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(onClick = onDelete)
                        .padding(5.dp)
                )
            }
        }
        if (record.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = record.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyDateEditorSheet(
    form: KeyDateFormState,
    isEditing: Boolean,
    onUpdate: ((KeyDateFormState) -> KeyDateFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        SheetContent(
            title = if (isEditing) "Edit document" else "Add document",
            subtitle = "Renewal reminders use these dates"
        ) {
            SoloTextField(
                value = form.title,
                onValueChange = { value -> onUpdate { it.copy(title = value.take(120)) } },
                label = "Document name",
                placeholder = "Trade licence, service agreement…",
                isError = form.titleError
            )
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyDateCategories.all.take(3).forEach { category ->
                    FilterChip(category, form.category == category) { onUpdate { it.copy(category = category) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyDateCategories.all.drop(3).forEach { category ->
                    FilterChip(category, form.category == category) { onUpdate { it.copy(category = category) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Valid from", form.validFrom, { value -> onUpdate { it.copy(validFrom = value) } }, Modifier.weight(1f))
                DateField("Valid until", form.validUntil, { value -> onUpdate { it.copy(validUntil = value) } }, Modifier.weight(1f), form.untilError)
            }
            if (form.rangeError) {
                Text(
                    text = "Expiry must be after the start date",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoloTheme.colors.danger
                )
            }
            SoloTextField(
                value = form.note,
                onValueChange = { value -> onUpdate { it.copy(note = value.take(300)) } },
                label = "Note",
                placeholder = "Renewal office, fee, reference number",
                singleLine = false,
                minLines = 2,
                maxLines = 3
            )
            PrimaryButton(text = if (isEditing) "Save changes" else "Save document", onClick = onSave)
        }
    }
}

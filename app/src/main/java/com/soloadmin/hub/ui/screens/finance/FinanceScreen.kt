package com.soloadmin.hub.ui.screens.finance

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.FinanceStatus
import com.soloadmin.hub.ui.components.BalanceCard
import com.soloadmin.hub.ui.components.BarChart
import com.soloadmin.hub.ui.components.CircleIconButton
import com.soloadmin.hub.ui.components.DateField
import com.soloadmin.hub.ui.components.EmptyState
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.GlassStat
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.components.StatusPill
import com.soloadmin.hub.ui.screens.home.SheetContent
import com.soloadmin.hub.ui.theme.SoloTheme

@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel,
    onSnackbar: (String) -> Unit,
    onOpenReports: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FinanceRecord?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { onSnackbar(it) } }

    SoloScreen(
        title = "Pay & finance",
        subtitle = "Salary, allowances and bills",
        actions = {
            CircleIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Add pay entry",
                onClick = { viewModel.startAdd(); showEditor = true },
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 36.dp
            )
        }
    ) {
        item {
            BalanceCard(
                eyebrow = "Net balance · pay minus personal spend",
                amount = formatInr(state.netBalance),
                caption = if (state.netBalance.isNegative) "Personal spend is ahead of received pay" else "Healthy month so far",
                footer = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassStat("Salary received", formatInr(state.totalReceived), Modifier.weight(1f))
                        GlassStat("Pending bills", formatInr(state.totalPending), Modifier.weight(1f), androidx.compose.ui.graphics.Color(0xFFF3C98B))
                        GlassStat("Personal spend", formatInr(state.personalSpent), Modifier.weight(1f), androidx.compose.ui.graphics.Color(0xFFCFE9DC))
                    }
                }
            )
        }

        item {
            SoloCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Six month trend",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Received",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoloTheme.colors.textTertiary
                    )
                }
                Spacer(Modifier.height(12.dp))
                BarChart(
                    values = state.trend.map { it.received.paise / 100f },
                    labels = state.trend.map { it.label },
                    highlightIndex = state.trend.lastIndex,
                    height = 110.dp
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "This month ${formatInr(state.receivedThisMonth)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoloTheme.colors.textTertiary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenReports),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Monthly reports",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Salary, allowances & bills",
                style = MaterialTheme.typography.labelLarge,
                color = SoloTheme.colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        if (state.records.isEmpty()) {
            item {
                SoloCard(padding = 0.dp) {
                    EmptyState(
                        icon = Icons.Outlined.Payments,
                        title = "No pay records",
                        description = "Add your salary, DA arrears or a pending TA bill to see the balance.",
                        actionLabel = "Add record",
                        onAction = { viewModel.startAdd(); showEditor = true }
                    )
                }
            }
        } else {
            items(state.records.size) { index ->
                val record = state.records[index]
                FinanceRow(
                    record = record,
                    onToggle = { viewModel.toggleStatus(record) },
                    onEdit = { viewModel.startEdit(record); showEditor = true },
                    onDelete = { deleting = record }
                )
            }
        }
    }

    if (showEditor) {
        FinanceEditorSheet(
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
            title = { Text("Delete this entry?") },
            text = { Text("\"${record.title}\" (${formatInr(record.amount)}) will be removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(record); deleting = null }) {
                    Text("Delete", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun FinanceRow(
    record: FinanceRecord,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = SoloTheme.colors
    val received = record.status == FinanceStatus.RECEIVED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${DateKeys.formatPretty(record.dateKey)} · ${record.legacyType}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(onClick = onToggle),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = if (received) "Received" else "Pending · tap to mark",
                        color = if (received) colors.positive else colors.warning,
                        background = if (received) colors.positiveSoft else colors.warningSoft
                    )
                }
                Spacer(Modifier.width(8.dp))
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
        Spacer(Modifier.width(10.dp))
        Text(
            text = formatInr(record.amount),
            style = MaterialTheme.typography.titleMedium,
            color = if (received) colors.positive else colors.warning,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceEditorSheet(
    form: FinanceFormState,
    isEditing: Boolean,
    onUpdate: ((FinanceFormState) -> FinanceFormState) -> Unit,
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
            title = if (isEditing) "Edit pay record" else "Add pay record",
            subtitle = "Salary, allowances, arrears or bills"
        ) {
            SoloTextField(
                value = form.title,
                onValueChange = { value -> onUpdate { it.copy(title = value.take(120)) } },
                label = "Description",
                placeholder = "Monthly salary, DA arrears, TA bill…",
                isError = form.titleError
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoloTextField(
                    value = form.amountText,
                    onValueChange = { value ->
                        onUpdate { it.copy(amountText = value.filter { ch -> ch.isDigit() || ch == '.' }.take(12)) }
                    },
                    label = "Amount (₹)",
                    placeholder = "45000",
                    keyboardType = KeyboardType.Decimal,
                    isError = form.amountError,
                    modifier = Modifier.weight(1f)
                )
                DateField(
                    label = "Date",
                    value = form.dateKey,
                    onChange = { value -> onUpdate { it.copy(dateKey = value) } },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip("Received", form.status == FinanceStatus.RECEIVED) {
                    onUpdate { it.copy(status = FinanceStatus.RECEIVED) }
                }
                FilterChip("Pending bill", form.status == FinanceStatus.PENDING) {
                    onUpdate { it.copy(status = FinanceStatus.PENDING) }
                }
            }
            PrimaryButton(text = if (isEditing) "Save changes" else "Save record", onClick = onSave)
        }
    }
}

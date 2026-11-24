package com.soloadmin.hub.ui.screens.expenses

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Verified
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
import com.soloadmin.hub.domain.model.ExpenseCategories
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.ui.components.CircleIconButton
import com.soloadmin.hub.ui.components.DateField
import com.soloadmin.hub.ui.components.DonutChart
import com.soloadmin.hub.ui.components.ChartLegend
import com.soloadmin.hub.ui.components.ChartSlice
import com.soloadmin.hub.ui.components.EmptyState
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.OptionRow
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.components.StatusPill
import com.soloadmin.hub.ui.screens.home.SheetContent
import com.soloadmin.hub.ui.theme.SoloTheme

@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel, onSnackbar: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ExpenseRecord?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { onSnackbar(it) } }

    SoloScreen(
        title = "Expenses",
        subtitle = "${state.totalCount} record${if (state.totalCount == 1) "" else "s"}",
        actions = {
            CircleIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Add expense",
                onClick = { viewModel.startAdd(); showEditor = true },
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 36.dp
            )
        }
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecoverableCard(
                    due = formatInr(state.headmasterDue),
                    recovered = formatInr(state.headmasterRecovered),
                    modifier = Modifier.weight(1f)
                )
                PersonalCard(
                    spent = formatInr(state.personalSpent),
                    count = state.all.count { it.owner == ExpenseOwner.PERSONAL },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ExpenseFilter.entries.forEach { filter ->
                    FilterChip(filter.label, state.filter == filter) { viewModel.setFilter(filter) }
                }
            }
        }

        if (state.all.isNotEmpty()) {
            item {
                SoloCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Where the money goes",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatInr(state.headmasterDue + state.personalSpent),
                            style = MaterialTheme.typography.labelMedium,
                            color = SoloTheme.colors.textTertiary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            slices = chartSlices(state),
                            diameter = 108.dp,
                            strokeWidth = 14.dp,
                            centerLabel = formatInr(state.personalSpent + state.headmasterDue).take(9),
                            centerCaption = "total"
                        )
                        Spacer(Modifier.width(16.dp))
                        ChartLegend(chartSlices(state), Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.visible.isEmpty()) {
            item {
                SoloCard(padding = 0.dp) {
                    EmptyState(
                        icon = Icons.Outlined.Receipt,
                        title = "No expenses here",
                        description = "Add a bill to track what you paid and what the headmaster owes.",
                        actionLabel = "Add expense",
                        onAction = { viewModel.startAdd(); showEditor = true }
                    )
                }
            }
        } else {
            items(state.visible.size) { index ->
                val expense = state.visible[index]
                ExpenseRow(
                    expense = expense,
                    onToggleRecovered = { viewModel.toggleRecovered(expense) },
                    onEdit = { viewModel.startEdit(expense); showEditor = true },
                    onDelete = { deleting = expense }
                )
            }
        }
    }

    if (showEditor) {
        ExpenseEditorSheet(
            form = state.form,
            isEditing = state.form.isEditing,
            onUpdate = viewModel::updateForm,
            onDismiss = { showEditor = false; viewModel.resetForm() },
            onSave = { viewModel.save(); showEditor = false }
        )
    }

    deleting?.let { expense ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete expense?") },
            text = { Text("\"${expense.title}\" (${formatInr(expense.amount)}) will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(expense); deleting = null }) {
                    Text("Delete", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun chartSlices(state: ExpensesUiState): List<ChartSlice> {
    val colors = SoloTheme.colors
    val palette = listOf(colors.duty, colors.warning, colors.early, colors.present, colors.leave, colors.absent)
    return state.categoryTotals.take(6).mapIndexed { index, (category, amount) ->
        ChartSlice(category, amount.paise / 100f, palette[index % palette.size])
    }
}

@Composable
private fun RecoverableCard(due: String, recovered: String, modifier: Modifier = Modifier) {
    val colors = SoloTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.warningSoft)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Receipt, contentDescription = null, tint = colors.warning, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Headmaster due",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = due,
            style = MaterialTheme.typography.titleLarge,
            color = colors.warning,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Recovered $recovered",
            style = MaterialTheme.typography.labelSmall,
            color = colors.warning.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PersonalCard(spent: String, count: Int, modifier: Modifier = Modifier) {
    val colors = SoloTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .background(colors.cardSurface)
            .padding(14.dp)
    ) {
        Text(
            text = "Personal spent",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = spent,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$count personal bill${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: ExpenseRecord,
    onToggleRecovered: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = SoloTheme.colors
    val isHeadmaster = expense.owner == ExpenseOwner.HEADMASTER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    if (isHeadmaster) {
                        StatusPill(
                            text = if (expense.recovered) "Recovered" else "Due",
                            color = if (expense.recovered) colors.positive else colors.warning,
                            background = if (expense.recovered) colors.positiveSoft else colors.warningSoft
                        )
                    } else {
                        StatusPill("Personal", MaterialTheme.colorScheme.onSurfaceVariant, colors.subtleSurface)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(DateKeys.formatPretty(expense.dateKey))
                        append(" · ").append(expense.category)
                        if (expense.note.isNotBlank()) append(" · ").append(expense.note)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatInr(expense.amount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isHeadmaster) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleRecovered)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expense.recovered) Icons.Outlined.Undo else Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = if (expense.recovered) colors.textTertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (expense.recovered) "Mark as due" else "Mark recovered",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expense.recovered) colors.textTertiary else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    text = "Personal expense",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete expense",
                tint = colors.danger.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditorSheet(
    form: ExpenseFormState,
    isEditing: Boolean,
    onUpdate: ((ExpenseFormState) -> ExpenseFormState) -> Unit,
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
            title = if (isEditing) "Edit expense" else "Add expense",
            subtitle = "Headmaster bills stay recoverable until you mark them settled"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionRow(
                    label = "Headmaster",
                    supporting = "Recoverable",
                    selected = form.owner == ExpenseOwner.HEADMASTER,
                    onClick = { onUpdate { it.copy(owner = ExpenseOwner.HEADMASTER) } },
                    modifier = Modifier.weight(1f)
                )
                OptionRow(
                    label = "Personal",
                    selected = form.owner == ExpenseOwner.PERSONAL,
                    onClick = { onUpdate { it.copy(owner = ExpenseOwner.PERSONAL) } },
                    modifier = Modifier.weight(1f)
                )
            }
            SoloTextField(
                value = form.title,
                onValueChange = { value -> onUpdate { it.copy(title = value.take(120)) } },
                label = "Title",
                placeholder = "Stationery, printing, travel…",
                isError = form.titleError
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoloTextField(
                    value = form.amountText,
                    onValueChange = { value ->
                        onUpdate { it.copy(amountText = value.filter { ch -> ch.isDigit() || ch == '.' }.take(12)) }
                    },
                    label = "Amount (₹)",
                    placeholder = "500",
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
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpenseCategories.all.take(3).forEach { category ->
                    FilterChip(category, form.category == category) { onUpdate { it.copy(category = category) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpenseCategories.all.drop(3).forEach { category ->
                    FilterChip(category, form.category == category) { onUpdate { it.copy(category = category) } }
                }
            }
            SoloTextField(
                value = form.note,
                onValueChange = { value -> onUpdate { it.copy(note = value.take(200)) } },
                label = "Voucher / note",
                placeholder = "Optional",
                singleLine = true
            )
            PrimaryButton(
                text = if (isEditing) "Save changes" else "Save expense",
                onClick = onSave
            )
        }
    }
}

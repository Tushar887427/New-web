package com.soloadmin.hub.ui.screens.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soloadmin.hub.domain.model.DailyTaskRecord
import com.soloadmin.hub.ui.components.EmptyState
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.theme.SoloTheme
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Daily tasks: an always-visible quick add field, pending/completed filters, tap-to-complete rows
 * with a satisfying check animation, and inline rename/delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newTask by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<DailyTaskRecord?>(null) }
    var deleting by remember { mutableStateOf<DailyTaskRecord?>(null) }

    SoloScreen(
        title = "Daily tasks",
        onBack = onBack,
        subtitle = if (state.pending.isEmpty()) "All clear" else "${state.pending.size} pending"
    ) {
        item {
            SoloCard(padding = 14.dp) {
                SoloTextField(
                    value = newTask,
                    onValueChange = { newTask = it.take(300) },
                    placeholder = "Add an office task…",
                    singleLine = true,
                    trailing = {
                        if (newTask.isNotBlank()) {
                            TextButton(onClick = {
                                viewModel.add(newTask)
                                newTask = ""
                            }) {
                                Text("Add", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                )
                if (newTask.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(text = "Add task", icon = Icons.Outlined.Add, onClick = {
                        viewModel.add(newTask)
                        newTask = ""
                    })
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip("Pending (${state.pending.size})", state.filter == TaskFilter.PENDING) {
                    viewModel.setFilter(TaskFilter.PENDING)
                }
                FilterChip("Completed (${state.completed.size})", state.filter == TaskFilter.COMPLETED) {
                    viewModel.setFilter(TaskFilter.COMPLETED)
                }
                FilterChip("All", state.filter == TaskFilter.ALL) { viewModel.setFilter(TaskFilter.ALL) }
            }
        }

        if (state.visible.isEmpty()) {
            item {
                SoloCard(padding = 0.dp) {
                    EmptyState(
                        icon = Icons.Outlined.TaskAlt,
                        title = when (state.filter) {
                            TaskFilter.PENDING -> "No pending tasks"
                            TaskFilter.COMPLETED -> "Nothing completed yet"
                            TaskFilter.ALL -> "No tasks yet"
                        },
                        description = "Type above to add today's office work."
                    )
                }
            }
        } else {
            items(state.visible.size) { index ->
                val task = state.visible[index]
                TaskRow(
                    task = task,
                    onToggle = { viewModel.toggle(task) },
                    onEdit = { editing = task },
                    onDelete = { deleting = task }
                )
            }
        }
    }

    editing?.let { task ->
        var text by remember(task.id) { mutableStateOf(task.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit task") },
            text = {
                SoloTextField(value = text, onValueChange = { text = it.take(300) }, singleLine = false, maxLines = 3)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update(task, text)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete task?") },
            text = { Text("\"${task.text}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(task); deleting = null }) {
                    Text("Delete", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TaskRow(
    task: DailyTaskRecord,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = SoloTheme.colors
    val haptics = LocalHapticFeedback.current
    val checkScale by animateFloatAsState(
        targetValue = if (task.done) 1f else 0f,
        animationSpec = tween(180),
        label = "check"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardSurface)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (task.done) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                .border(if (task.done) 0.dp else 1.6.dp, colors.offday, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checkScale > 0.05f) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp).scale(checkScale)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.done) colors.textTertiary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
            )
        }
        Icon(
            Icons.Outlined.Edit,
            contentDescription = "Edit task",
            tint = colors.textTertiary,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEdit)
                .padding(6.dp)
        )
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = "Delete task",
            tint = colors.danger,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete)
                .padding(6.dp)
        )
    }
}

package com.soloadmin.hub.ui.screens.colleagues

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.soloadmin.hub.domain.model.ColleagueRecord
import com.soloadmin.hub.domain.model.ColleagueTheme
import com.soloadmin.hub.domain.model.InfoField
import com.soloadmin.hub.ui.components.CircleIconButton
import com.soloadmin.hub.data.files.photoFile
import com.soloadmin.hub.ui.components.EmptyState
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.screens.home.SheetContent
import com.soloadmin.hub.ui.theme.SoloTheme

/**
 * Colleagues directory: searchable cards with photo, role, theme tint and flexible info pairs
 * (mobile, ID, desk, anything the office needs).
 */
@Composable
fun ColleaguesScreen(viewModel: ColleaguesViewModel, onBack: () -> Unit, onSnackbar: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ColleagueRecord?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { onSnackbar(it) } }

    SoloScreen(
        title = "Colleagues",
        onBack = onBack,
        subtitle = "${state.all.size} in your directory",
        actions = {
            CircleIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Add colleague",
                onClick = { viewModel.startAdd(); showEditor = true },
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 36.dp
            )
        }
    ) {
        item {
            SoloTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search by name, role or contact",
                leadingIcon = Icons.Outlined.Search,
                trailing = if (state.query.isNotEmpty()) {
                    {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = SoloTheme.colors.textTertiary,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.setQuery("") }
                                .padding(7.dp)
                        )
                    }
                } else null
            )
        }

        if (state.visible.isEmpty()) {
            item {
                SoloCard(padding = 0.dp) {
                    EmptyState(
                        icon = Icons.Outlined.Groups,
                        title = if (state.query.isBlank()) "No colleagues yet" else "No matches",
                        description = if (state.query.isBlank()) {
                            "Save the people you work with for quick contact details."
                        } else {
                            "Try a different name, role or number."
                        },
                        actionLabel = if (state.query.isBlank()) "Add colleague" else null,
                        onAction = if (state.query.isBlank()) {
                            { viewModel.startAdd(); showEditor = true }
                        } else null
                    )
                }
            }
        } else {
            items(state.visible.size) { index ->
                val colleague = state.visible[index]
                ColleagueCard(
                    colleague = colleague,
                    onEdit = { viewModel.startEdit(colleague); showEditor = true },
                    onDelete = { deleting = colleague }
                )
            }
        }
    }

    if (showEditor) {
        ColleagueEditorSheet(
            state = state,
            onUpdate = viewModel::updateForm,
            onAddField = viewModel::addInfoField,
            onUpdateField = viewModel::updateInfoField,
            onRemoveField = viewModel::removeInfoField,
            onPhoto = viewModel::setPhoto,
            onCover = viewModel::setCover,
            onRemovePhoto = viewModel::clearPhoto,
            onRemoveCover = viewModel::clearCover,
            onDismiss = { showEditor = false; viewModel.resetForm() },
            onSave = { viewModel.save(); showEditor = false }
        )
    }

    deleting?.let { colleague ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove colleague?") },
            text = { Text("${colleague.name} and their saved contact details will be deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(colleague.id); deleting = null }) {
                    Text("Remove", color = SoloTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }
        )
    }
}

@Composable
fun colleagueAccent(theme: ColleagueTheme): androidx.compose.ui.graphics.Color = when (theme) {
    ColleagueTheme.SKY -> SoloTheme.colors.tileSky
    ColleagueTheme.MINT -> SoloTheme.colors.presentSoft
    ColleagueTheme.SUNSET -> SoloTheme.colors.warningSoft
    ColleagueTheme.LAVENDER -> SoloTheme.colors.tileLilac
}

@Composable
private fun ColleagueCard(colleague: ColleagueRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = SoloTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColleagueAvatar(colleague, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = colleague.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = colleague.role.ifBlank { "Colleague" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete",
                tint = colors.danger.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onDelete)
                    .padding(6.dp)
            )
        }
        val fields = colleague.info.filter { it.label.isNotBlank() && it.value.isNotBlank() }
        if (fields.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.subtleSurface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                fields.take(4).forEach { field ->
                    Row {
                        Text(
                            text = field.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            modifier = Modifier.width(78.dp)
                        )
                        Text(
                            text = field.value,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColleagueAvatar(colleague: ColleagueRecord, size: androidx.compose.ui.unit.Dp) {
    val initials = colleague.name.trim().take(2).uppercase().ifBlank { "??" }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colleagueAccent(colleague.theme)),
        contentAlignment = Alignment.Center
    ) {
        if (colleague.photoPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoFile(colleague.photoPath))
                    .crossfade(true)
                    .build(),
                contentDescription = colleague.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColleagueEditorSheet(
    state: ColleaguesUiState,
    onUpdate: ((ColleagueFormState) -> ColleagueFormState) -> Unit,
    onAddField: () -> Unit,
    onUpdateField: (Int, InfoField) -> Unit,
    onRemoveField: (Int) -> Unit,
    onPhoto: (Uri) -> Unit,
    onCover: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onRemoveCover: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val form = state.form
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri)
    }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onCover(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        SheetContent(
            title = if (form.id != null) "Edit colleague" else "Add colleague",
            subtitle = "Photo and contact details stay on this device"
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(SoloTheme.colors.subtleSurface)
                        .clickable {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (form.photoPath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photoFile(form.photoPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(58.dp)
                        )
                    } else {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = "Add photo",
                            tint = SoloTheme.colors.textTertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = {
                        photoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text(if (form.photoPath == null) "Choose photo" else "Change photo")
                    }
                    if (form.photoPath != null) {
                        TextButton(onClick = onRemovePhoto) {
                            Text("Remove photo", color = SoloTheme.colors.danger)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    coverLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Text(if (form.coverPath == null) "Add cover image" else "Change cover image")
                }
                if (form.coverPath != null) {
                    TextButton(onClick = onRemoveCover) {
                        Text("Remove cover", color = SoloTheme.colors.danger)
                    }
                }
            }
            SoloTextField(
                value = form.name,
                onValueChange = { value -> onUpdate { it.copy(name = value.take(80)) } },
                label = "Name",
                isError = form.nameError
            )
            SoloTextField(
                value = form.role,
                onValueChange = { value -> onUpdate { it.copy(role = value.take(80)) } },
                label = "Role / designation",
                placeholder = "Senior clerk, headmaster…"
            )
            Text(
                text = "Card colour",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColleagueTheme.entries.forEach { theme ->
                    FilterChip(theme.label, form.theme == theme) { onUpdate { it.copy(theme = theme) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAddField) { Text("Add field") }
            }
            form.info.forEachIndexed { index, field ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoloTextField(
                        value = field.label,
                        onValueChange = { onUpdateField(index, field.copy(label = it.take(30))) },
                        placeholder = "Label",
                        modifier = Modifier.weight(0.8f)
                    )
                    SoloTextField(
                        value = field.value,
                        onValueChange = { onUpdateField(index, field.copy(value = it.take(120))) },
                        placeholder = "Value",
                        modifier = Modifier.weight(1.2f)
                    )
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove field",
                        tint = SoloTheme.colors.textTertiary,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onRemoveField(index) }
                            .padding(6.dp)
                    )
                }
            }
            PrimaryButton(text = if (form.id != null) "Save changes" else "Save colleague", onClick = onSave)
        }
    }
}

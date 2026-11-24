package com.soloadmin.hub.ui.screens.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.soloadmin.hub.data.files.photoFile
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.InfoField
import com.soloadmin.hub.ui.components.FilterChip
import com.soloadmin.hub.ui.components.Weekdays
import com.soloadmin.hub.ui.components.PrimaryButton
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.SoloTextField
import com.soloadmin.hub.ui.components.TimeField
import com.soloadmin.hub.ui.theme.SoloTheme
import kotlinx.coroutines.flow.collect

/**
 * My profile: identity card, editable details, flexible info pairs and the working schedule
 * (shift start + weekly off) that drives late-marking across the app.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onOpenColleagues: () -> Unit,
    onSnackbar: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { onSnackbar(it) } }

    SoloScreen(
        title = "My profile",
        onBack = onBack,
        subtitle = "Identity and working hours",
        actions = {
            if (!editing) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { viewModel.resetForm(); editing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit profile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
            }
        }
    ) {
        item {
            ProfileHeader(
                name = state.profile.name,
                bio = state.profile.bio,
                photoPath = state.profile.photoPath,
                weeklyOff = state.weeklyOff,
                shiftStart = state.profile.shiftStart
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoloCard(modifier = Modifier.weight(1f), padding = 14.dp) {
                    Text("Days marked", style = MaterialTheme.typography.labelSmall, color = SoloTheme.colors.textTertiary)
                    Text(
                        text = state.attendanceCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Attendance entries", style = MaterialTheme.typography.labelSmall, color = SoloTheme.colors.textTertiary)
                }
                SoloCard(modifier = Modifier.weight(1f), padding = 14.dp) {
                    Text("Directory", style = MaterialTheme.typography.labelSmall, color = SoloTheme.colors.textTertiary)
                    Text(
                        text = state.colleagueCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Colleagues saved", style = MaterialTheme.typography.labelSmall, color = SoloTheme.colors.textTertiary)
                }
            }
        }

        if (!editing) {
            item { ProfileDetails(state.profile.info) }
            item {
                SoloCard(onClick = onOpenColleagues) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Colleague directory", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "${state.colleagueCount} saved contacts",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoloTheme.colors.textTertiary
                            )
                        }
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = SoloTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            item {
                SoloCard {
                    Text(
                        text = "Working schedule",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Shift starts ${DateKeys.formatTime12h(state.profile.shiftStart)} · weekly off ${state.weeklyOff}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { viewModel.resetForm(); editing = true }) { Text("Edit schedule & details") }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileEditor(
                        state = state,
                        onUpdate = viewModel::updateForm,
                        onAddField = viewModel::addInfoField,
                        onUpdateField = viewModel::updateInfoField,
                        onRemoveField = viewModel::removeInfoField,
                        onPhoto = viewModel::setPhoto,
                        onClearPhoto = viewModel::clearPhoto,
                        onSave = { viewModel.save(); editing = false },
                        onCancel = { editing = false }
                    )
                    ScheduleCard(
                        weeklyOff = state.profile.weeklyOffDay,
                        shiftStart = state.profile.shiftStart,
                        onSaveSchedule = { off, start ->
                            viewModel.saveSchedule(state.form.name.ifBlank { state.profile.name }, off, start)
                        }
                    )
                    PrimaryButton(text = "Save profile", onClick = { viewModel.save(); editing = false })
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(name: String, bio: String, photoPath: String?, weeklyOff: String, shiftStart: String) {
    val colors = SoloTheme.colors
    SoloCard(padding = 18.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(colors.subtleSurface)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val photo = photoFile(photoPath)
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(78.dp)
                    )
                } else {
                    Text(
                        text = name.trim().take(2).uppercase().ifBlank { "SA" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = name.ifBlank { "Add your name" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (bio.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileChip("Shift ${DateKeys.formatTime12h(shiftStart)}")
                ProfileChip("Off: $weeklyOff")
            }
        }
    }
}

@Composable
private fun ProfileChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(SoloTheme.colors.subtleSurface)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ProfileDetails(info: List<InfoField>) {
    val fields = info.filter { it.label.isNotBlank() && it.value.isNotBlank() }
    SoloCard {
        Text(
            text = "Details",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        if (fields.isEmpty()) {
            Text(
                text = "No details saved yet. Add mobile, employee ID, school name and so on.",
                style = MaterialTheme.typography.bodySmall,
                color = SoloTheme.colors.textTertiary
            )
        } else {
            fields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = field.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = SoloTheme.colors.textTertiary,
                        modifier = Modifier.width(96.dp)
                    )
                    Text(
                        text = field.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    state: ProfileUiState,
    onUpdate: ((ProfileFormState) -> ProfileFormState) -> Unit,
    onAddField: () -> Unit,
    onUpdateField: (Int, InfoField) -> Unit,
    onRemoveField: (Int) -> Unit,
    onPhoto: (Uri) -> Unit,
    onClearPhoto: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val form = state.form
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri)
    }

    SoloCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Edit profile",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SoloTheme.colors.subtleSurface)
                    .clickable { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                val photo = photoFile(form.photoPath)
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "Add photo", tint = SoloTheme.colors.textTertiary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                TextButton(onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (form.photoPath == null) "Choose photo" else "Change photo")
                }
                if (form.photoPath != null) {
                    TextButton(onClick = onClearPhoto) { Text("Remove", color = SoloTheme.colors.danger) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SoloTextField(
            value = form.name,
            onValueChange = { value -> onUpdate { it.copy(name = value.take(80)) } },
            label = "Name",
            isError = form.nameError
        )
        Spacer(Modifier.height(10.dp))
        SoloTextField(
            value = form.bio,
            onValueChange = { value -> onUpdate { it.copy(bio = value.take(200)) } },
            label = "Bio / designation",
            placeholder = "Senior clerk at Zilla Parishad office",
            singleLine = false,
            minLines = 2,
            maxLines = 3
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onValueChange = { onUpdateField(index, field.copy(value = it.take(140))) },
                    placeholder = "Value",
                    modifier = Modifier.weight(1.2f)
                )
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove",
                    tint = SoloTheme.colors.textTertiary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onRemoveField(index) }
                        .padding(6.dp)
                )
            }
        }
    }
}

/** Schedule changes are saved immediately: they alter how every future punch is marked. */
@Composable
private fun ScheduleCard(
    weeklyOff: Int,
    shiftStart: String,
    onSaveSchedule: (Int, String) -> Unit
) {
    var selectedOff by remember(weeklyOff) { mutableStateOf(weeklyOff) }
    var start by remember(shiftStart) { mutableStateOf(shiftStart) }
    SoloCard {
        Text(
            text = "Working schedule",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Used to detect late arrivals and weekly off days",
            style = MaterialTheme.typography.labelSmall,
            color = SoloTheme.colors.textTertiary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Weekdays.take(4).forEachIndexed { index, day ->
                FilterChip(day, selectedOff == index) { selectedOff = index }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Weekdays.drop(4).forEachIndexed { index, day ->
                FilterChip(day, selectedOff == index + 4) { selectedOff = index + 4 }
            }
        }
        Spacer(Modifier.height(10.dp))
        TimeField(label = "Shift start", value = start, onChange = { start = it })
        Spacer(Modifier.height(8.dp))
        PrimaryButton(text = "Save schedule", onClick = { onSaveSchedule(selectedOff, start) })
    }
}

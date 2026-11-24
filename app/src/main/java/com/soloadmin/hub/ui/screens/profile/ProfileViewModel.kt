package com.soloadmin.hub.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.files.MediaStore
import com.soloadmin.hub.data.repository.AttendanceRepository
import com.soloadmin.hub.data.repository.ColleagueRepository
import com.soloadmin.hub.data.repository.ProfileRepository
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.InfoField
import com.soloadmin.hub.domain.model.ProfileRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileFormState(
    val name: String = "",
    val bio: String = "",
    val photoPath: String? = null,
    val info: List<InfoField> = emptyList(),
    val showErrors: Boolean = false
) {
    val nameError: Boolean get() = showErrors && name.isBlank()
}

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: ProfileRecord = ProfileRecord(),
    val attendanceCount: Int = 0,
    val colleagueCount: Int = 0,
    val form: ProfileFormState = ProfileFormState()
) {
    val weeklyOff: String get() = profile.weeklyOffLabel
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val attendanceRepository: AttendanceRepository,
    private val colleagueRepository: ColleagueRepository,
    private val mediaStore: MediaStore
) : ViewModel() {

    private val form = MutableStateFlow(ProfileFormState())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.observe(),
        attendanceRepository.observeAll(),
        colleagueRepository.observeAll(),
        form
    ) { values ->
        val profile = values[0] as ProfileRecord
        val attendance = values[1] as List<com.soloadmin.hub.domain.model.AttendanceRecord>
        val colleagues = values[2] as List<com.soloadmin.hub.domain.model.ColleagueRecord>
        val formState = values[3] as ProfileFormState
        ProfileUiState(
            loading = false,
            profile = profile,
            attendanceCount = attendance.size,
            colleagueCount = colleagues.size,
            form = formState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init {
        // Seed the editor once from storage; later edits are user-driven.
        viewModelScope.launch {
            val profile = profileRepository.get()
            form.value = ProfileFormState(
                name = profile.name,
                bio = profile.bio,
                photoPath = profile.photoPath,
                info = profile.info.ifEmpty { listOf(InfoField("ID", ""), InfoField("Office", "")) }
            )
        }
    }

    fun resetForm() {
        val profile = uiState.value.profile
        form.value = ProfileFormState(
            name = profile.name,
            bio = profile.bio,
            photoPath = profile.photoPath,
            info = profile.info.ifEmpty { listOf(InfoField("ID", ""), InfoField("Office", "")) }
        )
    }

    fun updateForm(block: (ProfileFormState) -> ProfileFormState) = form.update(block)

    fun addInfoField() = form.update { it.copy(info = it.info + InfoField("", "")) }

    fun updateInfoField(index: Int, field: InfoField) = form.update { state ->
        val next = state.info.toMutableList()
        if (index in next.indices) next[index] = field
        state.copy(info = next)
    }

    fun removeInfoField(index: Int) = form.update { state ->
        val next = state.info.toMutableList()
        if (index in next.indices) next.removeAt(index)
        state.copy(info = next)
    }

    fun setPhoto(uri: android.net.Uri) = viewModelScope.launch {
        val name = mediaStore.importImage(uri, "profile")
        if (name == null) events.send("Could not read that photo") else form.update { it.copy(photoPath = name) }
    }

    fun clearPhoto() = form.update { it.copy(photoPath = null) }

    /** Quick photo replacement from the portrait card, without opening the full editor. */
    fun quickSetPhoto(uri: android.net.Uri) = viewModelScope.launch {
        val name = mediaStore.importImage(uri, "profile") ?: run {
            events.send("Could not read that photo")
            return@launch
        }
        val profile = uiState.value.profile
        profileRepository.save(profile.copy(photoPath = name))
        form.update { it.copy(photoPath = name) }
        events.send("Profile photo updated")
    }

    fun save() = viewModelScope.launch {
        val current = form.value
        if (current.name.isBlank()) {
            form.update { it.copy(showErrors = true) }
            events.send("Enter your name")
            return@launch
        }
        val profile = uiState.value.profile
        val cleanInfo = current.info
            .filter { it.label.isNotBlank() || it.value.isNotBlank() }
            .map { InfoField(it.label.trim().ifEmpty { "Info" }, it.value.trim()) }
            .take(12)
        profileRepository.save(
            profile.copy(
                name = current.name.trim(),
                bio = current.bio.trim(),
                photoPath = current.photoPath,
                info = cleanInfo
            )
        )
        events.send("Profile saved")
    }

    /** Work schedule settings live on the profile record (shared with Home). */
    fun saveSchedule(name: String, weeklyOff: Int, shiftStart: String) = viewModelScope.launch {
        if (!DateKeys.isValidTime(shiftStart)) {
            events.send("Enter a valid office start time")
            return@launch
        }
        val profile = uiState.value.profile
        profileRepository.save(
            profile.copy(
                name = name.trim().ifEmpty { "Admin" },
                weeklyOffDay = weeklyOff.coerceIn(0, 6),
                shiftStart = shiftStart
            )
        )
        events.send("Preferences saved")
    }
}

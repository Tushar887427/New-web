package com.soloadmin.hub.ui.screens.officialduty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.files.MediaStore
import com.soloadmin.hub.data.repository.AttendanceRepository
import com.soloadmin.hub.data.repository.OfficialDutyRepository
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.DutyMode
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DutyFormState(
    val id: String? = null,
    val dateKey: String = DateKeys.of(LocalDate.now()),
    val mode: DutyMode = DutyMode.default,
    val startTime: String = "10:00",
    val endTime: String = "16:00",
    val meetingWith: String = "",
    val location: String = "",
    val remarks: String = "",
    val attachmentPath: String? = null,
    val showErrors: Boolean = false
) {
    val isEditing: Boolean get() = id != null
    val meetingError: Boolean get() = showErrors && meetingWith.isBlank()
    val timeError: Boolean
        get() = showErrors && ((DateKeys.minutesOfDay(endTime) ?: 0) <= (DateKeys.minutesOfDay(startTime) ?: 0))
}

data class DutyUiState(
    val loading: Boolean = true,
    val records: List<OfficialDutyRecord> = emptyList(),
    val form: DutyFormState = DutyFormState()
)

class OfficialDutyViewModel(
    private val dutyRepository: OfficialDutyRepository,
    private val attendanceRepository: AttendanceRepository,
    private val mediaStore: MediaStore
) : ViewModel() {

    private val form = MutableStateFlow(DutyFormState())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<DutyUiState> = combine(
        dutyRepository.observeAll(),
        form
    ) { records, formState ->
        DutyUiState(loading = false, records = records, form = formState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DutyUiState())

    fun updateForm(block: (DutyFormState) -> DutyFormState) {
        form.update { block(it) }
    }

    fun shiftDate(days: Long) = form.update {
        val current = DateKeys.parseOrNull(it.dateKey) ?: LocalDate.now()
        it.copy(dateKey = DateKeys.of(current.plusDays(days)))
    }

    fun edit(record: OfficialDutyRecord) = form.update {
        DutyFormState(
            id = record.id,
            dateKey = record.dateKey,
            mode = record.mode,
            startTime = record.startTime,
            endTime = record.endTime,
            meetingWith = record.meetingWith,
            location = record.location,
            remarks = record.remarks,
            attachmentPath = record.attachmentPath
        )
    }

    fun resetForm() = form.value = DutyFormState()

    fun attachPhoto(uri: android.net.Uri) = viewModelScope.launch {
        val name = mediaStore.importAttachment(uri)
        if (name == null) {
            events.send("That file could not be attached")
        } else {
            form.update { it.copy(attachmentPath = name) }
            events.send("Attachment added")
        }
    }

    fun clearAttachment() {
        form.update { it.copy(attachmentPath = null) }
    }

    fun save() = viewModelScope.launch {
        val current = form.value
        if (current.meetingWith.isBlank()) {
            form.update { it.copy(showErrors = true) }
            events.send("Enter the office or department visited")
            return@launch
        }
        val start = DateKeys.minutesOfDay(current.startTime)
        val end = DateKeys.minutesOfDay(current.endTime)
        if (start == null || end == null) {
            form.update { it.copy(showErrors = true) }
            events.send("Choose valid start and end times")
            return@launch
        }
        if (end <= start) {
            form.update { it.copy(showErrors = true) }
            events.send("End time must be after start time")
            return@launch
        }
        val record = OfficialDutyRecord(
            id = current.id ?: "oow-${System.currentTimeMillis()}",
            dateKey = current.dateKey,
            mode = current.mode,
            startTime = current.startTime,
            endTime = current.endTime,
            meetingWith = current.meetingWith.trim(),
            location = current.location.trim(),
            remarks = current.remarks.trim(),
            attachmentPath = current.attachmentPath,
            createdAt = System.currentTimeMillis()
        )
        dutyRepository.saveWithAttendance(record)
        form.value = DutyFormState()
        events.send(if (current.isEditing) "Official duty updated" else "Official duty saved & marked on calendar")
    }

    fun delete(record: OfficialDutyRecord) = viewModelScope.launch {
        dutyRepository.delete(record)
        // Restore the day to a normal working state when the linked duty is removed.
        val attendance = attendanceRepository.get(record.dateKey)
        if (attendance?.officialDutyId == record.id) {
            attendanceRepository.upsert(
                attendance.copy(
                    status = com.soloadmin.hub.domain.model.AttendanceStatus.PRESENT,
                    officialDutyId = null,
                    note = ""
                )
            )
        }
        events.send("Official duty record deleted")
    }
}

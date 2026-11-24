package com.soloadmin.hub.ui.screens.keydates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.preferences.SettingsRepository
import com.soloadmin.hub.data.repository.KeyDateRepository
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.KeyDateCategories
import com.soloadmin.hub.domain.model.KeyDateRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class KeyDateFilter(val label: String) {
    ALL("All key dates"),
    SOON("Expiring in 60 days"),
    EXPIRED("Expired")
}

data class KeyDateFormState(
    val id: String? = null,
    val title: String = "",
    val category: String = KeyDateCategories.default,
    val validFrom: String = DateKeys.of(LocalDate.now()),
    val validUntil: String = "",
    val note: String = "",
    val showErrors: Boolean = false
) {
    val titleError: Boolean get() = showErrors && title.isBlank()
    val untilError: Boolean get() = showErrors && DateKeys.parseOrNull(validUntil) == null
    val rangeError: Boolean
        get() {
            val from = DateKeys.parseOrNull(validFrom) ?: return false
            val until = DateKeys.parseOrNull(validUntil) ?: return false
            return until < from
        }
}

data class KeyDatesUiState(
    val loading: Boolean = true,
    val records: List<KeyDateRecord> = emptyList(),
    val filter: KeyDateFilter = KeyDateFilter.ALL,
    val form: KeyDateFormState = KeyDateFormState(),
    val today: LocalDate = LocalDate.now(),
    val remindersEnabled: Boolean = true,
    val leadDays: Int = 30
) {
    val visible: List<KeyDateRecord>
        get() = records.filter { record ->
            val days = record.daysUntilExpiry(today)
            when (filter) {
                KeyDateFilter.ALL -> true
                KeyDateFilter.SOON -> days in 0..60
                KeyDateFilter.EXPIRED -> days < 0
            }
        }.sortedBy { it.validUntil }

    val needsAttention: Int
        get() = records.count { it.daysUntilExpiry(today) <= 60 }
}

class KeyDatesViewModel(
    private val repository: KeyDateRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val filter = MutableStateFlow(KeyDateFilter.ALL)
    private val form = MutableStateFlow(KeyDateFormState())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<KeyDatesUiState> = combine(
        repository.observeAll(),
        filter,
        form,
        settingsRepository.settings
    ) { records, activeFilter, formState, settings ->
        KeyDatesUiState(
            remindersEnabled = settings.remindersEnabled,
            leadDays = settings.reminderLeadDays,
            loading = false,
            records = records,
            filter = activeFilter,
            form = formState,
            today = LocalDate.now()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeyDatesUiState())

    fun setFilter(value: KeyDateFilter) { filter.value = value }

    fun startAdd() { form.value = KeyDateFormState() }

    fun startEdit(record: KeyDateRecord) {
        form.value = KeyDateFormState(
            id = record.id,
            title = record.title,
            category = record.category,
            validFrom = record.validFrom,
            validUntil = record.validUntil,
            note = record.note
        )
    }

    fun resetForm() { form.value = KeyDateFormState() }

    fun updateForm(block: (KeyDateFormState) -> KeyDateFormState) = form.update(block)

    fun save() = viewModelScope.launch {
        val current = form.value
        if (current.title.isBlank()) {
            form.update { it.copy(showErrors = true) }
            events.send("Enter a document name")
            return@launch
        }
        val from = DateKeys.parseOrNull(current.validFrom)
        val until = DateKeys.parseOrNull(current.validUntil)
        if (from == null || until == null) {
            form.update { it.copy(showErrors = true) }
            events.send("Choose valid start and expiry dates")
            return@launch
        }
        if (until < from) {
            form.update { it.copy(showErrors = true) }
            events.send("Expiry date cannot be before the start date")
            return@launch
        }
        val existing = current.id?.let { id -> uiState.value.records.firstOrNull { it.id == id } }
        repository.upsert(
            KeyDateRecord(
                id = current.id ?: "rem-${System.currentTimeMillis()}",
                title = current.title.trim(),
                category = KeyDateCategories.normalize(current.category),
                validFrom = DateKeys.of(from),
                validUntil = DateKeys.of(until),
                note = current.note.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        form.value = KeyDateFormState()
        events.send(if (current.id != null) "Key date updated" else "Key date saved")
    }

    fun delete(record: KeyDateRecord) = viewModelScope.launch {
        repository.delete(record.id)
        events.send("Key date removed")
    }
}

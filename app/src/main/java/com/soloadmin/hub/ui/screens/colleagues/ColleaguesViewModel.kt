package com.soloadmin.hub.ui.screens.colleagues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.files.MediaStore
import com.soloadmin.hub.data.repository.ColleagueRepository
import com.soloadmin.hub.domain.model.ColleagueRecord
import com.soloadmin.hub.domain.model.ColleagueTheme
import com.soloadmin.hub.domain.model.InfoField
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ColleagueFormState(
    val id: String? = null,
    val name: String = "",
    val role: String = "",
    val theme: ColleagueTheme = ColleagueTheme.SKY,
    val photoPath: String? = null,
    val coverPath: String? = null,
    val info: List<InfoField> = listOf(InfoField("Mobile", ""), InfoField("ID", "")),
    val showErrors: Boolean = false
) {
    val nameError: Boolean get() = showErrors && name.isBlank()
}

data class ColleaguesUiState(
    val loading: Boolean = true,
    val all: List<ColleagueRecord> = emptyList(),
    val query: String = "",
    val form: ColleagueFormState = ColleagueFormState()
) {
    val visible: List<ColleagueRecord>
        get() = if (query.isBlank()) all else all.filter { it.matches(query) }
}

class ColleaguesViewModel(
    private val repository: ColleagueRepository,
    private val mediaStore: MediaStore
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val form = MutableStateFlow(ColleagueFormState())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<ColleaguesUiState> = combine(
        repository.observeAll(),
        query,
        form
    ) { colleagues, text, formState ->
        ColleaguesUiState(loading = false, all = colleagues, query = text, form = formState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColleaguesUiState())

    fun setQuery(value: String) { query.value = value }

    fun startAdd() { form.value = ColleagueFormState() }

    fun startEdit(record: ColleagueRecord) {
        form.value = ColleagueFormState(
            id = record.id,
            name = record.name,
            role = record.role,
            theme = record.theme,
            photoPath = record.photoPath,
            coverPath = record.coverPath,
            info = record.info.ifEmpty { listOf(InfoField("Mobile", "")) }
        )
    }

    fun resetForm() { form.value = ColleagueFormState() }

    fun updateForm(block: (ColleagueFormState) -> ColleagueFormState) = form.update(block)

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
        val name = mediaStore.importImage(uri, "colleague")
        if (name == null) events.send("Could not read that photo") else {
            form.update { it.copy(photoPath = name) }
        }
    }

    fun setCover(uri: android.net.Uri) = viewModelScope.launch {
        val name = mediaStore.importImage(uri, "cover")
        if (name == null) events.send("Could not read that photo") else {
            form.update { it.copy(coverPath = name) }
        }
    }

    fun clearPhoto() = form.update { it.copy(photoPath = null) }
    fun clearCover() = form.update { it.copy(coverPath = null) }

    fun save() = viewModelScope.launch {
        val current = form.value
        if (current.name.isBlank()) {
            form.update { it.copy(showErrors = true) }
            events.send("Enter the colleague's name")
            return@launch
        }
        val existing = current.id?.let { id -> uiState.value.all.firstOrNull { it.id == id } }
        val cleanInfo = current.info
            .filter { it.label.isNotBlank() || it.value.isNotBlank() }
            .map { InfoField(it.label.trim().ifEmpty { "Info" }, it.value.trim()) }
            .take(12)
        repository.save(
            ColleagueRecord(
                id = current.id ?: "col-${System.currentTimeMillis()}",
                name = current.name.trim(),
                role = current.role.trim(),
                theme = current.theme,
                photoPath = current.photoPath,
                coverPath = current.coverPath,
                info = cleanInfo,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                sortOrder = existing?.sortOrder ?: uiState.value.all.size
            )
        )
        form.value = ColleagueFormState()
        events.send(if (current.id != null) "Colleague updated" else "Colleague added")
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.delete(id)
        form.value = ColleagueFormState()
        events.send("Colleague removed")
    }
}

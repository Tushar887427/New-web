package com.soloadmin.hub.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.repository.TaskRepository
import com.soloadmin.hub.domain.model.DailyTaskRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter(val label: String) { PENDING("Pending"), COMPLETED("Completed"), ALL("All") }

data class TasksUiState(
    val loading: Boolean = true,
    val pending: List<DailyTaskRecord> = emptyList(),
    val completed: List<DailyTaskRecord> = emptyList(),
    val filter: TaskFilter = TaskFilter.PENDING,
    val query: String = ""
) {
    val visible: List<DailyTaskRecord>
        get() {
            val base = when (filter) {
                TaskFilter.PENDING -> pending
                TaskFilter.COMPLETED -> completed
                TaskFilter.ALL -> pending + completed
            }
            val q = query.trim().lowercase()
            return if (q.isEmpty()) base else base.filter { it.text.lowercase().contains(q) }
        }
}

class TasksViewModel(private val repository: TaskRepository) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.PENDING)
    private val query = MutableStateFlow("")
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<TasksUiState> = combine(
        repository.observeAll(),
        filter,
        query
    ) { tasks, activeFilter, text ->
        TasksUiState(
            loading = false,
            pending = tasks.filter { !it.done },
            completed = tasks.filter { it.done },
            filter = activeFilter,
            query = text
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }

    fun add(text: String) = viewModelScope.launch {
        val clean = text.trim()
        if (clean.isEmpty()) return@launch
        if (clean.length > 300) {
            events.send("Keep task text under 300 characters")
            return@launch
        }
        repository.add(
            DailyTaskRecord(
                id = "tsk-${System.currentTimeMillis()}",
                text = clean,
                createdAt = System.currentTimeMillis(),
                sortOrder = uiState.value.pending.size
            )
        )
    }

    fun toggle(task: DailyTaskRecord) = viewModelScope.launch {
        repository.setDone(task.id, !task.done)
    }

    fun update(task: DailyTaskRecord, text: String) = viewModelScope.launch {
        val clean = text.trim()
        if (clean.isEmpty()) {
            events.send("Task text cannot be empty")
            return@launch
        }
        repository.update(task.copy(text = clean))
        events.send("Task updated")
    }

    fun delete(task: DailyTaskRecord) = viewModelScope.launch {
        repository.delete(task.id)
        events.send("Task deleted")
    }
}

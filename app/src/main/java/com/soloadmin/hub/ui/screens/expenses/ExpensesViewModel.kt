package com.soloadmin.hub.ui.screens.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.repository.ExpenseRepository
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.model.ExpenseCategories
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ExpenseRecord
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

enum class ExpenseFilter(val label: String) {
    ALL("All"), HEADMASTER("Headmaster"), PERSONAL("Personal")
}

data class ExpenseFormState(
    val id: String? = null,
    val owner: ExpenseOwner = ExpenseOwner.HEADMASTER,
    val title: String = "",
    val amountText: String = "",
    val dateKey: String = DateKeys.of(LocalDate.now()),
    val category: String = ExpenseCategories.default,
    val note: String = "",
    val showErrors: Boolean = false
) {
    val isEditing: Boolean get() = id != null
    val amount: Money? get() = Money.parseRupees(amountText)
    val titleError: Boolean get() = showErrors && title.isBlank()
    val amountError: Boolean get() = showErrors && (amount == null || amount!!.paise <= 0)
}

data class ExpensesUiState(
    val loading: Boolean = true,
    val all: List<ExpenseRecord> = emptyList(),
    val filter: ExpenseFilter = ExpenseFilter.ALL,
    val form: ExpenseFormState = ExpenseFormState(),
    val headmasterDue: Money = Money.ZERO,
    val headmasterRecovered: Money = Money.ZERO,
    val personalSpent: Money = Money.ZERO,
    val categoryTotals: List<Pair<String, Money>> = emptyList()
) {
    val visible: List<ExpenseRecord>
        get() = when (filter) {
            ExpenseFilter.ALL -> all
            ExpenseFilter.HEADMASTER -> all.filter { it.owner == ExpenseOwner.HEADMASTER }
            ExpenseFilter.PERSONAL -> all.filter { it.owner == ExpenseOwner.PERSONAL }
        }

    val totalCount: Int get() = all.size
}

class ExpensesViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val filter = MutableStateFlow(ExpenseFilter.ALL)
    private val form = MutableStateFlow(ExpenseFormState())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<ExpensesUiState> = combine(
        repository.observeAll(),
        filter,
        form
    ) { expenses, activeFilter, formState ->
        val figures = expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
        ExpensesUiState(
            loading = false,
            all = expenses,
            filter = activeFilter,
            form = formState,
            headmasterDue = FinancialCalculator.headmasterDue(figures),
            headmasterRecovered = FinancialCalculator.headmasterRecovered(figures),
            personalSpent = FinancialCalculator.personalSpent(figures),
            categoryTotals = FinancialCalculator.categoryTotals(figures)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    fun setFilter(value: ExpenseFilter) { filter.value = value }

    fun startAdd(owner: ExpenseOwner = ExpenseOwner.HEADMASTER) {
        form.value = ExpenseFormState(owner = owner)
    }

    fun startEdit(record: ExpenseRecord) {
        form.value = ExpenseFormState(
            id = record.id,
            owner = record.owner,
            title = record.title,
            amountText = if (record.amount.paise % 100L == 0L) (record.amount.paise / 100).toString()
            else String.format("%.2f", record.amount.paise / 100.0),
            dateKey = record.dateKey,
            category = record.category,
            note = record.note
        )
    }

    fun resetForm() { form.value = ExpenseFormState() }

    fun updateForm(block: (ExpenseFormState) -> ExpenseFormState) = form.update(block)

    fun save() = viewModelScope.launch {
        val current = form.value
        val amount = current.amount
        if (current.title.isBlank() || amount == null || amount.paise <= 0) {
            form.update { it.copy(showErrors = true) }
            events.send("Enter a title and an amount above zero")
            return@launch
        }
        if (DateKeys.parseOrNull(current.dateKey) == null) {
            form.update { it.copy(showErrors = true) }
            events.send("Choose a valid date")
            return@launch
        }
        val existing = current.id?.let { id -> uiState.value.all.firstOrNull { it.id == id } }
        val record = ExpenseRecord(
            id = current.id ?: "exp-${System.currentTimeMillis()}",
            dateKey = current.dateKey,
            title = current.title.trim(),
            category = ExpenseCategories.normalize(current.category),
            amount = amount,
            owner = current.owner,
            recovered = existing?.recovered ?: false,
            note = current.note.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        repository.update(record)
        form.value = ExpenseFormState()
        events.send(if (current.isEditing) "Expense updated" else "Expense added")
    }

    fun toggleRecovered(record: ExpenseRecord) = viewModelScope.launch {
        repository.setRecovered(record.id, !record.recovered)
        events.send(if (record.recovered) "Marked as due" else "Marked as recovered")
    }

    fun delete(record: ExpenseRecord) = viewModelScope.launch {
        repository.delete(record.id)
        events.send("Expense deleted")
    }
}

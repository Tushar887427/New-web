package com.soloadmin.hub.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.repository.ExpenseRepository
import com.soloadmin.hub.data.repository.FinanceRepository
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinanceFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.logic.formatInrCompact
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.FinanceStatus
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
import java.time.YearMonth

data class MonthlyTrendPoint(val month: YearMonth, val label: String, val received: Money, val personal: Money)

data class FinanceFormState(
    val id: String? = null,
    val title: String = "",
    val amountText: String = "",
    val dateKey: String = DateKeys.of(LocalDate.now()),
    val status: FinanceStatus = FinanceStatus.RECEIVED,
    val showErrors: Boolean = false
) {
    val amount: Money? get() = Money.parseRupees(amountText)
    val titleError: Boolean get() = showErrors && title.isBlank()
    val amountError: Boolean get() = showErrors && (amount == null || amount!!.paise <= 0)
}

data class FinanceUiState(
    val loading: Boolean = true,
    val records: List<FinanceRecord> = emptyList(),
    val form: FinanceFormState = FinanceFormState(),
    val totalReceived: Money = Money.ZERO,
    val totalPending: Money = Money.ZERO,
    val personalSpent: Money = Money.ZERO,
    val netBalance: Money = Money.ZERO,
    val headmasterDue: Money = Money.ZERO,
    val trend: List<MonthlyTrendPoint> = emptyList(),
    val receivedThisMonth: Money = Money.ZERO,
    val spendByCategory: List<Pair<String, Money>> = emptyList()
)

class FinanceViewModel(
    private val financeRepository: FinanceRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val form = MutableStateFlow(FinanceFormState())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<FinanceUiState> = combine(
        financeRepository.observeAll(),
        expenseRepository.observeAll(),
        form
    ) { finances, expenses, formState ->
        val expenseFigures = expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
        val financeFigures = finances.map { FinanceFigures(it.amount, it.status == FinanceStatus.RECEIVED, it.dateKey) }
        val today = LocalDate.now()
        val trend = (5 downTo 0).map { offset ->
            val month = YearMonth.from(today).minusMonths(offset.toLong())
            val prefix = "%04d-%02d".format(month.year, month.monthValue)
            val received = finances.filter { it.dateKey.startsWith(prefix) && it.status == FinanceStatus.RECEIVED }
                .fold(Money.ZERO) { acc, item -> acc + item.amount }
            val personal = expenses.filter { it.dateKey.startsWith(prefix) && it.owner == com.soloadmin.hub.domain.model.ExpenseOwner.PERSONAL }
                .fold(Money.ZERO) { acc, item -> acc + item.amount }
            MonthlyTrendPoint(month, DateKeys.monthShort(month.monthValue), received, personal)
        }
        val currentPrefix = "%04d-%02d".format(today.year, today.monthValue)
        FinanceUiState(
            loading = false,
            records = finances,
            form = formState,
            totalReceived = FinancialCalculator.totalReceived(financeFigures),
            totalPending = FinancialCalculator.totalPending(financeFigures),
            personalSpent = FinancialCalculator.personalSpent(expenseFigures),
            netBalance = FinancialCalculator.netBalance(financeFigures, expenseFigures),
            headmasterDue = FinancialCalculator.headmasterDue(expenseFigures),
            trend = trend,
            receivedThisMonth = finances.filter { it.dateKey.startsWith(currentPrefix) && it.status == FinanceStatus.RECEIVED }
                .fold(Money.ZERO) { acc, item -> acc + item.amount },
            spendByCategory = FinancialCalculator.categoryTotals(expenseFigures)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    fun startAdd(status: FinanceStatus = FinanceStatus.RECEIVED) {
        form.value = FinanceFormState(status = status)
    }

    fun startEdit(record: FinanceRecord) {
        form.value = FinanceFormState(
            id = record.id,
            title = record.title,
            amountText = if (record.amount.paise % 100L == 0L) (record.amount.paise / 100).toString()
            else String.format("%.2f", record.amount.paise / 100.0),
            dateKey = record.dateKey,
            status = record.status
        )
    }

    fun resetForm() { form.value = FinanceFormState() }

    fun updateForm(block: (FinanceFormState) -> FinanceFormState) = form.update(block)

    fun save() = viewModelScope.launch {
        val current = form.value
        val amount = current.amount
        if (current.title.isBlank() || amount == null || amount.paise <= 0) {
            form.update { it.copy(showErrors = true) }
            events.send("Enter a description and an amount above zero")
            return@launch
        }
        if (DateKeys.parseOrNull(current.dateKey) == null) {
            form.update { it.copy(showErrors = true) }
            events.send("Choose a valid date")
            return@launch
        }
        val existing = current.id?.let { id -> uiState.value.records.firstOrNull { it.id == id } }
        financeRepository.update(
            FinanceRecord(
                id = current.id ?: "fin-${System.currentTimeMillis()}",
                dateKey = current.dateKey,
                title = current.title.trim(),
                amount = amount,
                status = current.status,
                legacyType = existing?.legacyType ?: if (current.status == FinanceStatus.PENDING) "advance" else "income",
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        form.value = FinanceFormState()
        events.send(if (current.id != null) "Pay record updated" else "Pay record saved")
    }

    fun toggleStatus(record: FinanceRecord) = viewModelScope.launch {
        val next = if (record.status == FinanceStatus.RECEIVED) FinanceStatus.PENDING else FinanceStatus.RECEIVED
        financeRepository.setStatus(record.id, next)
        events.send(if (next == FinanceStatus.RECEIVED) "Marked as received" else "Marked as pending")
    }

    fun delete(record: FinanceRecord) = viewModelScope.launch {
        financeRepository.delete(record.id)
        events.send("Entry deleted")
    }

    /** Shared axis helper so the chart and the labels never disagree. */
    fun trendAxis(points: List<MonthlyTrendPoint>): List<String> =
        points.map { formatInrCompact(it.received) }

    fun monthSummary(points: List<MonthlyTrendPoint>): String {
        val latest = points.lastOrNull() ?: return "No records yet"
        return "${latest.label}: ${formatInrCompact(latest.received)} in, ${formatInrCompact(latest.personal)} spent"
    }

    fun durationLabel(minutes: Int): String = AttendanceCalculator.formatDuration(minutes)
}

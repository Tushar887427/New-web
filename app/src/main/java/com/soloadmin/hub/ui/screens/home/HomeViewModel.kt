package com.soloadmin.hub.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.preferences.SettingsRepository
import com.soloadmin.hub.data.repository.AttendanceRepository
import com.soloadmin.hub.data.repository.ExpenseRepository
import com.soloadmin.hub.data.repository.FinanceRepository
import com.soloadmin.hub.data.repository.KeyDateRepository
import com.soloadmin.hub.data.repository.OfficialDutyRepository
import com.soloadmin.hub.data.repository.ProfileRepository
import com.soloadmin.hub.data.repository.TaskRepository
import com.soloadmin.hub.data.repository.WorkSessionRepository
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.AttendanceCounters
import com.soloadmin.hub.domain.logic.DashboardMath
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinanceFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.DailyTaskRecord
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import kotlinx.coroutines.flow.receiveAsFlow
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.domain.model.ProfileRecord
import com.soloadmin.hub.domain.model.PunchEngine
import com.soloadmin.hub.domain.model.PunchOutcome
import com.soloadmin.hub.domain.model.WorkSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Everything the home dashboard renders, computed once per change. */
data class HomeUiState(
    val loading: Boolean = true,
    val profile: ProfileRecord = ProfileRecord(),
    val today: LocalDate = LocalDate.now(),
    val session: WorkSession = WorkSession.empty(DateKeys.of(LocalDate.now())),
    val todayRecord: AttendanceRecord? = null,
    val month: YearMonth = YearMonth.now(),
    val counters: AttendanceCounters = AttendanceCounters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    val headmasterDue: Money = Money.ZERO,
    val pendingTasks: Int = 0,
    val expiringKeyDates: List<KeyDateRecord> = emptyList(),
    val monthReceived: Money = Money.ZERO,
    val monthPersonal: Money = Money.ZERO,
    val monthPending: Money = Money.ZERO,
    val recentDuties: Int = 0,
    val dutyHoursThisMonth: Int = 0
) {
    val hasKeyDateAlert: Boolean get() = expiringKeyDates.isNotEmpty()
    val weeklyOffLabel: String get() = profile.weeklyOffLabel
    val isWeeklyOffToday: Boolean
        get() = DashboardMath.isWeeklyOff(today, profile.weeklyOffDay)

    val monthNet: Money get() = DashboardMath.monthNet(monthReceived, monthPersonal)
}

/** Toast/snackbar events are one-shot, so they travel through a channel rather than state. */
sealed interface HomeEvent {
    data class Message(val text: String, val success: Boolean = true) : HomeEvent
    data class PunchChanged(val punchedIn: Boolean) : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val attendanceRepository: AttendanceRepository,
    private val expenseRepository: ExpenseRepository,
    private val financeRepository: FinanceRepository,
    private val keyDateRepository: KeyDateRepository,
    private val taskRepository: TaskRepository,
    private val dutyRepository: OfficialDutyRepository,
    private val sessionRepository: WorkSessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val todayKey = MutableStateFlow(DateKeys.of(LocalDate.now()))
    private val events = kotlinx.coroutines.channels.Channel<HomeEvent>(capacity = kotlinx.coroutines.channels.Channel.BUFFERED)
    val eventFlow = kotlinx.coroutines.flow.receiveAsFlow(events)

    val uiState: StateFlow<HomeUiState> = todayKey.flatMapLatest { key ->
        val month = YearMonth.from(LocalDate.parse(key))
        combine(
            listOf(
            profileRepository.observe(),
            sessionRepository.observe(key),
            attendanceRepository.observeDate(key),
            attendanceRepository.observeMonth(DateKeys.monthKey(LocalDate.parse(key))),
            expenseRepository.observeAll(),
            financeRepository.observeAll(),
            keyDateRepository.observeAll(),
            taskRepository.observeAll(),
            dutyRepository.observeAll()
            )
        ) { values ->
            val profile = values[0] as ProfileRecord
            val session = values[1] as WorkSession
            val todayRecord = values[2] as AttendanceRecord?
            val monthRecords = values[3] as List<AttendanceRecord>
            @Suppress("UNCHECKED_CAST")
            val expenses = values[4] as List<ExpenseRecord>
            @Suppress("UNCHECKED_CAST")
            val finances = values[5] as List<FinanceRecord>
            @Suppress("UNCHECKED_CAST")
            val keyDates = values[6] as List<KeyDateRecord>
            val taskList = values[7] as List<DailyTaskRecord>
            val duties = values[8] as List<OfficialDutyRecord>

            val localDate = LocalDate.parse(key)
            val counters = AttendanceCalculator.counters(
                monthRecords.map { it.dateKey to it.status },
                monthRecords.filter { it.status.isLeave }.map { it.dateKey to it.leaveType }
            )
            val expenseFigures = expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
            val financeFigures = finances.map { FinanceFigures(it.amount, it.status.storageKey == "received", it.dateKey) }
            val monthFinances = finances.filter { it.dateKey.startsWith(DateKeys.monthKey(localDate)) }

            HomeUiState(
                loading = false,
                profile = profile,
                today = localDate,
                session = if (session.dateKey == key) session else WorkSession.empty(key),
                todayRecord = todayRecord,
                month = month,
                counters = counters,
                headmasterDue = FinancialCalculator.headmasterDue(expenseFigures),
                pendingTasks = taskList.count { !it.done },
                expiringKeyDates = keyDates.filter { DashboardMath.needsAttention(it.daysUntilExpiry(localDate)) }
                    .sortedBy { it.validUntil },
                monthReceived = FinancialCalculator.totalReceived(
                    monthFinances.map { FinanceFigures(it.amount, it.status.storageKey == "received", it.dateKey) }
                ),
                monthPersonal = FinancialCalculator.personalSpent(
                    expenses.filter { it.dateKey.startsWith(DateKeys.monthKey(localDate)) }
                        .map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
                ),
                monthPending = FinancialCalculator.totalPending(financeFigures),
                recentDuties = duties.count { it.dateKey.startsWith(DateKeys.monthKey(localDate)) },
                dutyHoursThisMonth = duties.filter { it.dateKey.startsWith(DateKeys.monthKey(localDate)) }
                    .sumOf { it.durationMinutes }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Call when the app returns to the foreground so a stale "today" is corrected. */
    fun refreshToday() {
        todayKey.value = DateKeys.of(LocalDate.now())
    }

    fun punch() = viewModelScope.launch {
        val key = todayKey.value
        val state = uiState.value
        val session = state.session.takeIf { it.dateKey == key } ?: sessionRepository.get(key)
        val record = attendanceRepository.get(key)
        val now = LocalTime.now()
        val nowMillis = System.currentTimeMillis()

        if (session.punchedIn) {
            // Ending the day also closes any running break, like the legacy toggle did.
            val (closed, _) = if (session.onBreak) PunchEngine.endBreak(session, nowMillis) else session to null
            val (updated, outcome) = PunchEngine.punchOut(closed, record, now, nowMillis)
            val isLate = DateKeys.minutesOfDay(updated.inTime)?.let { inMin ->
                DateKeys.minutesOfDay(state.profile.shiftStart)?.let { shift -> inMin > shift } ?: false
            } ?: false
            sessionRepository.save(updated)
            attendanceRepository.saveSession(updated, record, isLate)
            val text = (outcome as? PunchOutcome.PunchedOut)?.let {
                "Punched out at ${DateKeys.formatTime12h(it.time)}"
            } ?: "Punched out"
            events.send(HomeEvent.Message(text))
            events.send(HomeEvent.PunchChanged(false))
        } else {
            val (updated, outcome) = PunchEngine.punchIn(session, record, now, state.profile.shiftStart)
            val late = (outcome as? PunchOutcome.PunchedIn)?.late ?: false
            sessionRepository.save(updated)
            attendanceRepository.saveSession(updated, record, late)
            val text = "Punched in at ${DateKeys.formatTime12h(updated.inTime.orEmpty())}" +
                if (late) " · marked late" else ""
            events.send(HomeEvent.Message(text))
            events.send(HomeEvent.PunchChanged(true))
        }
    }

    fun toggleBreak() = viewModelScope.launch {
        val key = todayKey.value
        val session = sessionRepository.get(key)
        val nowMillis = System.currentTimeMillis()
        val (updated, outcome) = PunchEngine.toggleBreak(session, nowMillis)
        when (outcome) {
            is PunchOutcome.BreakRequiresPunchIn ->
                events.send(HomeEvent.Message("Punch in first to record a break", success = false))
            is PunchOutcome.BreakEnded -> {
                sessionRepository.save(updated)
                val record = attendanceRepository.get(key)
                attendanceRepository.saveSession(updated.copy(outTime = record?.outTime), record, false)
                events.send(HomeEvent.Message("Break ended · +${outcome.addedMinutes}m (total ${outcome.totalMinutes}m)"))
            }
            is PunchOutcome.BreakStarted -> {
                sessionRepository.save(updated)
                events.send(HomeEvent.Message("Break started"))
            }
            else -> Unit
        }
    }

    /** Used by the "Log time" quick action when a missed punch is saved. */
    fun recordManualTime(dateKey: String, inTime: String, outTime: String, note: String, shiftStart: String) =
        viewModelScope.launch {
            val late = DateKeys.minutesOfDay(inTime)?.let { inMin ->
                DateKeys.minutesOfDay(shiftStart)?.let { shift -> inMin > shift } ?: false
            } ?: false
            val record = AttendanceRecord(
                dateKey = dateKey,
                status = if (late) AttendanceStatus.LATE else AttendanceStatus.PRESENT,
                inTime = inTime,
                outTime = outTime,
                note = note
            )
            attendanceRepository.upsert(record)
            if (dateKey == todayKey.value) {
                sessionRepository.save(
                    WorkSession(
                        dateKey = dateKey,
                        inTime = inTime,
                        outTime = outTime,
                        breakMinutes = sessionRepository.get(dateKey).breakMinutes
                    )
                )
            }
            events.send(HomeEvent.Message("Office time saved"))
            refreshToday()
        }

    fun markTaskDone(id: String) = viewModelScope.launch {
        taskRepository.setDone(id, true)
        events.send(HomeEvent.Message("Task completed"))
    }

    fun updateSettings(block: suspend SettingsRepository.() -> Unit) = viewModelScope.launch {
        settingsRepository.block()
    }
}

package com.soloadmin.hub.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soloadmin.hub.data.repository.AttendanceRepository
import com.soloadmin.hub.data.repository.OfficialDutyRepository
import com.soloadmin.hub.data.repository.ProfileRepository
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.AttendanceCounters
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.LeaveTypes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class AttendanceUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val records: Map<String, AttendanceRecord> = emptyMap(),
    val weeklyOffDay: Int = 0,
    val shiftStart: String = "09:30",
    val today: LocalDate = LocalDate.now(),
    val counters: AttendanceCounters = AttendanceCounters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    val recordedDays: Int = 0,
    val totalWorkedMinutes: Int = 0,
    val averageStartMinutes: Int? = null
) {
    fun recordFor(date: LocalDate): AttendanceRecord? = records[DateKeys.of(date)]
}

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val profileRepository: ProfileRepository,
    private val dutyRepository: OfficialDutyRepository
) : ViewModel() {

    private val monthFlow = MutableStateFlow(YearMonth.now())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    private val today = LocalDate.now()

    val uiState: StateFlow<AttendanceUiState> = monthFlow.flatMapLatest { month ->
        combine(
            attendanceRepository.observeMonth(DateKeys.monthKey(month.atDay(1))),
            profileRepository.observe(),
            dutyRepository.observeAll()
        ) { records, profile, duties ->
            val byDate = records.associateBy { it.dateKey }
            val counters = AttendanceCalculator.counters(
                records.map { it.dateKey to it.status },
                records.filter { it.status.isLeave }.map { it.dateKey to it.leaveType }
            )
            val worked = records.mapNotNull { record ->
                AttendanceCalculator.workedMinutes(record.inTime, record.outTime, record.breakMinutes)
            }
            val starts = records.mapNotNull { DateKeys.minutesOfDay(it.inTime) }
            AttendanceUiState(
                loading = false,
                month = month,
                records = byDate,
                weeklyOffDay = profile.weeklyOffDay,
                shiftStart = profile.shiftStart,
                today = today,
                counters = counters,
                recordedDays = records.size,
                totalWorkedMinutes = worked.sum(),
                averageStartMinutes = if (starts.isEmpty()) null else starts.sum() / starts.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceUiState())

    fun changeMonth(delta: Long) {
        monthFlow.value = monthFlow.value.plusMonths(delta)
    }

    fun goToMonth(month: YearMonth) {
        monthFlow.value = month
    }

    fun saveDay(
        dateKey: String,
        status: AttendanceStatus,
        leaveType: String?,
        inTime: String?,
        outTime: String?,
        note: String
    ) = viewModelScope.launch {
        if (!inTime.isNullOrBlank() && !outTime.isNullOrBlank()) {
            val start = DateKeys.minutesOfDay(inTime)
            val end = DateKeys.minutesOfDay(outTime)
            if (start != null && end != null && end <= start) {
                events.send("Out time must be after in time")
                return@launch
            }
        }
        val existing = attendanceRepository.get(dateKey)
        attendanceRepository.upsert(
            AttendanceRecord(
                dateKey = dateKey,
                status = status,
                leaveType = if (status == AttendanceStatus.LEAVE) LeaveTypes.normalize(leaveType) else null,
                inTime = inTime?.takeIf { it.isNotBlank() },
                outTime = outTime?.takeIf { it.isNotBlank() },
                note = note.trim(),
                officialDutyId = existing?.officialDutyId,
                breakMinutes = existing?.breakMinutes ?: 0
            )
        )
        events.send("Attendance updated")
    }

    fun clearDay(dateKey: String) = viewModelScope.launch {
        attendanceRepository.delete(dateKey)
        events.send("Day cleared")
    }

    /** Bulk leave entry for a date range (legacy "Log Leave" modal). */
    fun saveLeaveRange(leaveType: String, fromDate: String, toDate: String, note: String) =
        viewModelScope.launch {
            val start = DateKeys.parseOrNull(fromDate)
            val end = DateKeys.parseOrNull(toDate)
            if (start == null || end == null) {
                events.send("Choose valid dates")
                return@launch
            }
            if (end < start) {
                events.send("End date cannot be before start date")
                return@launch
            }
            if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 120) {
                events.send("Please log at most 120 days at once")
                return@launch
            }
            val type = LeaveTypes.normalize(leaveType)
            val records = generateSequence(start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(end) }
                .map { date ->
                    val key = DateKeys.of(date)
                    val existing = attendanceRepository.get(key)
                    AttendanceRecord(
                        dateKey = key,
                        status = AttendanceStatus.LEAVE,
                        leaveType = type,
                        inTime = null,
                        outTime = null,
                        note = note.trim(),
                        officialDutyId = existing?.officialDutyId
                    )
                }.toList()
            attendanceRepository.upsertAll(records)
            events.send("$type saved (${records.size} day${if (records.size == 1) "" else "s"})")
        }

    fun saveManualPunch(dateKey: String, inTime: String, outTime: String, note: String, shiftStart: String) =
        viewModelScope.launch {
            val start = DateKeys.minutesOfDay(inTime)
            val end = DateKeys.minutesOfDay(outTime)
            if (start == null || end == null) {
                events.send("Enter valid in and out times")
                return@launch
            }
            if (end <= start) {
                events.send("Out time must be after in time")
                return@launch
            }
            val shift = DateKeys.minutesOfDay(shiftStart) ?: 570
            val existing = attendanceRepository.get(dateKey)
            attendanceRepository.upsert(
                AttendanceRecord(
                    dateKey = dateKey,
                    status = if (existing?.status?.isLeave == true || existing?.status?.isOfficialDuty == true)
                        existing.status else if (start > shift) AttendanceStatus.LATE else AttendanceStatus.PRESENT,
                    leaveType = existing?.leaveType,
                    inTime = inTime,
                    outTime = outTime,
                    note = note.trim(),
                    officialDutyId = existing?.officialDutyId,
                    breakMinutes = existing?.breakMinutes ?: 0
                )
            )
            events.send("Office time updated")
        }
}

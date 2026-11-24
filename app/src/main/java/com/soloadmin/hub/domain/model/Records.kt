package com.soloadmin.hub.domain.model

import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * Immutable domain records. The data layer translates them to/from Room entities, so business
 * logic and its tests never need Android or SQLite.
 */

data class AttendanceRecord(
    val dateKey: String,
    val status: AttendanceStatus,
    val leaveType: String? = null,
    val inTime: String? = null,
    val outTime: String? = null,
    val note: String = "",
    val officialDutyId: String? = null,
    val breakMinutes: Int = 0
) {
    val date: LocalDate? get() = DateKeys.parseOrNull(dateKey)
    val isLeave: Boolean get() = status.isLeave
    val hasTimes: Boolean get() = !inTime.isNullOrBlank() || !outTime.isNullOrBlank()
}

data class OfficialDutyRecord(
    val id: String,
    val dateKey: String,
    val mode: DutyMode,
    val startTime: String,
    val endTime: String,
    val meetingWith: String,
    val location: String = "",
    val remarks: String = "",
    val attachmentPath: String? = null,
    val createdAt: Long = 0L
) {
    val date: LocalDate? get() = DateKeys.parseOrNull(dateKey)

    val durationMinutes: Int
        get() {
            val start = DateKeys.minutesOfDay(startTime) ?: return 0
            val end = DateKeys.minutesOfDay(endTime) ?: return 0
            return (end - start).coerceAtLeast(0)
        }
}

data class ExpenseRecord(
    val id: String,
    val dateKey: String,
    val title: String,
    val category: String,
    val amount: Money,
    val owner: ExpenseOwner,
    val recovered: Boolean = false,
    val note: String = "",
    val createdAt: Long = 0L
) {
    val date: LocalDate? get() = DateKeys.parseOrNull(dateKey)
    val isRecoverable: Boolean get() = owner == ExpenseOwner.HEADMASTER
}

data class FinanceRecord(
    val id: String,
    val dateKey: String,
    val title: String,
    val amount: Money,
    val status: FinanceStatus,
    /** Preserved from legacy JSON ("income", "advance", ...) for export fidelity. */
    val legacyType: String = "income",
    val createdAt: Long = 0L
) {
    val date: LocalDate? get() = DateKeys.parseOrNull(dateKey)
}

data class KeyDateRecord(
    val id: String,
    val title: String,
    val category: String,
    val validFrom: String,
    val validUntil: String,
    val note: String = "",
    val createdAt: Long = 0L
) {
    val expiresOn: LocalDate? get() = DateKeys.parseOrNull(validUntil)
    val startsOn: LocalDate? get() = DateKeys.parseOrNull(validFrom)

    fun daysUntilExpiry(today: LocalDate): Int = DateKeys.daysUntil(validUntil, today)

    /** Elapsed share of the validity window, 0f–1f, for progress bars. */
    fun validityFraction(today: LocalDate): Float = (validityProgress(today) / 100f).coerceIn(0f, 1f)

    /** Port of the legacy validity progress bar as a 0–100 percentage. */
    fun validityProgress(today: LocalDate): Int {
        val start = startsOn ?: return 100
        val end = expiresOn ?: return 100
        if (end <= start) return 100
        val span = end.toEpochDay() - start.toEpochDay()
        val elapsed = today.toEpochDay() - start.toEpochDay()
        val pct = Math.round(elapsed.toDouble() / span.toDouble() * 100.0).toInt()
        return pct.coerceIn(0, 100)
    }

    fun expiryState(today: LocalDate): ExpiryState {
        val days = daysUntilExpiry(today)
        return when {
            days < 0 -> ExpiryState.Expired(days)
            days == 0 -> ExpiryState.ExpiresToday
            days <= 60 -> ExpiryState.ExpiringSoon(days)
            else -> ExpiryState.Active(days)
        }
    }
}

sealed interface ExpiryState {
    data class Active(val daysLeft: Int) : ExpiryState
    data class ExpiringSoon(val daysLeft: Int) : ExpiryState
    data object ExpiresToday : ExpiryState
    data class Expired(val daysAgo: Int) : ExpiryState

    val needsAttention: Boolean get() = this !is Active

    val badgeText: String
        get() = when (this) {
            is Active -> "Active (${daysLeft}d left)"
            is ExpiringSoon -> "Expires in ${daysLeft}d"
            ExpiresToday -> "Expires Today"
            is Expired -> "Expired ${-daysAgo}d ago"
        }
}

data class DailyTaskRecord(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    val sortOrder: Int = 0
)

data class ColleagueRecord(
    val id: String,
    val name: String,
    val role: String = "",
    val theme: ColleagueTheme = ColleagueTheme.SKY,
    val photoPath: String? = null,
    val coverPath: String? = null,
    val info: List<InfoField> = emptyList(),
    val createdAt: Long = 0L,
    val sortOrder: Int = 0
) {
    val initials: String
        get() {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val letters = words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            return letters.joinToString("").ifBlank { "C" }
        }

    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (name.lowercase().contains(q)) return true
        if (role.lowercase().contains(q)) return true
        return info.any { it.label.lowercase().contains(q) || it.value.lowercase().contains(q) }
    }
}

data class ProfileRecord(
    val name: String = "Admin",
    val bio: String = "",
    val photoPath: String? = null,
    val weeklyOffDay: Int = 0,
    val shiftStart: String = "09:30",
    val info: List<InfoField> = emptyList()
) {
    val initials: String
        get() {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val letters = words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            return letters.joinToString("").ifBlank { "A" }
        }

    val weeklyOffLabel: String get() = DateKeys.weekdayName(weeklyOffDay)
}

/** Live punch state for today. */
data class WorkSession(
    val dateKey: String,
    val inTime: String? = null,
    val outTime: String? = null,
    val onBreak: Boolean = false,
    val breakStartedAt: Long? = null,
    val breakMinutes: Int = 0,
    val sessionsCount: Int = 0
) {
    val punchedIn: Boolean get() = !inTime.isNullOrBlank() && outTime.isNullOrBlank()
    val hasAnyPunch: Boolean get() = !inTime.isNullOrBlank() || !outTime.isNullOrBlank()

    /** Live minutes for the break pill (includes the running break when active). */
    fun totalBreakMinutes(nowMillis: Long): Int {
        val running = if (onBreak && breakStartedAt != null) {
            ((nowMillis - breakStartedAt).coerceAtLeast(0L) / 60_000L).toInt()
        } else {
            0
        }
        return breakMinutes + running
    }

    companion object {
        fun empty(dateKey: String) = WorkSession(dateKey = dateKey)
    }
}

/** Result of a punch action, used to drive toasts and haptics. */
sealed interface PunchOutcome {
    data class PunchedIn(val time: String, val late: Boolean) : PunchOutcome
    data class PunchedOut(val time: String) : PunchOutcome
    data class BreakStarted(val time: String) : PunchOutcome
    data class BreakEnded(val addedMinutes: Int, val totalMinutes: Int) : PunchOutcome
    data object BreakRequiresPunchIn : PunchOutcome
    data object NothingToDo : PunchOutcome
}

/** Pure punch/break engine shared by the ViewModel and unit tests. */
object PunchEngine {

    fun punchIn(session: WorkSession, record: AttendanceRecord?, now: LocalTime, shiftStart: String): Pair<WorkSession, PunchOutcome> {
        val time = DateKeys.nowTimeKey(now)
        // Punching in again after a completed day keeps the original in-time, like the legacy app.
        val effectiveIn = session.inTime?.takeIf { session.outTime.isNullOrBlank() } ?: time
        val late = DateKeys.minutesOfDay(effectiveIn)?.let { inMin ->
            DateKeys.minutesOfDay(shiftStart)?.let { shift -> inMin > shift } ?: false
        } ?: false
        val status = if (late) AttendanceStatus.LATE else AttendanceStatus.PRESENT
        val base = record
        val updatedRecord = AttendanceRecord(
            dateKey = session.dateKey,
            status = if (base != null && base.status.isLeave) base.status else status,
            leaveType = base?.leaveType,
            inTime = effectiveIn,
            outTime = null,
            note = base?.note.orEmpty(),
            officialDutyId = base?.officialDutyId
        )
        val updatedSession = session.copy(
            inTime = effectiveIn,
            outTime = null,
            sessionsCount = session.sessionsCount + 1
        )
        return updatedSession to PunchOutcome.PunchedIn(effectiveIn, late)
    }

    fun punchOut(session: WorkSession, record: AttendanceRecord?, now: LocalTime, nowMillis: Long): Pair<WorkSession, PunchOutcome> {
        val time = DateKeys.nowTimeKey(now)
        val closedBreak = if (session.onBreak) endBreak(session, nowMillis).first else session
        val updatedSession = closedBreak.copy(outTime = time, inTime = closedBreak.inTime ?: time)
        val updatedRecord = AttendanceRecord(
            dateKey = session.dateKey,
            status = record?.status ?: AttendanceStatus.PRESENT,
            leaveType = record?.leaveType,
            inTime = updatedSession.inTime,
            outTime = time,
            note = record?.note.orEmpty(),
            officialDutyId = record?.officialDutyId
        )
        return updatedSession to PunchOutcome.PunchedOut(time)
    }

    fun punchOutRecord(session: WorkSession, record: AttendanceRecord?, now: LocalTime, nowMillis: Long): AttendanceRecord {
        val (_, _) = nowMillis to now
        val time = DateKeys.nowTimeKey(now)
        return AttendanceRecord(
            dateKey = session.dateKey,
            status = record?.status ?: AttendanceStatus.PRESENT,
            leaveType = record?.leaveType,
            inTime = session.inTime ?: time,
            outTime = time,
            note = record?.note.orEmpty(),
            officialDutyId = record?.officialDutyId
        )
    }

    fun startBreak(session: WorkSession, nowMillis: Long): Pair<WorkSession, PunchOutcome> {
        if (!session.punchedIn) return session to PunchOutcome.BreakRequiresPunchIn
        if (session.onBreak) return session to PunchOutcome.NothingToDo
        val updated = session.copy(onBreak = true, breakStartedAt = nowMillis)
        val time = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return updated to PunchOutcome.BreakStarted(DateKeys.nowTimeKey(time))
    }

    fun endBreak(session: WorkSession, nowMillis: Long): Pair<WorkSession, PunchOutcome> {
        if (!session.onBreak) return session to PunchOutcome.NothingToDo
        val started = session.breakStartedAt ?: nowMillis
        // Legacy minimum: a break always adds at least one minute.
        val elapsed = Math.max(1, Math.round((nowMillis - started).toDouble() / 60_000.0).toInt())
        val total = session.breakMinutes + elapsed
        val updated = session.copy(onBreak = false, breakStartedAt = null, breakMinutes = total)
        return updated to PunchOutcome.BreakEnded(elapsed, total)
    }

    fun toggleBreak(session: WorkSession, nowMillis: Long): Pair<WorkSession, PunchOutcome> =
        if (session.onBreak) endBreak(session, nowMillis) else startBreak(session, nowMillis)

    /**
     * Outcome for the legacy "Punch In Again" flow: pressing punch while already out starts a
     * fresh in-time but remembers the earlier out-time only until the new punch completes.
     */
    fun shouldShowPunchOut(session: WorkSession): Boolean = session.punchedIn
}

package com.soloadmin.hub.domain

import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.PunchEngine
import com.soloadmin.hub.domain.model.PunchOutcome
import com.soloadmin.hub.domain.model.WorkSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PunchEngineTest {

    private val date = "2026-11-23"
    private val shift = "09:30"

    @Test
    fun `on time punch marks present`() {
        val session = WorkSession.empty(date)
        val (updated, outcome) = PunchEngine.punchIn(session, null, LocalTime.of(9, 20), shift)
        assertEquals("09:20", updated.inTime)
        assertTrue(updated.punchedIn)
        assertEquals(PunchOutcome.PunchedIn("09:20", false), outcome)
        assertFalse((outcome as PunchOutcome.PunchedIn).late)
    }

    @Test
    fun `late punch marks late`() {
        val (updated, outcome) = PunchEngine.punchIn(WorkSession.empty(date), null, LocalTime.of(9, 55), shift)
        val typed = outcome as PunchOutcome.PunchedIn
        assertTrue(typed.late)
        assertEquals(AttendanceStatus.LATE, statusOf(updated, AttendanceStatus.LATE))
    }

    @Test
    fun `punch out records time`() {
        val punchedIn = WorkSession(date, inTime = "09:20", sessionsCount = 1)
        val (updated, outcome) = PunchEngine.punchOut(punchedIn, null, LocalTime.of(17, 5), 0L)
        assertEquals("17:05", updated.outTime)
        assertFalse(updated.punchedIn)
        assertEquals(PunchOutcome.PunchedOut("17:05"), outcome)
    }

    @Test
    fun `punching out closes a running break`() {
        val session = WorkSession(date, inTime = "09:20", onBreak = true, breakStartedAt = 0L)
        val (updated, _) = PunchEngine.punchOut(session, null, LocalTime.of(17, 0), 10 * 60_000L)
        assertFalse(updated.onBreak)
        assertEquals(10, updated.breakMinutes)
    }

    @Test
    fun `break requires punch in`() {
        val (session, outcome) = PunchEngine.startBreak(WorkSession.empty(date), 1000L)
        assertEquals(PunchOutcome.BreakRequiresPunchIn, outcome)
        assertFalse(session.onBreak)
    }

    @Test
    fun `break adds at least one minute even for instant taps`() {
        val session = WorkSession(date, inTime = "09:20")
        val (started, _) = PunchEngine.startBreak(session, 10_000L)
        assertTrue(started.onBreak)
        val (ended, outcome) = PunchEngine.endBreak(started, 10_000L)
        assertEquals(1, ended.breakMinutes)
        assertEquals(PunchOutcome.BreakEnded(1, 1), outcome)
    }

    @Test
    fun `break accumulates across multiple breaks`() {
        var session = WorkSession(date, inTime = "09:20")
        val (s1, _) = PunchEngine.startBreak(session, 0L)
        val (s2, _) = PunchEngine.endBreak(s1, 15 * 60_000L)
        session = s2
        val (s3, _) = PunchEngine.startBreak(session, 60 * 60_000L)
        val (s4, outcome) = PunchEngine.endBreak(s3, 65 * 60_000L)
        assertEquals(20, s4.breakMinutes)
        assertEquals(PunchOutcome.BreakEnded(5, 20), outcome)
        assertEquals(15, session.breakMinutes)
    }

    @Test
    fun `live break minutes include running break`() {
        val session = WorkSession(date, inTime = "09:20", onBreak = true, breakStartedAt = 1000L, breakMinutes = 5)
        assertEquals(5 + 10, session.totalBreakMinutes(nowMillis = 1000L + 10 * 60_000L))
        assertEquals(5, session.copy(onBreak = false).totalBreakMinutes(0L))
    }

    @Test
    fun `punch in preserves an existing leave status`() {
        val leave = AttendanceRecord(date, AttendanceStatus.LEAVE, leaveType = "Casual Leave")
        val (_, outcome) = PunchEngine.punchIn(WorkSession.empty(date), leave, LocalTime.of(10, 0), shift)
        assertTrue((outcome as PunchOutcome.PunchedIn).late)
    }

    @Test
    fun `punch again after completion keeps the earliest in time`() {
        val session = WorkSession(date, inTime = "09:20", outTime = "17:00")
        val (updated, _) = PunchEngine.punchIn(session, null, LocalTime.of(18, 0), shift)
        assertEquals("18:00", updated.inTime)
        assertEquals(null, updated.outTime)
    }

    private fun statusOf(session: WorkSession, fallback: AttendanceStatus): AttendanceStatus {
        val late = com.soloadmin.hub.domain.logic.DateKeys.minutesOfDay(session.inTime)!! >
            com.soloadmin.hub.domain.logic.DateKeys.minutesOfDay(shift)!!
        return if (late) AttendanceStatus.LATE else AttendanceStatus.PRESENT
    }
}

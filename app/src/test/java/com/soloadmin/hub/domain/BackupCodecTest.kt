package com.soloadmin.hub.domain

import com.soloadmin.hub.data.backup.BackupCodec
import com.soloadmin.hub.data.backup.BackupCodec.DecodeException
import com.soloadmin.hub.domain.logic.BackupMapper
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinanceFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ImportOptions
import com.soloadmin.hub.domain.model.NumOrText
import com.soloadmin.hub.domain.model.ProfileRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** End-to-end checks against a byte-for-byte copy of the legacy localStorage payload. */
class BackupCodecTest {

    private val today = LocalDate.of(2026, 11, 23)

    private fun legacyJson(): String {
        // Under Gradle the fixture is a test resource; when running the sources directly from a
        // directory (how this project's logic suite is also verified) it is read from disk.
        val stream = javaClass.classLoader!!.getResourceAsStream("legacy_backup_sample.json")
        if (stream != null) return stream.bufferedReader().use { it.readText() }
        val file = java.io.File("legacy_backup_sample.json")
        assertTrue("legacy fixture missing", file.exists())
        return file.readText()
    }

    private fun emptySnapshot() = BackupSnapshot(
        ProfileRecord(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
    )

    @Test
    fun `decodes every legacy group`() {
        val decoded = BackupCodec.decode(legacyJson())
        assertFalse(decoded.isNativeFormat)
        assertEquals(8, decoded.attendance.size)
        assertEquals(1, decoded.duties.size)
        assertEquals(3, decoded.expenses.size)
        assertEquals(2, decoded.finances.size)
        assertEquals(3, decoded.keyDates.size)
        assertEquals(2, decoded.tasks.size)
        assertEquals(2, decoded.colleagues.size)
        assertEquals("Admin", decoded.profile?.name)
        assertEquals(4, decoded.profile?.info?.size)
    }

    @Test
    fun `legacy numbers survive as rupees`() {
        val decoded = BackupCodec.decode(legacyJson())
        val mapped = BackupMapper.toDomain(decoded, emptySnapshot(), ImportOptions())
        assertEquals("\u20B91,450", formatInr(mapped.expenses.first { it.id == "exp-1" }.amount))
        assertEquals("\u20B945,000", formatInr(mapped.finances.first { it.id == "fin-1" }.amount))
    }

    @Test
    fun `legacy money rules reproduce the original screen totals`() {
        val decoded = BackupCodec.decode(legacyJson())
        val mapped = BackupMapper.toDomain(decoded, emptySnapshot(), ImportOptions())
        val expenses = mapped.expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
        val finances = mapped.finances.map { FinanceFigures(it.amount, it.status.storageKey == "received", it.dateKey) }
        // Legacy: HM due = 1450 (exp-2 recovered), personal = 180, received = 45000.
        assertEquals("\u20B91,450", formatInr(FinancialCalculator.headmasterDue(expenses)))
        assertEquals("\u20B9320", formatInr(FinancialCalculator.headmasterRecovered(expenses)))
        assertEquals("\u20B9180", formatInr(FinancialCalculator.personalSpent(expenses)))
        assertEquals("\u20B944,820", formatInr(FinancialCalculator.netBalance(finances, expenses)))
        assertEquals("\u20B95,000", formatInr(FinancialCalculator.totalPending(finances)))
    }

    @Test
    fun `attendance statuses map to the shared enum`() {
        val decoded = BackupCodec.decode(legacyJson())
        val mapped = decoded.attendance.associate { it.dateKey to it.status }
        assertEquals("Late", mapped["2026-11-03"])
        assertEquals("Early_Going", mapped["2026-11-10"])
        assertEquals("Outstation", mapped["2026-11-06"])
        assertEquals(AttendanceStatus.LEAVE, AttendanceStatus.fromStorage(decoded.attendance.first { it.dateKey == "2026-11-04" }.status))
        assertEquals("Casual Leave", decoded.attendance.first { it.dateKey == "2026-11-04" }.leaveType)
    }

    @Test
    fun `round trip through the native writer is lossless`() {
        val decoded = BackupCodec.decode(legacyJson())
        val mapped = BackupMapper.toDomain(decoded, emptySnapshot(), ImportOptions())
        val profile = BackupMapper.mapProfile(decoded)!!
        val snapshot = BackupSnapshot(
            profile = profile,
            attendance = decoded.attendance.map { row ->
                com.soloadmin.hub.domain.model.AttendanceRecord(
                    dateKey = row.dateKey,
                    status = AttendanceStatus.fromStorage(row.status)!!,
                    leaveType = row.leaveType?.takeIf { it.isNotBlank() },
                    inTime = row.inTime?.takeIf { it.isNotBlank() },
                    outTime = row.outTime?.takeIf { it.isNotBlank() },
                    note = row.note.orEmpty()
                )
            },
            duties = mapped.duties,
            expenses = mapped.expenses,
            finances = mapped.finances,
            keyDates = mapped.keyDates,
            tasks = mapped.tasks,
            colleagues = mapped.colleagues,
            workSessions = emptyList()
        )
        val written = BackupCodec.encode(snapshot, today)
        val reread = BackupCodec.decode(written)
        assertTrue(reread.isNativeFormat)
        assertEquals(8, reread.attendance.size)
        assertEquals(3, reread.expenses.size)
        assertEquals(2, reread.finances.size)
        assertEquals(3, reread.keyDates.size)
        val remapped = BackupMapper.toDomain(reread, emptySnapshot(), ImportOptions())
        assertEquals(
            snapshot.expenses.map { it.amount.paise }.sorted(),
            remapped.expenses.map { it.amount.paise }.sorted()
        )
        assertEquals(snapshot.keyDates.map { it.validUntil }.sorted(), remapped.keyDates.map { it.validUntil }.sorted())
        assertEquals("Fire Safety NOC", decoded.keyDates.first { it.id == "rem-2" }.title)
    }

    @Test
    fun `writer escapes quotes and newlines`() {
        val snapshot = emptySnapshot().copy(
            profile = ProfileRecord(name = "A \"quoted\" name", bio = "line1\nline2"),
            keyDates = listOf(
                com.soloadmin.hub.domain.model.KeyDateRecord("rem-1", "Title \"X\"", "Document", "2026-01-01", "2026-12-31", "note\nwith break")
            )
        )
        val text = BackupCodec.encode(snapshot, today)
        val reread = BackupCodec.decode(text)
        assertEquals("A \"quoted\" name", reread.profile?.name)
        assertEquals("line1\nline2", reread.profile?.bio)
        assertEquals("note\nwith break", reread.keyDates.first().note)
    }

    @Test
    fun `garbage is rejected with a clear error`() {
        assertThrowsDecode("this is not json")
    }

    @Test
    fun `empty file is rejected`() {
        assertThrowsDecode("   ")
    }

    @Test
    fun `unrelated json is rejected`() {
        assertThrowsDecode("""{"hello":"world","items":[1,2,3]}""")
    }

    @Test
    fun `truncated json is rejected`() {
        assertThrowsDecode("""{"expenses":[{"id":"e1",""")
    }

    private fun assertThrowsDecode(text: String) {
        try {
            BackupCodec.decode(text)
        } catch (_: DecodeException) {
            return
        }
        throw AssertionError("expected DecodeException for input: $text")
    }

    @Test
    fun `bom and utf8 whitespace are tolerated`() {
        val text = "\uFEFF\n" + legacyJson()
        val decoded = BackupCodec.decode(text)
        assertEquals(8, decoded.attendance.size)
    }

    @Test
    fun `partial dumps import what they contain`() {
        val onlyTasks = """{"tasks":[{"id":"t1","text":"Only task","done":false}]}"""
        val decoded = BackupCodec.decode(onlyTasks)
        assertEquals(1, decoded.tasks.size)
        assertEquals(0, decoded.expenses.size)
        assertNull(decoded.profile)
    }

    @Test
    fun `unicode escapes decode`() {
        val json = """{"tasks":[{"id":"t1","text":"Renew \u20B9 500 licence","done":true}]}"""
        val decoded = BackupCodec.decode(json)
        assertEquals("Renew \u20B9 500 licence", decoded.tasks.first().text)
        assertEquals(true, decoded.tasks.first().done)
    }

    @Test
    fun `json numbers and numeric strings both parse`() {
        val json = """{"expenses":[
            {"id":"e1","date":"2026-11-01","title":"Numeric","category":"Office","amount":1234.5,"owner":"headmaster","recovered":false},
            {"id":"e2","date":"2026-11-02","title":"String","category":"Travel","amount":"99","owner":"personal","recovered":true}
        ]}"""
        val decoded = BackupCodec.decode(json)
        assertEquals(NumOrText.Number(1234.5), decoded.expenses[0].amount)
        assertEquals(NumOrText.Text("99"), decoded.expenses[1].amount)
        val mapped = BackupMapper.toDomain(decoded, emptySnapshot(), ImportOptions())
        assertEquals("\u20B91,234.50", formatInr(mapped.expenses.first { it.id == "e1" }.amount))
        assertEquals("\u20B999", formatInr(mapped.expenses.first { it.id == "e2" }.amount))
        assertEquals(ExpenseOwner.PERSONAL, mapped.expenses.first { it.id == "e2" }.owner)
    }

    @Test
    fun `excel style csv quotes commas`() {
        val csv = BackupCodec.csv("Lunch, tea & snacks")
        assertEquals("\"Lunch, tea & snacks\"", csv)
        assertEquals("\"Say \"\"hi\"\"\"", BackupCodec.csv("Say \"hi\""))
        assertEquals("plain", BackupCodec.csv("plain"))
    }

    private fun assertFalse(value: Boolean) {
        if (value) throw AssertionError("expected false")
    }
}

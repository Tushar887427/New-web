package com.soloadmin.hub.data.backup

import com.soloadmin.hub.domain.logic.BackupMapper
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.DecodedBackup
import com.soloadmin.hub.domain.model.ImportOptions
import com.soloadmin.hub.domain.model.ImportedAttendance
import com.soloadmin.hub.domain.model.ImportedColleague
import com.soloadmin.hub.domain.model.ImportedDuty
import com.soloadmin.hub.domain.model.ImportedExpense
import com.soloadmin.hub.domain.model.ImportedFinance
import com.soloadmin.hub.domain.model.ImportedInfo
import com.soloadmin.hub.domain.model.ImportedKeyDate
import com.soloadmin.hub.domain.model.ImportedProfile
import com.soloadmin.hub.domain.model.ImportedTask
import com.soloadmin.hub.domain.model.ImportedWorkSession
import com.soloadmin.hub.domain.model.NumOrText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonElement.JsonArray
import kotlinx.serialization.json.JsonElement.JsonObject
import kotlinx.serialization.json.JsonElement.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

/**
 * Reads and writes both backup dialects with hand-written JSON traversal.
 *
 * Traversal (instead of a strict `@Serializable` decode) is intentional: partial or slightly
 * malformed localStorage dumps are common, and one odd field must not reject the whole file.
 */
object BackupCodec {

    // ------------------------------------------------------------------ writing

    fun encode(snapshot: BackupSnapshot, exportedAt: LocalDate = LocalDate.now()): String {
        val root = buildJsonObject {
            put("format", BackupPayload.FORMAT_NATIVE)
            put("version", BackupPayload.NATIVE_VERSION)
            put("exportedAt", exportedAt.toString())
            put("appVersion", "1.0.0")

            put("profile", buildJsonObject {
                put("name", snapshot.profile.name)
                put("bio", snapshot.profile.bio)
                put("photo", snapshot.profile.photoPath ?: "")
                put("weeklyOff", snapshot.profile.weeklyOffDay)
                put("shiftStart", snapshot.profile.shiftStart)
                put("info", infoArray(snapshot.profile.info.map { it.label to it.value }))
            })

            put("attendance", buildJsonObject {
                snapshot.attendance.sortedBy { it.dateKey }.forEach { record ->
                    put(record.dateKey, buildJsonObject {
                        put("status", record.status.storageKey)
                        if (!record.leaveType.isNullOrBlank()) put("leaveType", record.leaveType)
                        put("inTime", record.inTime ?: "")
                        put("outTime", record.outTime ?: "")
                        put("note", record.note)
                    })
                }
            })

            put("outOnWorkLogs", buildJsonArray {
                snapshot.duties.sortedByDescending { it.dateKey }.forEach { duty ->
                    add(buildJsonObject {
                        put("id", duty.id)
                        put("date", duty.dateKey)
                        put("mode", duty.mode.label)
                        put("startTime", duty.startTime)
                        put("endTime", duty.endTime)
                        put("meetingWith", duty.meetingWith)
                        put("location", duty.location)
                        put("remarks", duty.remarks)
                        put("attachment", duty.attachmentPath ?: "")
                    })
                }
            })

            put("expenses", buildJsonArray {
                snapshot.expenses.sortedByDescending { it.dateKey }.forEach { expense ->
                    add(buildJsonObject {
                        put("id", expense.id)
                        put("date", expense.dateKey)
                        put("title", expense.title)
                        put("category", expense.category)
                        put("amount", expense.amount.paise / 100.0)
                        put("owner", expense.owner.storageKey)
                        put("recovered", expense.recovered)
                        put("note", expense.note)
                    })
                }
            })

            put("finances", buildJsonArray {
                snapshot.finances.sortedByDescending { it.dateKey }.forEach { finance ->
                    add(buildJsonObject {
                        put("id", finance.id)
                        put("date", finance.dateKey)
                        put("title", finance.title)
                        put("type", finance.legacyType)
                        put("amount", finance.amount.paise / 100.0)
                        put("status", finance.status.storageKey)
                    })
                }
            })

            put("reminders", buildJsonArray {
                snapshot.keyDates.sortedBy { it.validUntil }.forEach { keyDate ->
                    add(buildJsonObject {
                        put("id", keyDate.id)
                        put("title", keyDate.title)
                        put("category", keyDate.category)
                        put("validFrom", keyDate.validFrom)
                        put("validUntil", keyDate.validUntil)
                        put("note", keyDate.note)
                    })
                }
            })

            put("tasks", buildJsonArray {
                snapshot.tasks.sortedBy { it.sortOrder }.forEach { task ->
                    add(buildJsonObject {
                        put("id", task.id)
                        put("text", task.text)
                        put("done", task.done)
                    })
                }
            })

            put("colleagues", buildJsonArray {
                snapshot.colleagues.sortedBy { it.sortOrder }.forEach { colleague ->
                    add(buildJsonObject {
                        put("id", colleague.id)
                        put("name", colleague.name)
                        put("role", colleague.role)
                        put("theme", colleague.theme.key)
                        put("photo", colleague.photoPath ?: "")
                        put("bgPhoto", colleague.coverPath ?: "")
                        put("info", infoArray(colleague.info.map { it.label to it.value }))
                    })
                }
            })

            put("workSessions", buildJsonArray {
                snapshot.workSessions.sortedBy { it.dateKey }.forEach { session ->
                    add(buildJsonObject {
                        put("dateKey", session.dateKey)
                        put("inTime", session.inTime ?: "")
                        put("outTime", session.outTime ?: "")
                        put("onBreak", session.onBreak)
                        put("breakStartedAt", session.breakStartedAt)
                        put("breakMinutes", session.breakMinutes)
                        put("sessionsCount", session.sessionsCount)
                    })
                }
            })
        }
        return writeJson(root)
    }

    /** Compact CSV exports used by the share/report flows. */
    fun encodeAttendanceCsv(records: List<AttendanceRecord>): String {
        val header = "Date,Day,Status,LeaveType,In,Out,BreakMinutes,Note"
        val rows = records.sortedBy { it.dateKey }.map { record ->
            listOf(
                record.dateKey,
                record.date?.let { DateKeys.dayShort(it) } ?: "",
                record.status.label,
                record.leaveType ?: "",
                record.inTime ?: "",
                record.outTime ?: "",
                record.breakMinutes.toString(),
                record.note
            ).joinToString(",") { csv(it) }
        }
        return (listOf(header) + rows).joinToString("\r\n")
    }

    fun csv(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun infoArray(items: List<Pair<String, String>>): JsonArray = buildJsonArray {
        items.forEach { (label, value) ->
            add(buildJsonObject {
                put("label", label)
                put("value", value)
            })
        }
    }

    // ------------------------------------------------------------------ reading

    class DecodeException(message: String) : Exception(message)

    fun decode(raw: String): DecodedBackup {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) throw DecodeException("The selected file is empty.")
        val root = try {
            readJson(text)
        } catch (error: Exception) {
            throw DecodeException("This file is not valid JSON. Choose the backup exported from the app.")
        }
        val obj = root as? JsonObject ?: throw DecodeException("The backup file must contain a JSON object.")
        if (!looksLikeBackup(obj)) {
            throw DecodeException(
                "This JSON does not look like a Solo Admin backup. Expected keys such as \"attendance\" or \"profile\"."
            )
        }
        return fromObject(obj)
    }

    private fun looksLikeBackup(obj: JsonObject): Boolean = listOf(
        "attendance", "outOnWorkLogs", "expenses", "finances", "reminders", "tasks",
        "colleagues", "profile", "workSessions", "format"
    ).any { obj.containsKey(it) }

    fun fromObject(obj: JsonObject): DecodedBackup {
        val profile = obj["profile"].asObjectOrNull()?.let { p ->
            ImportedProfile(
                name = p.string("name"),
                bio = p.string("bio"),
                photoDataUrl = p.string("photo"),
                weeklyOff = p.number("weeklyOff"),
                shiftStart = p.string("shiftStart"),
                info = p.infoList("info")
            )
        }

        val attendance = obj["attendance"].asObjectOrNull()?.entries?.mapNotNull { (dateKey, element) ->
            val record = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedAttendance(
                dateKey = dateKey,
                status = record.string("status"),
                leaveType = record.string("leaveType"),
                inTime = record.string("inTime"),
                outTime = record.string("outTime"),
                note = record.string("note")
            )
        }.orEmpty()

        val duties = obj["outOnWorkLogs"].asArrayOrNull()?.mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedDuty(
                id = o.string("id"),
                dateKey = o.string("date"),
                mode = o.string("mode"),
                startTime = o.string("startTime"),
                endTime = o.string("endTime"),
                meetingWith = o.string("meetingWith"),
                location = o.string("location"),
                remarks = o.string("remarks"),
                attachment = o.string("attachment")
            )
        }.orEmpty()

        val expenses = obj["expenses"].asArrayOrNull()?.mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedExpense(
                id = o.string("id"),
                dateKey = o.string("date"),
                title = o.string("title"),
                category = o.string("category"),
                amount = o.number("amount"),
                owner = o.string("owner"),
                recovered = o.boolean("recovered"),
                note = o.string("note")
            )
        }.orEmpty()

        val finances = obj["finances"].asArrayOrNull()?.mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedFinance(
                id = o.string("id"),
                dateKey = o.string("date"),
                title = o.string("title"),
                type = o.string("type"),
                amount = o.number("amount"),
                status = o.string("status")
            )
        }.orEmpty()

        val reminders = obj["reminders"].asArrayOrNull()?.mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedKeyDate(
                id = o.string("id"),
                title = o.string("title"),
                category = o.string("category"),
                validFrom = o.string("validFrom"),
                validUntil = o.string("validUntil"),
                note = o.string("note")
            )
        }.orEmpty()

        val tasks = obj["tasks"].asArrayOrNull()?.mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedTask(id = o.string("id"), text = o.string("text"), done = o.boolean("done"))
        }.orEmpty()

        val colleagues = obj["colleagues"].asArrayOrNull()?.mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedColleague(
                id = o.string("id"),
                name = o.string("name"),
                role = o.string("role"),
                theme = o.string("theme"),
                photoDataUrl = o.string("photo"),
                coverDataUrl = o.string("bgPhoto"),
                info = o.infoList("info")
            )
        }.orEmpty()

        val sessions = obj["workSessions"].asArrayOrNull()?.mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            val dateKey = o.string("dateKey") ?: return@mapNotNull null
            ImportedWorkSession(
                dateKey = dateKey,
                inTime = o.string("inTime"),
                outTime = o.string("outTime"),
                onBreak = o.boolean("onBreak") ?: false,
                breakStartedAt = o.long("breakStartedAt"),
                breakMinutes = o.number("breakMinutes"),
                sessionsCount = o.number("sessionsCount")
            )
        }.orEmpty()

        return DecodedBackup(
            isNativeFormat = obj.string("format") == BackupPayload.FORMAT_NATIVE,
            sourceVersion = obj["version"]?.primitiveInt(),
            profile = profile,
            attendance = attendance,
            duties = duties,
            expenses = expenses,
            finances = finances,
            keyDates = reminders,
            tasks = tasks,
            colleagues = colleagues,
            workSessions = sessions
        )
    }

    // ------------------------------------------------------------- JSON helpers

    private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonObject.string(key: String): String? {
        val value = this[key] ?: return null
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.number(key: String): NumOrText {
        val value = this[key] ?: return NumOrText.Absent
        val primitive = value as? JsonPrimitive ?: return NumOrText.Absent
        // A quoted number keeps its original string shape: the legacy app wrote some amounts as
        // text, and the mapper decides how to interpret them.
        if (primitive.isString) {
            val content = primitive.contentOrNull?.trim().orEmpty()
            if (content.isEmpty() || content == "null") return NumOrText.Absent
            return NumOrText.Text(content)
        }
        primitive.doubleOrNull?.let { return NumOrText.Number(it) }
        val content = primitive.contentOrNull?.trim().orEmpty()
        return if (content.isEmpty()) NumOrText.Absent else NumOrText.Text(content)
    }

    private fun JsonObject.long(key: String): Long? {
        val value = this[key] as? JsonPrimitive ?: return null
        value.doubleOrNull?.let { return it.toLong() }
        return value.contentOrNull?.toLongOrNull()
    }

    private fun JsonElement.primitiveInt(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { return it }
        return primitive.contentOrNull?.toIntOrNull()
    }

    // ------------------------------------------------------- JSON reader/writer

    /** Minimal, dependency-sized JSON reader. Throws [DecodeException] on malformed input. */
    fun readJson(text: String): JsonElement {
        val parser = Reader(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw DecodeException("Unexpected trailing content in the JSON file.")
        return value
    }

    /** Pretty writer producing stable, diff-friendly output. */
    fun writeJson(element: JsonElement): String = buildString { Writer(this).write(element, 0) }

    private class Reader(private val text: String) {
        private var pos = 0
        fun atEnd(): Boolean = pos >= text.length
        fun skipWhitespace() { while (pos < text.length && text[pos].isWhitespace()) pos++ }

        fun readValue(): JsonElement {
            skipWhitespace()
            if (atEnd()) throw DecodeException("The JSON file ends unexpectedly.")
            return when (val c = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonPrimitive(readString())
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> {
                    if (c == '-' || c.isDigit()) readNumber()
                    else throw DecodeException("Unexpected character '$c' in the JSON file.")
                }
            }
        }

        private fun readObject(): JsonObject {
            expect('{')
            val map = LinkedHashMap<String, JsonElement>()
            skipWhitespace()
            if (peek() == '}') { pos++; return JsonObject(map) }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                map[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> { pos++; continue }
                    '}' -> { pos++; return JsonObject(map) }
                    else -> throw DecodeException("Expected ',' or '}' in the JSON object.")
                }
            }
        }

        private fun readArray(): JsonArray {
            expect('[')
            val list = mutableListOf<JsonElement>()
            skipWhitespace()
            if (peek() == ']') { pos++; return JsonArray(list) }
            while (true) {
                list.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> { pos++; continue }
                    ']' -> { pos++; return JsonArray(list) }
                    else -> throw DecodeException("Expected ',' or ']' in the JSON array.")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw DecodeException("Unterminated string in the JSON file.")
                val c = text[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw DecodeException("Unterminated escape sequence.")
                        when (val esc = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw DecodeException("Invalid unicode escape.")
                                val hex = text.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw DecodeException("Invalid unicode escape."))
                            }
                            else -> throw DecodeException("Invalid escape '\\$esc'.")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): JsonPrimitive {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && (text[pos].isDigit() || text[pos] == '.' || text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')) pos++
            val slice = text.substring(start, pos)
            slice.toDoubleOrNull() ?: throw DecodeException("Invalid number '$slice' in the JSON file.")
            return JsonPrimitive(slice, isString = false)
        }

        private fun readBoolean(): JsonPrimitive {
            return when {
                text.startsWith("true", pos) -> { pos += 4; JsonPrimitive("true", isString = false) }
                text.startsWith("false", pos) -> { pos += 5; JsonPrimitive("false", isString = false) }
                else -> throw DecodeException("Invalid literal in the JSON file.")
            }
        }

        private fun readNull(): JsonPrimitive {
            if (!text.startsWith("null", pos)) throw DecodeException("Invalid literal in the JSON file.")
            pos += 4
            return JsonPrimitive("", isString = true)
        }

        private fun peek(): Char? = if (atEnd()) null else text[pos]
        private fun expect(c: Char) {
            skipWhitespace()
            if (atEnd() || text[pos] != c) throw DecodeException("Expected '$c' in the JSON file.")
            pos++
        }
    }

    private class Writer(private val out: StringBuilder) {
        fun write(element: JsonElement, indent: Int) {
            when (element) {
                is JsonObject -> writeObject(element, indent)
                is JsonArray -> writeArray(element, indent)
                is JsonPrimitive -> out.append(escape(element))
            }
        }

        private fun writeObject(obj: JsonObject, indent: Int) {
            if (obj.map.isEmpty()) { out.append("{}"); return }
            out.append("{\n")
            val keys = obj.map.keys.toList()
            keys.forEachIndexed { index, key ->
                pad(indent + 1)
                out.append(quote(key)).append(": ")
                write(obj.map.getValue(key), indent + 1)
                if (index != keys.lastIndex) out.append(',')
                out.append('\n')
            }
            pad(indent)
            out.append('}')
        }

        private fun writeArray(array: JsonArray, indent: Int) {
            if (array.list.isEmpty()) { out.append("[]"); return }
            out.append("[\n")
            array.list.forEachIndexed { index, element ->
                pad(indent + 1)
                write(element, indent + 1)
                if (index != array.list.lastIndex) out.append(',')
                out.append('\n')
            }
            pad(indent)
            out.append(']')
        }

        private fun pad(level: Int) { repeat(level) { out.append("  ") } }

        private fun escape(primitive: JsonPrimitive): String {
            if (!primitive.isString) {
                val raw = primitive.raw
                return if (raw.isEmpty()) "null" else raw
            }
            return quote(primitive.raw)
        }

        private fun quote(value: String): String {
            val sb = StringBuilder(value.length + 2)
            sb.append('"')
            value.forEach { c ->
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
                }
            }
            sb.append('"')
            return sb.toString()
        }
    }

    private fun JsonObject.infoList(key: String): List<ImportedInfo> =
        (this[key].asArrayOrNull()?.list ?: emptyList()).mapNotNull { element ->
            val o = element.asObjectOrNull() ?: return@mapNotNull null
            ImportedInfo(o.string("label"), o.string("value"))
        }
}

/** Convenience for callers that only care whether a file is importable. */
fun previewOf(decoded: DecodedBackup, snapshot: BackupSnapshot, options: ImportOptions) =
    BackupMapper.preview(decoded, snapshot, options, LocalDate.now())

fun AttendanceStatus.displayLabel(): String = label

fun amountLabel(paise: Long): String = formatInr(paise)

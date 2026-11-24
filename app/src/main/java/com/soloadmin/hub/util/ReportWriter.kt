package com.soloadmin.hub.util

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.soloadmin.hub.data.backup.BackupCodec
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinanceFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.FinanceStatus
import java.time.LocalDate

/**
 * Builds the shareable documents: a monthly summary PDF and CSV exports.
 *
 * The PDF is produced with Android's platform [PdfDocument] (A4 at 72dpi = 595x842pt), which needs
 * no third-party dependency and prints crisply.
 */
object ReportWriter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f

    data class Report(val fileName: String, val mimeType: String)

    fun monthlySummaryPdf(snapshot: BackupSnapshot, month: String, today: LocalDate = LocalDate.now()): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        val bold = Paint().apply { isAntiAlias = true; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val small = Paint().apply { isAntiAlias = true; textSize = 9f; color = 0xFF6B7280.toInt() }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN + 10f

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN + 10f
        }

        fun ensure(space: Float) {
            if (y + space > PAGE_HEIGHT - MARGIN - 24f) newPage()
        }

        fun text(value: String, size: Float = 10.5f, isBold: Boolean = false, color: Int = 0xFF111827.toInt(), indent: Float = 0f) {
            paint.textSize = size
            paint.color = color
            paint.typeface = if (isBold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            val maxWidth = PAGE_WIDTH - MARGIN * 2 - indent
            val lines = wrap(value, paint, maxWidth)
            ensure(lines.size * (size + 4f) + 4f)
            lines.forEach { line ->
                canvas.drawText(line, MARGIN + indent, y, paint)
                y += size + 4f
            }
        }

        fun divider() {
            ensure(12f)
            paint.color = 0xFFE5E7EB.toInt()
            paint.strokeWidth = 1f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
            y += 10f
        }

        fun kvRow(left: String, right: String, boldRight: Boolean = false) {
            ensure(16f)
            paint.textSize = 10.5f
            paint.typeface = Typeface.SANS_SERIF
            paint.color = 0xFF374151.toInt()
            canvas.drawText(left, MARGIN, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = 0xFF111827.toInt()
            paint.typeface = if (boldRight) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            canvas.drawText(right, PAGE_WIDTH - MARGIN, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 16f
        }

        // Header band
        paint.color = 0xFF0B5D46.toInt()
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, paint)

        text("Solo Admin — Monthly Summary", 18f, isBold = true)
        var subtitle = DateKeys.formatMonthYearComma(java.time.YearMonth.parse(month))
        text("${snapshot.profile.name.ifBlank { "Admin" }} · $subtitle · generated ${DateKeys.formatPretty(today)}", 10f, color = 0xFF6B7280.toInt())
        y += 6f

        val monthRecords = snapshot.attendance.filter { it.dateKey.startsWith(month) }
        val counters = AttendanceCalculator.counters(
            monthRecords.map { it.dateKey to it.status },
            monthRecords.filter { it.status.isLeave }.map { it.dateKey to it.leaveType }
        )

        text("Attendance", 13f, isBold = true)
        divider()
        kvRow("Present days (incl. late / early out)", counters.present.toString())
        kvRow("Late arrivals", counters.late.toString())
        kvRow("Early going", counters.earlyGoing.toString())
        kvRow("Official duty days", counters.officialDuty.toString())
        kvRow("Casual leave", counters.casualLeave.toString())
        kvRow("Earned leave", counters.earnedLeave.toString())
        kvRow("Commuted leave", counters.commutedLeave.toString())
        kvRow("Absent", counters.absent.toString())
        kvRow("Holidays / off days", "${counters.holiday} / ${counters.offday}")
        y += 8f

        text("Pay & finance", 13f, isBold = true)
        divider()
        val finances = snapshot.finances.map { FinanceFigures(it.amount, it.status == FinanceStatus.RECEIVED, it.dateKey) }
        val expenses = snapshot.expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
        kvRow("Salary & allowances received", formatInr(FinancialCalculator.totalReceived(finances)), boldRight = true)
        kvRow("Pending arrears / bills", formatInr(FinancialCalculator.totalPending(finances)))
        kvRow("Personal spend", formatInr(FinancialCalculator.personalSpent(expenses)))
        kvRow("Net balance (received − personal)", formatInr(FinancialCalculator.netBalance(finances, expenses)), boldRight = true)
        y += 8f

        text("Headmaster recoverables", 13f, isBold = true)
        divider()
        kvRow("Outstanding to recover", formatInr(FinancialCalculator.headmasterDue(expenses)), boldRight = true)
        kvRow("Already recovered", formatInr(FinancialCalculator.headmasterRecovered(expenses)))
        y += 8f

        val pendingKeyDates = snapshot.keyDates.filter { it.daysUntilExpiry(today) <= 60 }
        if (pendingKeyDates.isNotEmpty()) {
            text("Key dates needing attention", 13f, isBold = true)
            divider()
            pendingKeyDates.sortedBy { it.validUntil }.forEach { keyDate ->
                val days = keyDate.daysUntilExpiry(today)
                val label = if (days < 0) "Expired ${-days}d ago" else "$days d left"
                kvRow("${keyDate.title} (${keyDate.category}) · $label", DateKeys.formatPretty(keyDate.validUntil))
            }
            y += 8f
        }

        if (snapshot.duties.isNotEmpty()) {
            text("Official duty log", 13f, isBold = true)
            divider()
            snapshot.duties.sortedByDescending { it.dateKey }.take(20).forEach { duty ->
                kvRow(
                    "${DateKeys.formatPretty(duty.dateKey)} · ${duty.meetingWith}",
                    "${DateKeys.formatTime12h(duty.startTime)}–${DateKeys.formatTime12h(duty.endTime)}"
                )
            }
            y += 8f
        }

        if (monthRecords.isNotEmpty()) {
            text("Day-wise attendance", 13f, isBold = true)
            divider()
            monthRecords.sortedBy { it.dateKey }.forEach { record ->
                val times = if (record.hasTimes) {
                    "  ${DateKeys.formatTime12h(record.inTime)}–${DateKeys.formatTime12h(record.outTime)}"
                } else ""
                val suffix = record.leaveType?.let { " · $it" } ?: ""
                kvRow("${DateKeys.formatPretty(record.dateKey)}  ${record.status.label}$suffix$times", "")
            }
        }

        ensure(30f)
        y += 6f
        canvas.drawText(
            "Generated offline by Solo Admin — all figures come from locally stored records.",
            MARGIN, PAGE_HEIGHT - MARGIN, small
        )

        document.finishPage(page)
        val output = java.io.ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    fun attendanceCsv(records: List<AttendanceRecord>): String = BackupCodec.encodeAttendanceCsv(records)

    fun expenseCsv(snapshot: BackupSnapshot): String {
        val header = "Date,Title,Category,Amount,PaidBy,Recovered,Note"
        val rows = snapshot.expenses.sortedByDescending { it.dateKey }.map { expense ->
            listOf(
                expense.dateKey,
                expense.title,
                expense.category,
                formatInr(expense.amount).replace("₹", ""),
                if (expense.owner == ExpenseOwner.HEADMASTER) "Headmaster" else "Personal",
                if (expense.recovered) "Yes" else "No",
                expense.note
            ).joinToString(",") { BackupCodec.csv(it) }
        }
        val summary = listOf(
            "",
            "Summary",
            "Headmaster due,${formatInr(FinancialCalculator.headmasterDue(snapshot.expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) })).replace("₹", "")}",
            "Headmaster recovered,${formatInr(FinancialCalculator.headmasterRecovered(snapshot.expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) })).replace("₹", "")}",
            "Personal spent,${formatInr(FinancialCalculator.personalSpent(snapshot.expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) })).replace("₹", "")}"
        )
        return (listOf(header) + rows + summary).joinToString("\r\n")
    }

    fun financeCsv(snapshot: BackupSnapshot): String {
        val header = "Date,Title,Amount,Status"
        val rows = snapshot.finances.sortedByDescending { it.dateKey }.map { finance ->
            listOf(
                finance.dateKey,
                finance.title,
                formatInr(finance.amount).replace("₹", ""),
                if (finance.status == FinanceStatus.RECEIVED) "Received" else "Pending"
            ).joinToString(",") { BackupCodec.csv(it) }
        }
        return (listOf(header) + rows).joinToString("\r\n")
    }

    fun keyDatesCsv(snapshot: BackupSnapshot, today: LocalDate): String {
        val header = "Title,Category,ValidFrom,ValidUntil,DaysRemaining,Note"
        val rows = snapshot.keyDates.sortedBy { it.validUntil }.map { keyDate ->
            listOf(
                keyDate.title,
                keyDate.category,
                keyDate.validFrom,
                keyDate.validUntil,
                keyDate.daysUntilExpiry(today).let { if (it == Int.MAX_VALUE) "" else it.toString() },
                keyDate.note
            ).joinToString(",") { BackupCodec.csv(it) }
        }
        return (listOf(header) + rows).joinToString("\r\n")
    }

    /** Official-duty history for the accountant: one row per visit. */
    fun dutyCsv(snapshot: BackupSnapshot): String {
        val header = "Date,Mode,Start,End,DurationMinutes,OfficeVisited,Location,Remarks,Attachment"
        val rows = snapshot.duties.sortedByDescending { it.dateKey }.map { duty ->
            listOf(
                duty.dateKey,
                duty.mode.label,
                duty.startTime,
                duty.endTime,
                duty.durationMinutes.toString(),
                duty.meetingWith,
                duty.location,
                duty.remarks,
                if (duty.attachmentPath != null) "Yes" else "No"
            ).joinToString(",") { BackupCodec.csv(it) }
        }
        val totalMinutes = snapshot.duties.sumOf { it.durationMinutes }
        val total = listOf("", "Total,${snapshot.duties.size} visits,,,,${totalMinutes} minutes,,,,,")
        return (listOf(header) + rows + total).joinToString("\r\n")
    }

    /** Renewal list ordered by urgency, matching the in-app reminder order. */
    fun remindersCsv(snapshot: BackupSnapshot, today: LocalDate): String {
        val header = "Status,Title,Category,ValidFrom,ValidUntil,DaysRemaining,Note"
        val rows = snapshot.keyDates
            .sortedBy { keyDate ->
                val days = keyDate.daysUntilExpiry(today)
                if (days == Int.MAX_VALUE) Int.MAX_VALUE - 1 else days
            }
            .map { keyDate ->
                val days = keyDate.daysUntilExpiry(today)
                val status = when {
                    days == Int.MAX_VALUE -> "No expiry"
                    days < 0 -> "Expired"
                    days <= 30 -> "Due soon"
                    days <= 60 -> "Upcoming"
                    else -> "Valid"
                }
                listOf(
                    status,
                    keyDate.title,
                    keyDate.category,
                    keyDate.validFrom,
                    keyDate.validUntil,
                    if (days == Int.MAX_VALUE) "" else days.toString(),
                    keyDate.note
                ).joinToString(",") { BackupCodec.csv(it) }
            }
        return (listOf(header) + rows).joinToString("\r\n")
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf("") }
    }
}

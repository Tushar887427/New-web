package com.soloadmin.hub.ui.screens.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soloadmin.hub.di.AppContainer
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.ExpenseFigures
import com.soloadmin.hub.domain.logic.FinancialCalculator
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import com.soloadmin.hub.ui.components.BalanceCard
import com.soloadmin.hub.ui.components.EmptyState
import com.soloadmin.hub.ui.components.GlassStat
import com.soloadmin.hub.ui.components.GroupDivider
import com.soloadmin.hub.ui.components.GroupedList
import com.soloadmin.hub.ui.components.IconTile
import com.soloadmin.hub.ui.components.ListRow
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.components.StatusPill
import com.soloadmin.hub.ui.navigation.Routes
import com.soloadmin.hub.ui.theme.SoloTheme
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Activity Ledger: the legacy "Total logged records" summary plus a merged timeline of duty, leave
 * and expense entries. Everything is read straight from repositories, no extra table needed.
 */
@Composable
fun LedgerScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit,
    onSnackbar: (String) -> Unit
) {
    val attendanceFlow = remember { container.attendanceRepository.observeAll() }
    val dutyFlow = remember { container.dutyRepository.observeAll() }
    val expenseFlow = remember { container.expenseRepository.observeAll() }
    val taskFlow = remember { container.taskRepository.observeAll() }

    val attendance by attendanceFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val duties by dutyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val expenses by expenseFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by taskFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val counters = remember(attendance) {
        AttendanceCalculator.counters(
            attendance.map { it.dateKey to it.status },
            attendance.filter { it.status.isLeave }.map { it.dateKey to it.leaveType }
        )
    }
    val totalRecords = counters.present + duties.size + counters.casualLeave + counters.earnedLeave +
        counters.commutedLeave + expenses.size
    val expenseFigures = remember(expenses) {
        expenses.map { ExpenseFigures(it.owner, it.amount, it.recovered, it.dateKey, it.category) }
    }
    val headmasterDue = remember(expenseFigures) { FinancialCalculator.headmasterDue(expenseFigures) }
    val monthRecords = remember(attendance) {
        val prefix = DateKeys.monthKey(LocalDate.now())
        attendance.count { it.dateKey.startsWith(prefix) }
    }

    SoloScreen(
        title = "Activity ledger",
        subtitle = "$totalRecords records logged"
    ) {
        item {
            BalanceCard(
                eyebrow = "Activity this month",
                amount = "$monthRecords marked",
                caption = "Attendance, duty, leave and expenses in one place",
                footer = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassStat("Present", counters.present.toString(), Modifier.weight(1f))
                        GlassStat("Duty", counters.officialDuty.toString(), Modifier.weight(1f))
                        GlassStat("Leave", counters.totalLeaves.toString(), Modifier.weight(1f))
                        GlassStat(
                            "Recover",
                            formatInr(Money(headmasterDue.paise)).substringBefore("."),
                            Modifier.weight(1f),
                            Color(0xFFF3C98B)
                        )
                    }
                }
            )
        }

        item {
            GroupedList {
                ListRow(
                    title = "Present days",
                    subtitle = "${counters.late} late · ${counters.earlyGoing} early out",
                    leading = { IconTile(Icons.Outlined.FactCheck, SoloTheme.colors.present, SoloTheme.colors.presentSoft) },
                    trailing = { CountBadge(counters.present) },
                    showChevron = true,
                    onClick = { onNavigate(Routes.ATTENDANCE) }
                )
                GroupDivider()
                ListRow(
                    title = "Official duty",
                    subtitle = "Field visits with times and remarks",
                    leading = { IconTile(Icons.Outlined.Badge, SoloTheme.colors.duty, SoloTheme.colors.dutySoft) },
                    trailing = { CountBadge(duties.size) },
                    showChevron = true,
                    onClick = { onNavigate(Routes.OFFICIAL_DUTY) }
                )
                GroupDivider()
                ListRow(
                    title = "Casual leave",
                    subtitle = "Short personal leave",
                    leading = { IconTile(Icons.Outlined.EventAvailable, SoloTheme.colors.leave, SoloTheme.colors.leaveSoft) },
                    trailing = { CountBadge(counters.casualLeave) },
                    showChevron = true,
                    onClick = { onNavigate(Routes.ATTENDANCE) }
                )
                GroupDivider()
                ListRow(
                    title = "Earned leave",
                    subtitle = "Accrued leave balance usage",
                    leading = { IconTile(Icons.Outlined.CalendarMonth, SoloTheme.colors.early, SoloTheme.colors.earlySoft) },
                    trailing = { CountBadge(counters.earnedLeave) },
                    showChevron = true,
                    onClick = { onNavigate(Routes.ATTENDANCE) }
                )
                GroupDivider()
                ListRow(
                    title = "Commuted leave",
                    subtitle = "Medical / commuted leave",
                    leading = { IconTile(Icons.Outlined.LocalHospital, SoloTheme.colors.absent, SoloTheme.colors.absentSoft) },
                    trailing = { CountBadge(counters.commutedLeave) },
                    showChevron = true,
                    onClick = { onNavigate(Routes.ATTENDANCE) }
                )
                GroupDivider()
                ListRow(
                    title = "Expenses logged",
                    subtitle = "Personal and headmaster bills",
                    leading = { IconTile(Icons.Outlined.Receipt, SoloTheme.colors.warning, SoloTheme.colors.warningSoft) },
                    trailing = { CountBadge(expenses.size) },
                    showChevron = true,
                    onClick = { onNavigate(Routes.EXPENSES) }
                )
                GroupDivider()
                ListRow(
                    title = "Daily tasks",
                    subtitle = "${tasks.count { !it.done }} pending",
                    leading = { IconTile(Icons.Outlined.TaskAlt, MaterialTheme.colorScheme.primary, SoloTheme.colors.tileLilac) },
                    trailing = { CountBadge(tasks.size) },
                    showChevron = true,
                    onClick = { onNavigate(Routes.TASKS) }
                )
            }
        }

        item {
            Text(
                text = "Recent activity",
                style = MaterialTheme.typography.labelLarge,
                color = SoloTheme.colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        val timeline = remember(duties, attendance, expenses, SoloTheme.colors) {
            buildTimeline(duties, attendance, expenses).take(30)
        }

        if (timeline.isEmpty()) {
            item {
                SoloCard(padding = 0.dp) {
                    EmptyState(
                        icon = Icons.Outlined.FactCheck,
                        title = "Nothing logged yet",
                        description = "Punch in, add a duty visit or record an expense and it will show up here."
                    )
                }
            }
        } else {
            items(timeline.size) { index ->
                TimelineRow(timeline[index])
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Text(
        text = count.toString().padStart(2, '0'),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold
    )
}

private data class TimelineEntry(
    val dateKey: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val badgeColor: Color,
    val badgeBackground: Color
)

@Composable
private fun buildTimeline(
    duties: List<OfficialDutyRecord>,
    attendance: List<AttendanceRecord>,
    expenses: List<com.soloadmin.hub.domain.model.ExpenseRecord>
): List<TimelineEntry> {
    val colors = SoloTheme.colors
    val entries = mutableListOf<TimelineEntry>()
    duties.take(15).forEach { duty ->
        entries += TimelineEntry(
            dateKey = duty.dateKey,
            title = duty.meetingWith,
            subtitle = "${duty.mode.label} · ${DateKeys.formatTime12h(duty.startTime)}–${DateKeys.formatTime12h(duty.endTime)}",
            badge = "Duty",
            badgeColor = colors.duty,
            badgeBackground = colors.dutySoft
        )
    }
    attendance.filter { it.status == AttendanceStatus.LEAVE }.take(15).forEach { record ->
        entries += TimelineEntry(
            dateKey = record.dateKey,
            title = record.leaveType ?: "Leave",
            subtitle = record.note.ifBlank { "Logged on calendar" },
            badge = "Leave",
            badgeColor = colors.leave,
            badgeBackground = colors.leaveSoft
        )
    }
    expenses.take(15).forEach { expense ->
        entries += TimelineEntry(
            dateKey = expense.dateKey,
            title = expense.title,
            subtitle = "${expense.category} · ${if (expense.owner == ExpenseOwner.HEADMASTER) "Headmaster" else "Personal"}",
            badge = formatInr(expense.amount),
            badgeColor = if (expense.owner == ExpenseOwner.HEADMASTER) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            badgeBackground = if (expense.owner == ExpenseOwner.HEADMASTER) colors.warningSoft else colors.subtleSurface
        )
    }
    return entries.sortedByDescending { it.dateKey }
}

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            Modifier.width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = DateKeys.parseOrNull(entry.dateKey)?.dayOfMonth?.toString() ?: "--",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = DateKeys.parseOrNull(entry.dateKey)?.let { DateKeys.monthShort(it.monthValue) } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = SoloTheme.colors.textTertiary
            )
            Spacer(Modifier.height(2.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
        ) {
            // timeline body
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SoloTheme.colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(entry.badge, entry.badgeColor, entry.badgeBackground)
    }
}

package com.soloadmin.hub.ui.screens.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.ui.components.CircleIconButton
import com.soloadmin.hub.ui.components.GroupDivider
import com.soloadmin.hub.ui.components.GroupedList
import com.soloadmin.hub.ui.components.IconTile
import com.soloadmin.hub.ui.components.ListRow
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.SoloScreen
import com.soloadmin.hub.ui.screens.home.MissedPunchSheet
import com.soloadmin.hub.ui.theme.SoloColors
import com.soloadmin.hub.ui.theme.SoloTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly attendance calendar.
 *
 * The grid keeps the legacy colour semantics (one colour per status) but uses soft fills with a
 * coloured numeral plus a small marker, so the screen survives dark mode and colour-blind users.
 */
@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel,
    onBack: () -> Unit,
    onOpenDuty: () -> Unit,
    onSnackbar: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var showLogTime by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { onSnackbar(it) }
    }

    SoloScreen(
        title = "Attendance",
        onBack = onBack,
        subtitle = DateKeys.formatMonthYearComma(state.month),
        actions = {
            CircleIconButton(
                icon = Icons.Outlined.Schedule,
                contentDescription = "Log time",
                onClick = { showLogTime = true },
                size = 38.dp,
                container = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    ) {
        item {
            MonthSwitcher(
                month = state.month,
                isCurrentMonth = state.month == YearMonth.now(),
                onPrevious = { viewModel.changeMonth(-1) },
                onNext = { viewModel.changeMonth(1) },
                onToday = { viewModel.goToMonth(YearMonth.now()) }
            )
        }

        item {
            CalendarCard(
                state = state,
                onDayClick = { date -> editingDate = date }
            )
        }

        item { StatsRow(state) }

        item {
            Text(
                text = "Legends",
                style = MaterialTheme.typography.labelLarge,
                color = SoloTheme.colors.textTertiary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item { LegendGrid() }

        item {
            GroupedList {
                ListRow(
                    title = "Official duty records",
                    subtitle = "Field visits linked to the calendar",
                    leading = { IconTile(Icons.Outlined.Badge, SoloTheme.colors.duty, SoloTheme.colors.dutySoft) },
                    showChevron = true,
                    onClick = onOpenDuty
                )
                GroupDivider()
                ListRow(
                    title = "Month work hours",
                    subtitle = "From recorded in/out times",
                    leading = { IconTile(Icons.Outlined.Schedule, MaterialTheme.colorScheme.primary, SoloTheme.colors.tileSky) },
                    trailing = {
                        Text(
                            text = AttendanceCalculator.formatDuration(state.totalWorkedMinutes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }
    }

    editingDate?.let { date ->
        DayEditorSheet(
            dateKey = DateKeys.of(date),
            existing = state.recordFor(date),
            defaultLeaveType = state.recordFor(date)?.leaveType,
            onDismiss = { editingDate = null },
            onSave = { status, leaveType, inTime, outTime, note ->
                viewModel.saveDay(DateKeys.of(date), status, leaveType, inTime, outTime, note)
                editingDate = null
            },
            onClear = {
                viewModel.clearDay(DateKeys.of(date))
                editingDate = null
            }
        )
    }

    if (showLogTime) {
        MissedPunchSheet(
            todayKey = DateKeys.of(LocalDate.now()),
            defaultIn = state.records[DateKeys.of(LocalDate.now())]?.inTime ?: "09:30",
            defaultOut = state.records[DateKeys.of(LocalDate.now())]?.outTime ?: "17:00",
            defaultNote = state.records[DateKeys.of(LocalDate.now())]?.note.orEmpty(),
            shiftStart = state.shiftStart,
            onDismiss = { showLogTime = false },
            onSave = { date, inTime, outTime, note ->
                viewModel.saveManualPunch(date, inTime, outTime, note, state.shiftStart)
                showLogTime = false
            }
        )
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = Icons.Outlined.ChevronLeft,
                contentDescription = "Previous month",
                onClick = onPrevious,
                size = 34.dp
            )
            Text(
                text = DateKeys.formatMonthYearComma(month),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            CircleIconButton(
                icon = Icons.Outlined.ChevronRight,
                contentDescription = "Next month",
                onClick = onNext,
                size = 34.dp
            )
        }
        if (!isCurrentMonth) {
            Text(
                text = "This month",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToday)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CalendarCard(state: AttendanceUiState, onDayClick: (LocalDate) -> Unit) {
    SoloCard(padding = 14.dp) {
        Row(Modifier.fillMaxWidth()) {
            DateKeys.DAYS.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = SoloTheme.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val firstDay = state.month.atDay(1)
        val leadingBlanks = DateKeys.weekdayIndexSundayFirst(firstDay)
        val daysInMonth = state.month.lengthOfMonth()
        val cells = leadingBlanks + daysInMonth
        val rows = (cells + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val dayNumber = index - leadingBlanks + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = state.month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                record = state.recordFor(date),
                                isToday = date == state.today,
                                isWeeklyOff = DateKeys.weekdayIndexSundayFirst(date) == state.weeklyOffDay,
                                onClick = { onDayClick(date) }
                            )
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    record: AttendanceRecord?,
    isToday: Boolean,
    isWeeklyOff: Boolean,
    onClick: () -> Unit
) {
    val colors = SoloTheme.colors
    val palette = paletteFor(record?.status, isWeeklyOff, colors)
    val borderColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (record != null) palette.background else if (isWeeklyOff) colors.offdaySoft else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (isToday) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (record != null) palette.foreground else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
            )
            if (record != null) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(palette.foreground)
                )
            }
        }
    }
}

data class StatusPalette(val background: androidx.compose.ui.graphics.Color, val foreground: androidx.compose.ui.graphics.Color)

@Composable
fun paletteFor(status: AttendanceStatus?, isWeeklyOff: Boolean, colors: SoloColors): StatusPalette =
    when (status) {
        AttendanceStatus.PRESENT -> StatusPalette(colors.presentSoft, colors.present)
        AttendanceStatus.LATE -> StatusPalette(colors.lateSoft, colors.late)
        AttendanceStatus.EARLY_GOING -> StatusPalette(colors.earlySoft, colors.early)
        AttendanceStatus.LEAVE -> StatusPalette(colors.leaveSoft, colors.leave)
        AttendanceStatus.ABSENT -> StatusPalette(colors.absentSoft, colors.absent)
        AttendanceStatus.HOLIDAY -> StatusPalette(colors.holidaySoft, colors.holiday)
        AttendanceStatus.OFFDAY -> StatusPalette(colors.offdaySoft, colors.offday)
        AttendanceStatus.OUTSTATION -> StatusPalette(colors.dutySoft, colors.duty)
        AttendanceStatus.MISS_PUNCH -> StatusPalette(colors.missPunchSoft, colors.missPunch)
        null -> if (isWeeklyOff) StatusPalette(colors.offdaySoft, colors.offday)
        else StatusPalette(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.onSurface)
    }

@Composable
private fun StatsRow(state: AttendanceUiState) {
    val colors = SoloTheme.colors
    SoloCard {
        Text(
            text = "Month at a glance",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Present", state.counters.present, colors.present, Modifier.weight(1f))
            MiniStat("Duty", state.counters.officialDuty, colors.duty, Modifier.weight(1f))
            MiniStat("Leave", state.counters.totalLeaves, colors.leave, Modifier.weight(1f))
            MiniStat("Late", state.counters.late, colors.late, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Early out", state.counters.earlyGoing, colors.early, Modifier.weight(1f))
            MiniStat("Absent", state.counters.absent, colors.absent, Modifier.weight(1f))
            MiniStat("Holiday", state.counters.holiday, colors.holiday, Modifier.weight(1f))
            MiniStat("Miss punch", state.counters.missPunch, colors.missPunch, Modifier.weight(1f))
        }
        if (state.averageStartMinutes != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Average start ${DateKeys.formatTime12h("%02d:%02d".format(state.averageStartMinutes / 60, state.averageStartMinutes % 60))} · ${state.recordedDays} days recorded",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SoloTheme.colors.subtleSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun LegendGrid() {
    val colors = SoloTheme.colors
    val entries = listOf(
        "Present" to colors.present,
        "Late" to colors.late,
        "Early out" to colors.early,
        "Official duty" to colors.duty,
        "Leave" to colors.leave,
        "Absent" to colors.absent,
        "Holiday" to colors.holiday,
        "Off day" to colors.offday,
        "Miss punch" to colors.missPunch
    )
    SoloCard(padding = 14.dp) {
        entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                row.forEach { (label, color) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

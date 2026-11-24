package com.soloadmin.hub.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.soloadmin.hub.data.preferences.AppSettings
import com.soloadmin.hub.data.files.photoFile
import com.soloadmin.hub.domain.logic.AttendanceCalculator
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.logic.formatInr
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.ExpiryState
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.ui.components.BalanceCard
import com.soloadmin.hub.ui.components.CircleIconButton
import com.soloadmin.hub.ui.components.GlassStat
import com.soloadmin.hub.ui.components.GroupDivider
import com.soloadmin.hub.ui.components.GroupedList
import com.soloadmin.hub.ui.components.IconTile
import com.soloadmin.hub.ui.components.ListRow
import com.soloadmin.hub.ui.components.SoloCard
import com.soloadmin.hub.ui.components.StatusPill
import com.soloadmin.hub.ui.components.ValueBar
import com.soloadmin.hub.ui.navigation.Routes
import com.soloadmin.hub.ui.screens.attendance.AttendanceViewModel
import com.soloadmin.hub.ui.theme.SoloSpacing
import com.soloadmin.hub.ui.screens.home.HomeEvent
import com.soloadmin.hub.ui.theme.SoloColors
import com.soloadmin.hub.ui.theme.SoloTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.collect

/**
 * Home dashboard.
 *
 * Layout priority mirrors the legacy app (today's attendance first, then month figures, then
 * recoverables and shortcuts) but is reorganised as: identity strip → live punch card → month
 * numbers → recoverable strip → reminders → office actions.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    leaveViewModel: AttendanceViewModel,
    settings: AppSettings,
    onNavigate: (String) -> Unit,
    onSnackbar: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var showMissedPunch by remember { mutableStateOf(false) }
    var showQuickLeave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshToday()
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.Message -> onSnackbar(event.text)
                is HomeEvent.PunchChanged -> if (settings.hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(SoloSpacing.md)
    ) {
        item { HomeHeader(state, onNavigate) }

        item {
            Column(Modifier.padding(horizontal = SoloSpacing.gutter)) {
                TodayCard(
                    state = state,
                    compact = settings.compactCards,
                    onPunch = viewModel::punch,
                    onBreak = viewModel::toggleBreak,
                    onLogTime = { showMissedPunch = true },
                    onDuty = { onNavigate(Routes.OFFICIAL_DUTY) },
                    onAttendance = { onNavigate(Routes.ATTENDANCE) },
                    onDetails = { onNavigate(Routes.ATTENDANCE) }
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = SoloSpacing.gutter)) {
                MonthSummaryCard(state = state, onOpenLedger = { onNavigate(Routes.LEDGER) })
            }
        }

        if (settings.dashboardCharts) {
            item {
                Column(Modifier.padding(horizontal = SoloSpacing.gutter)) {
                    FinanceSnapshotCard(state = state, onOpenFinance = { onNavigate(Routes.FINANCE) })
                }
            }
        }

        if (state.headmasterDue.paise > 0) {
            item {
                Column(Modifier.padding(horizontal = SoloSpacing.gutter)) {
                    HeadmasterStrip(state.headmasterDue) { onNavigate(Routes.EXPENSES) }
                }
            }
        }

        if (state.expiringKeyDates.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = SoloSpacing.gutter)) {
                    KeyDateAlertCard(
                        items = state.expiringKeyDates,
                        today = state.today,
                        onOpen = { onNavigate(Routes.KEY_DATES) }
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = SoloSpacing.gutter)) {
                Text(
                    text = "Office actions",
                    style = MaterialTheme.typography.labelLarge,
                    color = SoloTheme.colors.textTertiary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                GroupedList {
                    ListRow(
                        title = "Log or correct office time",
                        subtitle = "Missed punch, late entry, half day",
                        leading = { IconTile(Icons.Outlined.Schedule, MaterialTheme.colorScheme.primary, SoloTheme.colors.tileMint) },
                        showChevron = true,
                        onClick = { showMissedPunch = true }
                    )
                    GroupDivider()
                    ListRow(
                        title = "Official duty",
                        subtitle = "Record a field visit and mark the calendar",
                        leading = { IconTile(Icons.Outlined.Badge, SoloTheme.colors.duty, SoloTheme.colors.dutySoft) },
                        showChevron = true,
                        onClick = { onNavigate(Routes.OFFICIAL_DUTY) }
                    )
                    GroupDivider()
                    ListRow(
                        title = "Log leave",
                        subtitle = "Casual, earned or commuted",
                        leading = { IconTile(Icons.Outlined.EventAvailable, SoloTheme.colors.leave, SoloTheme.colors.leaveSoft) },
                        showChevron = true,
                        onClick = { showQuickLeave = true }
                    )
                    GroupDivider()
                    ListRow(
                        title = "Attendance calendar",
                        subtitle = DateKeys.formatMonthYearComma(state.month),
                        leading = { IconTile(Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary, SoloTheme.colors.tileSky) },
                        showChevron = true,
                        onClick = { onNavigate(Routes.ATTENDANCE) }
                    )
                    GroupDivider()
                    ListRow(
                        title = "Daily tasks",
                        subtitle = if (state.pendingTasks == 0) "Nothing pending" else "${state.pendingTasks} pending",
                        leading = { IconTile(Icons.Outlined.TaskAlt, SoloTheme.colors.early, SoloTheme.colors.tileLilac) },
                        showChevron = true,
                        onClick = { onNavigate(Routes.TASKS) }
                    )
                    GroupDivider()
                    ListRow(
                        title = "Colleagues",
                        subtitle = "Directory and contact details",
                        leading = { IconTile(Icons.Outlined.Verified, MaterialTheme.colorScheme.primary, SoloTheme.colors.tileSky) },
                        showChevron = true,
                        onClick = { onNavigate(Routes.COLLEAGUES) }
                    )
                }
            }
        }

        item {
            Text(
                text = "Solo Admin · offline first · data stays on this device",
                style = MaterialTheme.typography.labelSmall,
                color = SoloTheme.colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showMissedPunch) {
        MissedPunchSheet(
            todayKey = DateKeys.of(state.today),
            defaultIn = state.todayRecord?.inTime ?: "09:30",
            defaultOut = state.todayRecord?.outTime ?: "17:00",
            defaultNote = state.todayRecord?.note.orEmpty(),
            shiftStart = state.profile.shiftStart,
            onDismiss = { showMissedPunch = false },
            onSave = { date, inTime, outTime, note ->
                viewModel.recordManualTime(date, inTime, outTime, note, state.profile.shiftStart)
                showMissedPunch = false
            }
        )
    }

    if (showQuickLeave) {
        QuickLeaveSheet(
            todayKey = DateKeys.of(state.today),
            viewModel = leaveViewModel,
            onDismiss = { showQuickLeave = false },
            onSaved = {
                showQuickLeave = false
                onSnackbar("Leave saved to the calendar")
            }
        )
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onNavigate: (String) -> Unit) {
    val colors = SoloTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = SoloSpacing.gutter, end = SoloSpacing.sm, top = 10.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Gov Office Hub",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(colors.positive)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Hello, ${state.profile.name.ifBlank { "Admin" }}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateKeys.prettyWithWeekday(state.today),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
            }
            CircleIconButton(
                icon = Icons.Outlined.CalendarMonth,
                contentDescription = "Attendance",
                onClick = { onNavigate(Routes.ATTENDANCE) },
                size = 38.dp
            )
            Spacer(Modifier.width(2.dp))
            CircleIconButton(
                icon = Icons.Outlined.TaskAlt,
                contentDescription = "Daily tasks",
                onClick = { onNavigate(Routes.TASKS) },
                size = 38.dp
            )
            Spacer(Modifier.width(2.dp))
            ProfileBubble(state, onClick = { onNavigate(Routes.PROFILE) })
        }
    }
}

@Composable
private fun ProfileBubble(state: HomeUiState, onClick: () -> Unit) {
    val photo = photoFile(state.profile.photoPath)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null && photo.exists()) {
            AsyncImage(model = photo, contentDescription = "My profile", modifier = Modifier.size(40.dp).clip(CircleShape))
        } else {
            Text(
                text = state.profile.initials,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TodayCard(
    state: HomeUiState,
    compact: Boolean,
    onPunch: () -> Unit,
    onBreak: () -> Unit,
    onLogTime: () -> Unit,
    onDuty: () -> Unit,
    onAttendance: () -> Unit,
    onDetails: () -> Unit
) {
    val colors = SoloTheme.colors
    val punchedIn = state.session.punchedIn
    val hasCompleted = !state.session.inTime.isNullOrBlank() && !state.session.outTime.isNullOrBlank()
    val status = state.todayRecord?.status
    val isOffDay = state.isWeeklyOffToday && state.todayRecord == null

    SoloCard(padding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Today's attendance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            isOffDay -> "Weekly off · ${state.profile.weeklyOffLabel}"
                            status != null -> status.label + (state.todayRecord?.leaveType?.let { " · $it" } ?: "")
                            punchedIn -> "Punched in · you are on duty"
                            hasCompleted -> "Day closed"
                            else -> "Not punched in yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary
                    )
                }
                when {
                    punchedIn -> StatusPill("On duty", colors.positive, colors.positiveSoft, showDot = true)
                    hasCompleted -> StatusPill("Completed", MaterialTheme.colorScheme.onSurfaceVariant, colors.subtleSurface)
                    status == AttendanceStatus.LATE -> StatusPill("Late", colors.late, colors.lateSoft)
                    status == AttendanceStatus.OUTSTATION -> StatusPill("Official duty", colors.duty, colors.dutySoft)
                    else -> StatusPill("Not marked", colors.textTertiary, colors.subtleSurface)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.subtleSurface)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeCell(
                    label = "In",
                    value = DateKeys.formatTime12h(state.session.inTime),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                TimeCell(
                    label = "Out",
                    value = DateKeys.formatTime12h(state.session.outTime),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                TimeCell(
                    label = "Breaks",
                    value = "${state.session.totalBreakMinutes(System.currentTimeMillis())}m",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            PunchButton(punchedIn = punchedIn, hasCompleted = hasCompleted, onClick = onPunch)

            if (!compact) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(
                        icon = Icons.Outlined.Schedule,
                        label = "Log time",
                        onClick = onLogTime,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        icon = Icons.Outlined.Badge,
                        label = "Duty",
                        onClick = onDuty,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        icon = if (state.session.onBreak) Icons.Outlined.PauseCircle else Icons.Outlined.Coffee,
                        label = if (state.session.onBreak) "End break" else "Break",
                        onClick = onBreak,
                        highlight = state.session.onBreak,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDetails)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Weekly off ${state.profile.weeklyOffLabel} · shift ${DateKeys.formatTime12h(state.profile.shiftStart)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SoloTheme.colors.textTertiary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun PunchButton(punchedIn: Boolean, hasCompleted: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = when {
        punchedIn -> "Punch out"
        hasCompleted -> "Punch in again"
        else -> "Punch in now"
    }
    val container = when {
        punchedIn -> SoloTheme.colors.danger
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(container)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (punchedIn) Icons.Outlined.PauseCircle else Icons.Outlined.Bolt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val colors = SoloTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) colors.warningSoft else colors.subtleSurface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (highlight) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun MonthSummaryCard(state: HomeUiState, onOpenLedger: () -> Unit) {
    val colors = SoloTheme.colors
    val totalMarked = state.counters.present + state.counters.officialDuty + state.counters.totalLeaves
    SoloCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "This month",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = DateKeys.formatMonthYear(state.month),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
            }
            Text(
                text = "Ledger",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenLedger)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBlock("Present", state.counters.present.toString().padStart(2, '0'), colors.positive, Modifier.weight(1f))
            MetricBlock("On duty", state.counters.officialDuty.toString().padStart(2, '0'), colors.duty, Modifier.weight(1f))
            MetricBlock("Leaves", state.counters.totalLeaves.toString().padStart(2, '0'), colors.warning, Modifier.weight(1f))
        }
        if (totalMarked > 0) {
            Spacer(Modifier.height(14.dp))
            val maxDays = maxOf(state.month.lengthOfMonth() - 4, 1)
            ValueBar(
                fraction = totalMarked.toFloat() / maxDays.toFloat(),
                color = MaterialTheme.colorScheme.primary,
                height = 6.dp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$totalMarked day${if (totalMarked == 1) "" else "s"} recorded · ${state.counters.late} late · ${state.counters.earlyGoing} early out",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SoloTheme.colors.subtleSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent,
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
private fun FinanceSnapshotCard(state: HomeUiState, onOpenFinance: () -> Unit) {
    val colors = SoloTheme.colors
    val netThisMonth = state.monthNet
    BalanceCard(
        eyebrow = "This month's balance",
        amount = formatInr(netThisMonth),
        modifier = Modifier.clickable(onClick = onOpenFinance),
        caption = "Received ${formatInr(state.monthReceived)} · spent ${formatInr(state.monthPersonal)}",
        accent = if (netThisMonth.isNegative) colors.tileClay else Color(0xFF9FE3C4),
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassStat("Pending bills", formatInr(state.monthPending), Modifier.weight(1f), Color(0xFFF3C98B))
                GlassStat("Duty days", state.recentDuties.toString(), Modifier.weight(1f), Color(0xFF9FE3C4))
                GlassStat(
                    "Duty hours",
                    AttendanceCalculator.formatDuration(state.dutyHoursThisMonth),
                    Modifier.weight(if (state.dutyHoursThisMonth > 0) 1.2f else 1f),
                    Color(0xFFCFE9DC)
                )
            }
        }
    )
}

@Composable
private fun HeadmasterStrip(due: Money, onClick: () -> Unit) {
    val colors = SoloTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.warningSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = Icons.Outlined.Receipt,
            tint = colors.warning,
            background = Color.White.copy(alpha = if (colors.isDark) 0.06f else 0.6f),
            size = 36.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Recover from Headmaster",
                style = MaterialTheme.typography.labelMedium,
                color = colors.warning,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap to view or settle bills",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning.copy(alpha = 0.75f)
            )
        }
        Text(
            text = formatInr(due),
            style = MaterialTheme.typography.titleMedium,
            color = colors.warning,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun KeyDateAlertCard(items: List<KeyDateRecord>, today: LocalDate, onOpen: () -> Unit) {
    val colors = SoloTheme.colors
    val soonest = items.minByOrNull { it.validUntil } ?: return
    val state = soonest.expiryState(today)
    val accent = when (state) {
        is ExpiryState.Expired -> colors.danger
        else -> colors.warning
    }
    SoloCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = Icons.Outlined.NotificationsActive,
                tint = accent,
                background = if (state is ExpiryState.Expired) colors.dangerSoft else colors.warningSoft
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (items.size == 1) "1 key date needs attention" else "${items.size} key dates need attention",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${soonest.title} · ${state.badgeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        ValueBar(
            fraction = soonest.validityFraction(today),
            color = accent,
            height = 5.dp
        )
    }
}

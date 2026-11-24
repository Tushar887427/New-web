package com.soloadmin.hub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.soloadmin.hub.di.AppContainer
import com.soloadmin.hub.ui.screens.attendance.AttendanceViewModel
import com.soloadmin.hub.ui.screens.colleagues.ColleaguesViewModel
import com.soloadmin.hub.ui.screens.expenses.ExpensesViewModel
import com.soloadmin.hub.ui.screens.finance.FinanceViewModel
import com.soloadmin.hub.ui.screens.home.HomeViewModel
import com.soloadmin.hub.ui.screens.keydates.KeyDatesViewModel
import com.soloadmin.hub.ui.screens.officialduty.OfficialDutyViewModel
import com.soloadmin.hub.ui.screens.profile.ProfileViewModel
import com.soloadmin.hub.ui.screens.settings.SettingsViewModel
import com.soloadmin.hub.ui.screens.tasks.TasksViewModel

/** Single factory that maps every ViewModel to its dependencies from [AppContainer]. */
class SoloViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            container.profileRepository,
            container.attendanceRepository,
            container.expenseRepository,
            container.financeRepository,
            container.keyDateRepository,
            container.taskRepository,
            container.dutyRepository,
            container.workSessionRepository,
            container.settings
        ) as T

        modelClass.isAssignableFrom(AttendanceViewModel::class.java) -> AttendanceViewModel(
            container.attendanceRepository,
            container.profileRepository,
            container.dutyRepository
        ) as T

        modelClass.isAssignableFrom(OfficialDutyViewModel::class.java) -> OfficialDutyViewModel(
            container.dutyRepository,
            container.attendanceRepository,
            container.mediaStore
        ) as T

        modelClass.isAssignableFrom(ExpensesViewModel::class.java) -> ExpensesViewModel(
            container.expenseRepository
        ) as T

        modelClass.isAssignableFrom(FinanceViewModel::class.java) -> FinanceViewModel(
            container.financeRepository,
            container.expenseRepository
        ) as T

        modelClass.isAssignableFrom(KeyDatesViewModel::class.java) -> KeyDatesViewModel(
            container.keyDateRepository,
            container.settings
        ) as T

        modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(
            container.taskRepository
        ) as T

        modelClass.isAssignableFrom(ColleaguesViewModel::class.java) -> ColleaguesViewModel(
            container.colleagueRepository,
            container.mediaStore
        ) as T

        modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(
            container.profileRepository,
            container.attendanceRepository,
            container.colleagueRepository,
            container.mediaStore
        ) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            container.settings,
            container.dataTransferRepository,
            container.backupFiles,
            container.reminderScheduler
        ) as T

        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

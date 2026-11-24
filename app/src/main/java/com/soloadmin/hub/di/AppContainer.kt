package com.soloadmin.hub.di

import android.content.Context
import com.soloadmin.hub.data.database.SoloDatabase
import com.soloadmin.hub.data.files.BackupFilesService
import com.soloadmin.hub.data.files.MediaStore
import com.soloadmin.hub.data.preferences.SettingsRepository
import com.soloadmin.hub.data.repository.AttendanceRepository
import com.soloadmin.hub.data.repository.ColleagueRepository
import com.soloadmin.hub.data.repository.DataTransferRepository
import com.soloadmin.hub.data.repository.ExpenseRepository
import com.soloadmin.hub.data.repository.FinanceRepository
import com.soloadmin.hub.data.repository.KeyDateRepository
import com.soloadmin.hub.data.repository.OfficialDutyRepository
import com.soloadmin.hub.data.repository.ProfileRepository
import com.soloadmin.hub.data.repository.TaskRepository
import com.soloadmin.hub.data.repository.WorkSessionRepository
import com.soloadmin.hub.notifications.ReminderScheduler

/**
 * Manual dependency container.
 *
 * A small, explicit graph is enough for an app this size and avoids annotation processing
 * (kapt/KSP for Hilt) — faster builds, nothing to misconfigure in a fresh Android Studio import,
 * and the whole object graph is readable in one screen.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: SoloDatabase by lazy { SoloDatabase.get(appContext) }
    val mediaStore: MediaStore by lazy { MediaStore(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val backupFiles: BackupFilesService by lazy { BackupFilesService(appContext, mediaStore) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(database, mediaStore) }
    val attendanceRepository: AttendanceRepository by lazy { AttendanceRepository(database) }
    val dutyRepository: OfficialDutyRepository by lazy { OfficialDutyRepository(database) }
    val expenseRepository: ExpenseRepository by lazy { ExpenseRepository(database) }
    val financeRepository: FinanceRepository by lazy { FinanceRepository(database) }
    val keyDateRepository: KeyDateRepository by lazy { KeyDateRepository(database) }
    val taskRepository: TaskRepository by lazy { TaskRepository(database) }
    val colleagueRepository: ColleagueRepository by lazy { ColleagueRepository(database, mediaStore) }
    val workSessionRepository: WorkSessionRepository by lazy { WorkSessionRepository(database) }
    val dataTransferRepository: DataTransferRepository by lazy {
        DataTransferRepository(database, mediaStore, profileRepository)
    }
}

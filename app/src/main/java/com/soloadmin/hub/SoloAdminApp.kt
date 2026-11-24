package com.soloadmin.hub

import android.app.Application
import com.soloadmin.hub.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class SoloAdminApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderScheduler.createChannels()

        // A fresh install starts with the same sample records the legacy app shipped with, so the
        // dashboard is immediately meaningful. Existing data is never touched.
        appScope.launch {
            runCatching { container.dataTransferRepository.seedIfEmpty(LocalDate.now()) }
            runCatching {
                container.settings.settings.collect { settings ->
                    container.reminderScheduler.setLeadDays(settings.reminderLeadDays)
                    container.reminderScheduler.scheduleDailyCheck(settings.remindersEnabled)
                }
            }
        }
    }
}

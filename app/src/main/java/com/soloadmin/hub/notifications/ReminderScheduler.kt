package com.soloadmin.hub.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soloadmin.hub.MainActivity
import com.soloadmin.hub.R
import com.soloadmin.hub.data.database.SoloDatabase
import com.soloadmin.hub.data.repository.toDomain
import com.soloadmin.hub.domain.logic.DateKeys
import com.soloadmin.hub.domain.model.KeyDateRecord
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Local expiry reminders.
 *
 * WorkManager runs a single daily check (no polling, no wakelocks beyond the system's scheduling)
 * and posts at most one grouped notification per day listing the documents that need attention.
 * Everything works offline and no notification is posted without the runtime permission.
 */
class ReminderScheduler(private val context: Context) {

    companion object {
        const val CHANNEL_REMINDERS = "key_date_reminders"
        const val CHANNEL_TASKS = "daily_tasks"
        private const val WORK_DAILY_CHECK = "solo_admin_daily_key_dates"
        private const val WORK_IMMEDIATE = "solo_admin_key_dates_now"
        private const val NOTIFICATION_ID = 4201
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(reminders)
        val tasks = NotificationChannel(
            CHANNEL_TASKS,
            context.getString(R.string.channel_daily_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_daily_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(tasks)
    }

    fun scheduleDailyCheck(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_DAILY_CHECK)
            return
        }
        val request = PeriodicWorkRequestBuilder<KeyDateReminderWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_DAILY_CHECK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Runs a check shortly after the user changes reminder settings or key dates. */
    /** Keeps the lead-day window readable by [KeyDateReminderWorker]. */
    fun setLeadDays(days: Int) {
        context.getSharedPreferences(KeyDateReminderWorker.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KeyDateReminderWorker.KEY_LEAD_DAYS, days.coerceIn(1, 120))
            .apply()
    }

    fun checkSoon() {
        val request = OneTimeWorkRequestBuilder<KeyDateReminderWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_IMMEDIATE, ExistingWorkPolicy.REPLACE, request)
    }

    private fun initialDelayMinutes(): Long {
        val now = java.time.LocalTime.now()
        val target = java.time.LocalTime.of(9, 0)
        val minutes = java.time.Duration.between(now, target).toMinutes()
        return if (minutes > 0) minutes else minutes + 24 * 60
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notifyExpiring(items: List<KeyDateRecord>, leadDays: Int): Boolean {
        if (items.isEmpty() || !canPostNotifications()) return false
        val today = LocalDate.now()
        val title = if (items.size == 1) "1 document needs attention" else "${items.size} documents need attention"
        val lines = items.take(4).map { item ->
            val days = item.daysUntilExpiry(today)
            val whenText = when {
                days < 0 -> "expired ${-days}d ago"
                days == 0 -> "expires today"
                else -> "$days days left"
            }
            "${item.title} — $whenText"
        }.joinToString("\n")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, "key_dates")
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Tap to review key dates and renewal steps.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun clearNotifications() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

/**
 * Reads key dates from the database directly (WorkManager may run when no UI exists) and notifies
 * about anything expiring inside the configured lead window, plus anything already expired once.
 */
class KeyDateReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val database = SoloDatabase.get(applicationContext)
        val scheduler = ReminderScheduler(applicationContext)
        scheduler.createChannels()
        if (!scheduler.canPostNotifications()) return Result.success()

        // The lead window mirrors the DataStore preference; the scheduler keeps this mirror in
        // sync whenever the user changes it, so the worker needs no UI dependency.
        val leadDays = applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LEAD_DAYS, 30)

        val today = LocalDate.now()
        val items = database.keyDateDao().getAll()
            .map { it.toDomain() }
            .filter { record ->
                val days = record.daysUntilExpiry(today)
                days in 0..leadDays
            }
            .sortedBy { DateKeys.daysUntil(it.validUntil, today) }

        scheduler.notifyExpiring(items, leadDays)
        return Result.success()
    }

    companion object {
        const val PREFS = "reminder_prefs"
        const val KEY_LEAD_DAYS = "lead_days"
    }
}

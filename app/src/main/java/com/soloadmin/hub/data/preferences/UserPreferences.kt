package com.soloadmin.hub.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "solo_admin_settings")

enum class ThemeChoice(val storageKey: String, val label: String) {
    SYSTEM("system", "Follow system"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(value: String?): ThemeChoice =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}

enum class StartScreen(val storageKey: String, val label: String) {
    HOME("home", "Home"),
    LEDGER("ledger", "Ledger"),
    EXPENSES("expenses", "Expenses"),
    FINANCE("finance", "Finance");

    companion object {
        fun fromKey(value: String?): StartScreen =
            entries.firstOrNull { it.storageKey == value } ?: HOME
    }
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColour: Boolean = false,
    val startScreen: StartScreen = StartScreen.HOME,
    val remindersEnabled: Boolean = true,
    /** Days before expiry the reminder notification fires. */
    val reminderLeadDays: Int = 30,
    val hapticsEnabled: Boolean = true,
    val dashboardCharts: Boolean = true,
    val compactCards: Boolean = false,
    val seeded: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_colour")
        val START_SCREEN = stringPreferencesKey("start_screen")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val LEAD_DAYS = intPreferencesKey("reminder_lead_days")
        val HAPTICS = booleanPreferencesKey("haptics")
        val CHARTS = booleanPreferencesKey("charts")
        val COMPACT = booleanPreferencesKey("compact_cards")
        val SEEDED = booleanPreferencesKey("seeded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = ThemeChoice.fromKey(prefs[Keys.THEME]),
            dynamicColour = prefs[Keys.DYNAMIC] ?: false,
            startScreen = StartScreen.fromKey(prefs[Keys.START_SCREEN]),
            remindersEnabled = prefs[Keys.REMINDERS] ?: true,
            reminderLeadDays = (prefs[Keys.LEAD_DAYS] ?: 30).coerceIn(1, 120),
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            dashboardCharts = prefs[Keys.CHARTS] ?: true,
            compactCards = prefs[Keys.COMPACT] ?: false,
            seeded = prefs[Keys.SEEDED] ?: false
        )
    }

    suspend fun setTheme(value: ThemeChoice) = edit { it[Keys.THEME] = value.storageKey }
    suspend fun setDynamicColour(value: Boolean) = edit { it[Keys.DYNAMIC] = value }
    suspend fun setStartScreen(value: StartScreen) = edit { it[Keys.START_SCREEN] = value.storageKey }
    suspend fun setRemindersEnabled(value: Boolean) = edit { it[Keys.REMINDERS] = value }
    suspend fun setReminderLeadDays(value: Int) = edit { it[Keys.LEAD_DAYS] = value.coerceIn(1, 120) }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.HAPTICS] = value }
    suspend fun setDashboardCharts(value: Boolean) = edit { it[Keys.CHARTS] = value }
    suspend fun setCompactCards(value: Boolean) = edit { it[Keys.COMPACT] = value }
    suspend fun setSeeded(value: Boolean) = edit { it[Keys.SEEDED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }
}

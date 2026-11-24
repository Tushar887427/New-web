package com.soloadmin.hub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.soloadmin.hub.data.preferences.AppSettings
import com.soloadmin.hub.ui.navigation.SoloAdminApp
import com.soloadmin.hub.ui.theme.SoloAdminTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_ROUTE = "com.soloadmin.hub.OPEN_ROUTE"
    }

    private val container by lazy { (application as SoloAdminApp).container }

    /** A backup file tapped in another app (Files, mail, downloads) is staged for import. */
    private var pendingImport by mutableStateOf<android.net.Uri?>(null)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* A denial simply means reminders stay silent; the preference is unchanged. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        container.reminderScheduler.createChannels()
        var openRoute = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        intent?.let { incoming ->
            val data = incoming.data
            if (incoming.action == Intent.ACTION_VIEW && data != null) {
                pendingImport = data
                openRoute = "backup"
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsState(initial = AppSettings())
            SoloTinyThemeGate(theme = settings, content = {
                SoloAdminApp(
                    settings = settings,
                    container = container,
                    launchRoute = openRoute,
                    importUri = pendingImport,
                    onImportConsumed = { pendingImport = null },
                    onRequestNotificationPermission = ::ensureNotificationPermission
                )
            })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { pendingImport = it }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun SoloTinyThemeGate(theme: AppSettings, content: @Composable () -> Unit) {
    SoloAdminTheme(
        themeChoice = theme.theme,
        dynamicColour = theme.dynamicColour,
        content = content
    )
}

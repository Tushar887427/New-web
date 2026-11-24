package com.soloadmin.hub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.soloadmin.hub.data.preferences.ThemeChoice

private val LightScheme = lightColorScheme(
    primary = Forest600,
    onPrimary = Color.White,
    primaryContainer = Forest50,
    onPrimaryContainer = Forest700,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealSoft,
    onSecondaryContainer = Color(0xFF0A4B52),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = Color(0xFF5C3A05),
    error = Rose,
    onError = Color.White,
    errorContainer = RoseSoft,
    onErrorContainer = Color(0xFF5F1410),
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceMutedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFCFA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF4F7F5),
    surfaceContainerHighest = Color(0xFFEDF2EE),
    outline = OutlineLight,
    outlineVariant = Color(0xFFEDF1EE),
    inverseSurface = Color(0xFF1B231E),
    inverseOnSurface = Color(0xFFEDF2EE),
    inversePrimary = Forest200,
    scrim = Color(0xFF0A1510)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FBB93),
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF0B3A2B),
    onPrimaryContainer = Color(0xFFA8DFC8),
    secondary = Color(0xFF57C4CE),
    onSecondary = Color(0xFF05272B),
    secondaryContainer = Color(0xFF0E3B40),
    onSecondaryContainer = Color(0xFFA8E4EA),
    tertiary = Color(0xFFE2A54A),
    onTertiary = Color(0xFF3A2403),
    tertiaryContainer = Color(0xFF3A2C10),
    onTertiaryContainer = Color(0xFFF6D9A6),
    error = Color(0xFFF2A69C),
    onError = Color(0xFF3D0B06),
    errorContainer = Color(0xFF43140F),
    onErrorContainer = Color(0xFFFBD5CF),
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceMutedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = Color(0xFF080B09),
    surfaceContainerLow = Color(0xFF111612),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceElevatedDark,
    surfaceContainerHighest = SurfaceMutedDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF232C26),
    inverseSurface = Color(0xFFECF1EE),
    inverseOnSurface = Color(0xFF1B231E),
    inversePrimary = Forest600,
    scrim = Color.Black
)

/**
 * Extra semantic colours Material 3 has no slot for. Screens read them through [SoloTheme],
 * so light/dark variants are resolved in exactly one place.
 */
@Immutable
data class SoloColors(
    val isDark: Boolean,
    // status
    val present: Color,
    val presentSoft: Color,
    val leave: Color,
    val leaveSoft: Color,
    val absent: Color,
    val absentSoft: Color,
    val holiday: Color,
    val holidaySoft: Color,
    val offday: Color,
    val offdaySoft: Color,
    val duty: Color,
    val dutySoft: Color,
    val late: Color,
    val lateSoft: Color,
    val early: Color,
    val earlySoft: Color,
    val missPunch: Color,
    val missPunchSoft: Color,
    // money
    val positive: Color,
    val positiveSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    // surfaces
    val cardSurface: Color,
    val raisedSurface: Color,
    val subtleSurface: Color,
    val textTertiary: Color,
    // decorative tiles
    val tileMint: Color,
    val tileSand: Color,
    val tileSky: Color,
    val tileLilac: Color,
    val tileClay: Color
)

private val LightExtras = SoloColors(
    isDark = false,
    present = StatusPresent, presentSoft = StatusPresentSoft,
    leave = StatusLeave, leaveSoft = StatusLeaveSoft,
    absent = StatusAbsent, absentSoft = StatusAbsentSoft,
    holiday = StatusHoliday, holidaySoft = StatusHolidaySoft,
    offday = StatusOffday, offdaySoft = StatusOffdaySoft,
    duty = StatusDuty, dutySoft = StatusDutySoft,
    late = StatusLate, lateSoft = StatusLateSoft,
    early = StatusEarly, earlySoft = StatusEarlySoft,
    missPunch = StatusMissPunch, missPunchSoft = StatusMissPunchSoft,
    positive = Color(0xFF0F7A4A), positiveSoft = Color(0xFFE6F5ED),
    warning = Amber, warningSoft = AmberSoft,
    danger = Rose, dangerSoft = RoseSoft,
    cardSurface = SurfaceLight,
    raisedSurface = Color(0xFFFFFFFF),
    subtleSurface = SurfaceMutedLight,
    textTertiary = TextTertiaryLight,
    tileMint = TileMint,
    tileSand = TileSand,
    tileSky = TileSky,
    tileLilac = TileLilac,
    tileClay = TileClay
)

private val DarkExtras = SoloColors(
    isDark = true,
    present = Color(0xFF6ED49D), presentSoft = Color(0xFF14301F),
    leave = Color(0xFFA8B3AC), leaveSoft = Color(0xFF222A25),
    absent = Color(0xFF9D97F0), absentSoft = Color(0xFF1E1B3A),
    holiday = Color(0xFFE9C46A), holidaySoft = Color(0xFF322812),
    offday = Color(0xFF7F8C84), offdaySoft = Color(0xFF1E2621),
    duty = Color(0xFF63C9D3), dutySoft = Color(0xFF0F2C2F),
    late = Color(0xFFF07CA8), lateSoft = Color(0xFF351822),
    early = Color(0xFFD593E0), earlySoft = Color(0xFF301833),
    missPunch = Color(0xFFA9CE5A), missPunchSoft = Color(0xFF232B12),
    positive = Color(0xFF6ED49D), positiveSoft = Color(0xFF14301F),
    warning = Color(0xFFE9B25C), warningSoft = AmberSoftDark,
    danger = Color(0xFFF2A69C), dangerSoft = RoseSoftDark,
    cardSurface = SurfaceDark,
    raisedSurface = SurfaceElevatedDark,
    subtleSurface = SurfaceMutedDark,
    textTertiary = TextTertiaryDark,
    tileMint = TileMintDark,
    tileSand = TileSandDark,
    tileSky = TileSkyDark,
    tileLilac = TileLilacDark,
    tileClay = TileClayDark
)

val LocalSoloColors = staticCompositionLocalOf { LightExtras }

object SoloTheme {
    val colors: SoloColors
        @Composable get() = LocalSoloColors.current
}

@Composable
fun SoloAdminTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    dynamicColour: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeChoice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = if (dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkScheme else LightScheme
    }
    val extras = if (dark) DarkExtras else LightExtras

    CompositionLocalProvider(LocalSoloColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SoloTypography,
            shapes = SoloShapes,
            content = content
        )
    }
}

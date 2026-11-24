package com.soloadmin.hub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Restrained radii. Cards use 18dp (not 28) so the layout reads like a productivity tool rather
 * than a bubble-heavy dashboard, and pills stay fully rounded for buttons and chips only.
 */
val SoloShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object SoloSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    /** Standard horizontal gutter for screen content. */
    val gutter = 16.dp
}

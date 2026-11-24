package com.soloadmin.hub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soloadmin.hub.ui.theme.SoloTheme
import kotlin.math.max

data class ChartSlice(val label: String, val value: Float, val color: Color)

/**
 * Donut chart with a centred total. Drawn with Canvas so there is no extra dependency and it
 * animates cheaply on budget hardware.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    strokeWidth: Dp = 16.dp,
    centerLabel: String? = null,
    centerCaption: String? = null
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val trackColor = SoloTheme.colors.subtleSurface

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (total <= 0f) return@Canvas
            var start = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total) * 360f * progress.value
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep - 1.5f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                start += sweep
            }
        }
        if (centerLabel != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (centerCaption != null) {
                    Text(
                        text = centerCaption,
                        style = MaterialTheme.typography.labelSmall,
                        color = SoloTheme.colors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun ChartLegend(slices: List<ChartSlice>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(slice.color))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = slice.value.toInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Vertical bars with month labels; used for the 6-month finance trend. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int? = null,
    height: Dp = 132.dp
) {
    val maxValue = max(values.maxOrNull() ?: 0f, 1f)
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val trackColor = SoloTheme.colors.subtleSurface
    val mutedColor = MaterialTheme.colorScheme.outlineVariant
    val highlight = SoloTheme.colors.warning

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = minOf(slot * 0.52f, 26.dp.toPx())
            val corner = barWidth / 2f
            values.forEachIndexed { index, value ->
                val ratio = (value / maxValue).coerceIn(0f, 1f) * progress.value
                val barHeight = max(size.height * ratio, if (value > 0f) 6.dp.toPx() else 3.dp.toPx())
                val left = slot * index + (slot - barWidth) / 2f
                val top = size.height - barHeight
                val color = when {
                    highlightIndex == index -> highlight
                    value <= 0f -> mutedColor
                    else -> barColor
                }
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlightIndex == index) SoloTheme.colors.warning else SoloTheme.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** Smooth sparkline of the running balance. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fill: Boolean = true,
    height: Dp = 64.dp
) {
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val minValue = values.minOrNull() ?: 0f
    val maxValue = values.maxOrNull() ?: 1f
    val range = max(maxValue - minValue, 1f)
    val fillColor = lineColor.copy(alpha = 0.18f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (values.size < 2) return@Canvas
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            val normalized = (value - minValue) / range
            Offset(
                x = stepX * index,
                y = size.height - (normalized * (size.height * 0.86f)) - size.height * 0.07f
            )
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                val midX = (previous.x + current.x) / 2f
                cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            }
        }
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent))
            )
        }
        // Draw only up to the animated progress for a subtle reveal.
        drawPath(path, color = lineColor, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(
            color = lineColor,
            radius = 3.2.dp.toPx(),
            center = points.last()
        )
    }
}

/** Slim stacked bar: e.g. present vs duty vs leave for the month. */
@Composable
fun StackedBar(
    segments: List<ChartSlice>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(SoloTheme.colors.subtleSurface)
    ) {
        if (total <= 0f) return@Row
        segments.forEach { segment ->
            if (segment.value <= 0f) return@forEach
            Box(
                modifier = Modifier
                    .weight(segment.value / total)
                    .height(height)
                    .background(segment.color)
            )
        }
    }
}

/** Compact ring used in the header of the attendance screen. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 7.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = SoloTheme.colors.subtleSurface,
    content: (@Composable () -> Unit)? = null
) {
    val animated = remember(progress) { Animatable(0f) }
    LaunchedEffect(progress) {
        animated.animateTo(progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing))
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        content?.invoke()
    }
}

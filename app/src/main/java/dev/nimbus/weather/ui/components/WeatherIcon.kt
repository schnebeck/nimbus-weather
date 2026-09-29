/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/components/WeatherIcon.kt
 * Multicolour weather symbols drawn on a canvas, incl. the combined wind symbol.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Opus 5.5 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.nimbus.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SunYellow = Color(0xFFFFD23F)
private val SunOrange = Color(0xFFFFA91F)
private val MoonColor = Color(0xFFF2EFDF)
private val CloudWhite = Color(0xFFFFFFFF)
private val CloudGrey = Color(0xFFC9D2DC)
private val CloudDark = Color(0xFF8E9AA8)
private val DropBlue = Color(0xFF5DB8FF)
private val BoltYellow = Color(0xFFFFCF33)

/** Multicolour weather glyph, drawn with Canvas. */
@Composable
fun WeatherIcon(
    condition: Condition, isDay: Boolean, size: Dp = 28.dp, modifier: Modifier = Modifier,
    description: String? = null, wind: Float = 0f,
) {
    Canvas(
        modifier.size(size).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        if (wind < 0.05f) {
            drawWeatherGlyph(condition, isDay)
        } else {
            // Combined symbol: weather glyph shrinks to the upper right, wind streaks in front.
            val s = this.size.minDimension
            withTransform({ scale(0.8f, 0.8f, Offset(s, 0f)) }) { drawWeatherGlyph(condition, isDay) }
            drawWindStreaks(s, wind)
        }
    }
}

/** 0..1 how windy it feels, from mean wind and gusts in km/h (0 below ~Bft 4). */
fun windiness(speedKmh: Double?, gustKmh: Double?): Float {
    val a = ((speedKmh ?: 0.0) - 20.0) / 40.0
    val b = ((gustKmh ?: 0.0) - 35.0) / 50.0
    return maxOf(a, b).toFloat().coerceIn(0f, 1f)
}

private val WindColor = Color(0xFFE3ECF6)

/** One to three curled wind streaks in the lower left, more and longer with stronger wind. */
private fun DrawScope.drawWindStreaks(s: Float, wind: Float) {
    val lines = when {
        wind > 0.7f -> 3
        wind > 0.35f -> 2
        else -> 1
    }
    val stroke = s * 0.055f
    for (i in 0 until lines) {
        val y = s * (0.66f + i * 0.12f)
        val len = s * (0.46f + 0.12f * wind - i * 0.08f)
        val x0 = s * 0.04f + i * s * 0.05f
        val path = Path().apply {
            moveTo(x0, y)
            lineTo(x0 + len, y)
            // curl at the end, like the common "wind" symbol
            val r = s * 0.07f
            cubicTo(x0 + len + r * 1.4f, y, x0 + len + r * 1.4f, y - r * 2f, x0 + len + r * 0.2f, y - r * 2f)
        }
        drawPath(path, WindColor, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
    }
}

fun DrawScope.drawWeatherGlyph(condition: Condition, isDay: Boolean) {
    val s = size.minDimension
    val celestial: DrawScope.(Offset, Float) -> Unit = { c, r -> if (isDay) sun(c, r) else moon(c, r) }
    when (condition) {
        Condition.CLEAR -> celestial(Offset(s * 0.5f, s * 0.5f), s * 0.24f)
        Condition.MOSTLY_CLEAR -> {
            celestial(Offset(s * 0.58f, s * 0.4f), s * 0.2f)
            cloud(Offset(s * 0.06f, s * 0.52f), s * 0.52f, CloudWhite)
        }
        Condition.PARTLY_CLOUDY -> {
            celestial(Offset(s * 0.64f, s * 0.34f), s * 0.19f)
            cloud(Offset(s * 0.06f, s * 0.36f), s * 0.8f, CloudWhite)
        }
        Condition.CLOUDY -> {
            cloud(Offset(s * 0.3f, s * 0.14f), s * 0.64f, CloudGrey)
            cloud(Offset(s * 0.04f, s * 0.36f), s * 0.8f, CloudWhite)
        }
        Condition.FOG -> {
            cloud(Offset(s * 0.1f, s * 0.1f), s * 0.8f, CloudGrey)
            for (i in 0 until 3) {
                val y = s * (0.7f + i * 0.11f)
                val inset = if (i == 1) 0.2f else 0.1f
                drawLine(CloudWhite, Offset(s * inset, y), Offset(s * (1f - inset), y), s * 0.06f, StrokeCap.Round)
            }
        }
        Condition.DRIZZLE -> {
            cloud(Offset(s * 0.1f, s * 0.08f), s * 0.8f, CloudWhite)
            drops(s, count = 3, length = 0.07f, width = 0.07f)
        }
        Condition.RAIN -> {
            cloud(Offset(s * 0.1f, s * 0.06f), s * 0.8f, CloudWhite)
            drops(s, count = 3, length = 0.16f, width = 0.065f)
        }
        Condition.HEAVY_RAIN -> {
            cloud(Offset(s * 0.1f, s * 0.04f), s * 0.8f, CloudGrey)
            drops(s, count = 4, length = 0.22f, width = 0.07f)
        }
        Condition.FREEZING_RAIN, Condition.SLEET -> {
            cloud(Offset(s * 0.1f, s * 0.06f), s * 0.8f, CloudWhite)
            drops(s, count = 2, length = 0.14f, width = 0.065f, startX = 0.3f, step = 0.3f)
            flake(Offset(s * 0.45f, s * 0.8f), s * 0.07f)
        }
        Condition.SNOW, Condition.HEAVY_SNOW -> {
            cloud(Offset(s * 0.1f, s * 0.06f), s * 0.8f, if (condition == Condition.HEAVY_SNOW) CloudGrey else CloudWhite)
            val pts = if (condition == Condition.HEAVY_SNOW)
                listOf(0.26f to 0.74f, 0.5f to 0.74f, 0.74f to 0.74f, 0.38f to 0.9f, 0.62f to 0.9f)
            else listOf(0.3f to 0.76f, 0.54f to 0.8f, 0.76f to 0.74f)
            pts.forEach { (x, y) -> flake(Offset(s * x, s * y), s * 0.075f) }
        }
        Condition.SHOWERS -> {
            celestial(Offset(s * 0.66f, s * 0.26f), s * 0.17f)
            cloud(Offset(s * 0.06f, s * 0.24f), s * 0.76f, CloudWhite)
            drops(s, count = 3, length = 0.13f, width = 0.065f, startX = 0.24f, top = 0.74f)
        }
        Condition.THUNDERSTORM -> {
            cloud(Offset(s * 0.1f, s * 0.04f), s * 0.8f, CloudDark)
            drops(s, count = 2, length = 0.13f, width = 0.06f, startX = 0.24f, step = 0.46f)
            bolt(s)
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float) {
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        val d = Offset(cos(a), sin(a))
        drawLine(SunOrange, c + d * (r * 1.3f), c + d * (r * 1.75f), r * 0.24f, StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(SunYellow, SunOrange), c - Offset(r * 0.3f, r * 0.3f), r * 1.6f), r, c)
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val full = Path().apply { addOval(Rect(c, r * 1.15f)) }
    val cut = Path().apply { addOval(Rect(c + Offset(r * 0.62f, -r * 0.42f), r * 1.0f)) }
    val crescent = Path().apply { op(full, cut, PathOperation.Difference) }
    drawPath(crescent, MoonColor)
}

/** Cloud silhouette whose bounding box starts at [topLeft] with the given [width]. */
private fun DrawScope.cloud(topLeft: Offset, width: Float, color: Color) {
    val h = width * 0.6f
    val x = topLeft.x
    val y = topLeft.y
    val shade = Color(
        red = color.red * 0.86f, green = color.green * 0.88f, blue = color.blue * 0.92f, alpha = 1f,
    )
    val brush = Brush.verticalGradient(listOf(color, shade), y, y + h)
    drawRoundRect(brush, Offset(x, y + h * 0.45f), Size(width, h * 0.55f), CornerRadius(h * 0.28f))
    drawCircle(brush, width * 0.22f, Offset(x + width * 0.3f, y + h * 0.56f))
    drawCircle(brush, width * 0.28f, Offset(x + width * 0.58f, y + h * 0.44f))
    drawCircle(brush, width * 0.17f, Offset(x + width * 0.82f, y + h * 0.66f))
}

private fun DrawScope.drops(
    s: Float, count: Int, length: Float, width: Float,
    startX: Float = 0.28f, step: Float = (0.5f / (count - 1).coerceAtLeast(1)), top: Float = 0.7f,
) {
    for (i in 0 until count) {
        val x = s * (startX + i * step)
        val y = s * top + if (i % 2 == 1) s * 0.05f else 0f
        drawLine(DropBlue, Offset(x, y), Offset(x - s * length * 0.35f, y + s * length), s * width, StrokeCap.Round)
    }
}

private fun DrawScope.flake(c: Offset, r: Float) {
    for (i in 0 until 3) {
        val a = i * PI.toFloat() / 3f + PI.toFloat() / 2f
        val d = Offset(cos(a), sin(a)) * r
        drawLine(CloudWhite, c - d, c + d, r * 0.38f, StrokeCap.Round)
    }
}

private fun DrawScope.bolt(s: Float) {
    val p = Path().apply {
        moveTo(s * 0.55f, s * 0.5f)
        lineTo(s * 0.38f, s * 0.75f)
        lineTo(s * 0.5f, s * 0.75f)
        lineTo(s * 0.42f, s * 0.97f)
        lineTo(s * 0.66f, s * 0.66f)
        lineTo(s * 0.53f, s * 0.66f)
        lineTo(s * 0.62f, s * 0.5f)
        close()
    }
    drawPath(p, BoltYellow)
}

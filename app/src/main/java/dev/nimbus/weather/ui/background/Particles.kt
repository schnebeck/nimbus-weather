/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/background/Particles.kt
 * Rain, snow, lightning and shooting stars of the animated sky.
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

package dev.nimbus.weather.ui.background

import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.nimbus.weather.data.model.Condition
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class PrecipSpec(val count: Int, val lengthDp: Float, val speed: Float, val widthDp: Float, val alpha: Float)

internal fun rainSpec(c: Condition): PrecipSpec? = when (c) {
    Condition.DRIZZLE -> PrecipSpec(110, 9f, 0.75f, 1f, 0.35f)
    Condition.SHOWERS -> PrecipSpec(170, 20f, 1.25f, 1.3f, 0.42f)
    Condition.RAIN, Condition.FREEZING_RAIN -> PrecipSpec(220, 22f, 1.35f, 1.3f, 0.45f)
    Condition.SLEET -> PrecipSpec(120, 14f, 1.1f, 1.2f, 0.4f)
    Condition.HEAVY_RAIN -> PrecipSpec(380, 30f, 1.7f, 1.5f, 0.5f)
    Condition.THUNDERSTORM -> PrecipSpec(300, 28f, 1.6f, 1.4f, 0.48f)
    else -> null
}

internal fun snowCount(c: Condition): Int = when (c) {
    Condition.SNOW -> 150
    Condition.HEAVY_SNOW -> 280
    Condition.SLEET -> 60
    else -> 0
}

/** Reused arrays and paints so the particle systems allocate nothing per frame. */
internal class ParticleBuffers {
    val far = FloatArray(4 * 400)
    val near = FloatArray(4 * 400)
    val snow = Array(3) { FloatArray(2 * 300) }
    val linePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
    }
    val pointPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }
}

internal fun DrawScope.drawRain(spec: PrecipSpec, t: Double, w: Float, h: Float, dp: Float, wind: Float, b: ParticleBuffers) {
    val slant = 0.06f + 0.5f * kotlin.math.abs(wind)
    val dirX = if (wind >= 0f) slant else -slant
    var nf = 0
    var nn = 0
    for (i in 0 until spec.count.coerceAtMost(400)) {
        val isNear = i % 3 == 0
        val len = spec.lengthDp * dp * (if (isNear) 1.25f else 0.8f) * (0.8f + 0.4f * rnd(i, 21))
        val v = h * spec.speed * (if (isNear) 1.15f else 0.8f) * (0.85f + 0.3f * rnd(i, 22))
        val total = h + len * 2
        val y = ((rnd(i, 23) * total + t * v).fmod(total.toDouble())).toFloat() - len
        // Drops start further left/right so that slanted rain covers the whole screen.
        val x0 = rnd(i, 24) * (w + h * slant) - (if (dirX > 0) h * slant else 0f)
        val x = x0 + y * dirX
        val arr = if (isNear) b.near else b.far
        val k = if (isNear) nn++ * 4 else nf++ * 4
        arr[k] = x; arr[k + 1] = y; arr[k + 2] = x + len * dirX; arr[k + 3] = y + len
    }
    val base = 0xD7E3F0
    drawIntoCanvas { c ->
        val p = b.linePaint
        p.color = ((spec.alpha * 0.6f * 255).toInt() shl 24) or base
        p.strokeWidth = spec.widthDp * dp * 0.8f
        c.nativeCanvas.drawLines(b.far, 0, nf * 4, p)
        p.color = ((spec.alpha * 255).toInt() shl 24) or base
        p.strokeWidth = spec.widthDp * dp * 1.2f
        c.nativeCanvas.drawLines(b.near, 0, nn * 4, p)
    }
}

internal fun DrawScope.drawSnow(count: Int, t: Double, w: Float, h: Float, dp: Float, wind: Float, b: ParticleBuffers) {
    // Three depth layers, each drawn with one drawPoints call.
    val n = IntArray(3)
    for (i in 0 until count.coerceAtMost(900)) {
        val layer = i % 3
        val depth = (layer + rnd(i, 31)) / 3f
        val r = (1.2f + 2.8f * depth) * dp
        val v = (22f + 55f * depth) * dp
        val total = h + r * 4
        val y = ((rnd(i, 32) * total + t * v).fmod(total.toDouble())).toFloat() - r * 2
        val sway = sin(t * (0.6 + rnd(i, 33)) + i).toFloat() * (8f + 14f * depth) * dp
        val drift = wind * y * (0.35f + 0.5f * (1f - depth))
        val x = ((rnd(i, 34) * w + sway + drift).toDouble().fmod(w.toDouble() + 20 * dp)).toFloat() - 10 * dp
        val arr = b.snow[layer]
        val k = n[layer]++ * 2
        if (k + 1 < arr.size) { arr[k] = x; arr[k + 1] = y } else n[layer]--
    }
    drawIntoCanvas { c ->
        for (layer in 0..2) {
            val depth = (layer + 0.5f) / 3f
            b.pointPaint.strokeWidth = 2 * (1.2f + 2.8f * depth) * dp
            b.pointPaint.alpha = ((0.45f + 0.45f * depth) * 255).toInt()
            c.nativeCanvas.drawPoints(b.snow[layer], 0, n[layer] * 2, b.pointPaint)
        }
    }
}

internal fun DrawScope.drawLightning(t: Double, w: Float, h: Float, dp: Float, bolt: Path) {
    val cycle = 6.5
    val n = floor(t / cycle).toInt()
    val offset = 1.0 + rnd(n, 41) * 4.0
    val local = t - n * cycle - offset
    if (local < 0 || local > 0.7) return
    // Double flash with exponential decay.
    val f1 = if (local < 0.08) local / 0.08 else kotlin.math.exp(-(local - 0.08) * 14)
    val f2 = if (local in 0.14..0.7) 0.8 * kotlin.math.exp(-(local - 0.14) * 9) else 0.0
    val flash = max(f1, f2).toFloat().coerceIn(0f, 1f)
    drawRect(Color(0xFFE6ECFF), alpha = 0.32f * flash)
    if (local < 0.3 && rnd(n, 42) > 0.3f) {
        bolt.reset()
        var x = w * (0.2f + 0.6f * rnd(n, 43))
        var y = h * 0.08f
        bolt.moveTo(x, y)
        var k = 0
        while (y < h * (0.45f + 0.2f * rnd(n, 44))) {
            x += (rnd(n * 97 + k, 45) - 0.5f) * 46f * dp
            y += (14f + 22f * rnd(n * 97 + k, 46)) * dp
            bolt.lineTo(x, y)
            k++
        }
        val a = min(1f, flash * 1.4f)
        drawPath(bolt, Color(0x668FA8FF), alpha = a, style = Stroke(width = 7f * dp, cap = StrokeCap.Round))
        drawPath(bolt, Color.White, alpha = a, style = Stroke(width = 2.2f * dp, cap = StrokeCap.Round))
    }
}

internal fun DrawScope.drawShootingStar(t: Double, w: Float, h: Float, dp: Float, alpha: Float) {
    val cycle = 17.0
    val n = floor(t / cycle).toInt()
    val local = t - n * cycle - rnd(n, 51) * 10
    if (local < 0 || local > 0.9) return
    val p = (local / 0.9).toFloat()
    val start = Offset(w * (0.1f + 0.5f * rnd(n, 52)), h * (0.05f + 0.2f * rnd(n, 53)))
    val dir = Offset(1f, 0.38f)
    val head = start + dir * (p * w * 0.45f)
    val tail = head - dir * (70f * dp)
    val a = alpha * sin(p * PI).toFloat()
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = a)), tail, head),
        tail, head, strokeWidth = 1.6f * dp, cap = StrokeCap.Round,
    )
}

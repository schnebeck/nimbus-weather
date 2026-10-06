/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/background/Clouds.kt
 * The clouds of the animated sky: sprites, their places and colours.
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

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.nimbus.weather.data.model.Condition

/** Soft cumulus sprites rendered once and tinted/scaled while drawing. */
internal object CloudSprites {
    private var cache: List<ImageBitmap>? = null

    fun get(): List<ImageBitmap> = cache ?: (0 until 4).map { make(it) }.also { cache = it }

    const val W = 512
    const val H = 320

    private fun make(seed: Int): ImageBitmap {
        val w = W
        val h = H
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
        val puffs = 10 + seed * 2
        for (i in 0 until puffs) {
            val fx = 0.2f + 0.6f * rnd(i, seed * 31 + 1)
            // Puffs in the middle are taller: gives a cumulus silhouette with a flatter base.
            val centerBias = 1f - kotlin.math.abs(fx - 0.5f) * 1.6f
            var r = (38f + 62f * rnd(i, seed * 31 + 2)) * (0.6f + 0.6f * centerBias)
            val cx = w * fx
            val cy = h * 0.66f - r * 0.5f * (0.4f + centerBias) + 10f * rnd(i, seed * 31 + 3)
            // Keep every gradient inside the bitmap, otherwise edges get cut off hard.
            r = minOf(r, cx - 2f, w - cx - 2f, cy - 2f, h - cy - 2f)
            if (r < 8f) continue
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(0xF2FFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r, paint)
        }
        return bmp.asImageBitmap()
    }
}

internal class CloudSpec(val x: Float, val y: Float, val scale: Float, val speed: Float, val sprite: Int, val alpha: Float, val front: Boolean)

internal fun cloudSpecs(condition: Condition): List<CloudSpec> {
    val count = when (condition) {
        Condition.CLEAR -> 0
        Condition.MOSTLY_CLEAR -> 3
        Condition.PARTLY_CLOUDY -> 6
        Condition.FOG -> 5
        Condition.CLOUDY -> 12
        Condition.DRIZZLE, Condition.SHOWERS -> 10
        Condition.SNOW, Condition.SLEET -> 10
        else -> 14
    }
    val spread = if (condition == Condition.MOSTLY_CLEAR || condition == Condition.PARTLY_CLOUDY) 0.34f else 0.5f
    return (0 until count).map { i ->
        val front = i % 3 == 2
        CloudSpec(
            x = rnd(i, 11),
            y = -0.04f + spread * rnd(i, 12) * (if (front) 1.1f else 0.85f),
            scale = (if (front) 1.3f else 0.85f) + 0.8f * rnd(i, 13),
            speed = (if (front) 9f else 5f) + 6f * rnd(i, 14),
            sprite = (rnd(i, 15) * 4).toInt().coerceIn(0, 3),
            alpha = (if (front) 0.95f else 0.75f) - 0.25f * rnd(i, 16),
            front = front,
        )
    }
}

internal fun cloudColor(condition: Condition, night: Boolean): Color = SkyScene.cloudColor(condition, night)

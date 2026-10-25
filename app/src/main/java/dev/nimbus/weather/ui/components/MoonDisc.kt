/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/components/MoonDisc.kt
 * Draws the moon in its real phase.
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

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the moon with its real phase. [phase]: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter.
 * On the northern hemisphere the waxing moon is lit on the right; [southern] mirrors it.
 *
 * Only the lit part is seen, as in the sky: the dark side shows as faint earthshine, strong at a
 * crescent and gone towards full moon, when the earth seen from the moon is nearly new. The lit
 * part dims towards the shadow line where the sun stands low, so a moon of 90 % still looks cut.
 */
fun DrawScope.drawMoonPhase(center: Offset, r: Float, phase: Float, southern: Boolean, alpha: Float = 1f, darkSide: Color = Color(0x33FFFFFF)) {
    val p = ((phase % 1f) + 1f) % 1f
    val lit = (1f - cos(2 * PI * p).toFloat()) / 2f
    drawCircle(darkSide, r, center, alpha = alpha * (1f - lit))
    val image = MoonImages.get(r, p, southern)
    drawImage(image, Offset(center.x - image.width / 2f, center.y - image.height / 2f), alpha = alpha)
}

/** The lit moon as pictures, kept for the few sizes and phases on screen. */
internal object MoonImages {
    /** Phase steps of a month: one picture lasts some 20 minutes. */
    private const val STEPS = 2000
    private const val KEEP = 8
    private val cache = object : LinkedHashMap<Triple<Int, Int, Boolean>, ImageBitmap>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Int, Int, Boolean>, ImageBitmap>) = size > KEEP
    }

    fun get(r: Float, phase: Float, southern: Boolean): ImageBitmap {
        val n = ceil(2 * r).toInt() + 2
        val step = (phase * STEPS).roundToInt() % STEPS
        return synchronized(cache) {
            cache.getOrPut(Triple(n, step, southern)) {
                val px = pixels(n, r, step.toDouble() / STEPS, southern)
                Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
    }

    /**
     * The lit moon on an [n]·[n] square, centred, radius [r]. Brightness after Lommel-Seeliger,
     * 2·μ0 / (μ0 + μ) with μ0 the sun's and μ the eye's angle cosine: flat at full moon, as the moon
     * shows no darker rim, and falling to nothing at the shadow line. It saturates smoothly, so no
     * seam shows where it reaches full, and greys the moon rather than letting the sky through.
     */
    fun pixels(n: Int, r: Float, phase: Double, southern: Boolean): IntArray {
        val th = 2 * PI * phase
        val sx = sin(th) * (if (southern) -1 else 1)
        val sz = -cos(th)
        val out = IntArray(n * n)
        val half = n / 2.0
        val norm = 1 - exp(-SATURATE)
        for (j in 0 until n) for (i in 0 until n) {
            val dx = i + 0.5 - half
            val dy = j + 0.5 - half
            val d = sqrt(dx * dx + dy * dy)
            val cover = (r - d + 0.5).coerceIn(0.0, 1.0)
            if (cover <= 0.0) continue
            val x = dx / r
            val z = sqrt((1 - (d / r) * (d / r)).coerceAtLeast(0.0))
            val mu0 = x * sx + z * sz
            if (mu0 <= 0) continue
            val ls = 2 * mu0 / (mu0 + z).coerceAtLeast(1e-6)
            val light = ((1 - exp(-SATURATE * ls)) / norm).coerceAtMost(1.0)
            // a soft shadow line a few pixels wide, then grey turning to the full colour
            val a = (255 * cover * (light / EDGE).coerceAtMost(1.0)).roundToInt()
            if (a == 0) continue
            val shade = GREY + (1 - GREY) * light
            // a little warmer and brighter in the middle than at the rim
            val t = (d / r).coerceIn(0.0, 1.0)
            val red = ((0xFB + (0xE4 - 0xFB) * t) * shade).roundToInt()
            val green = ((0xF8 + (0xDF - 0xF8) * t) * shade).roundToInt()
            val blue = ((0xEC + (0xCB - 0xEC) * t) * shade).roundToInt()
            out[j * n + i] = (a shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return out
    }

    /** How quickly the brightness reaches full away from the shadow line. */
    private const val SATURATE = 2.5
    /** The darkest grey of the lit part, at the shadow line, as a share of the full colour. */
    private const val GREY = 0.4
    /** Brightness below which the moon fades into the sky: the softness of the shadow line. */
    private const val EDGE = 0.15
}

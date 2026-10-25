/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/MoonLookTest.kt
 * The moon as seen in the sky: only its lit part, dimmer towards the shadow line, 100 % only when full.
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

package dev.nimbus.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.ui.components.MoonImages
import dev.nimbus.weather.ui.components.drawMoonPhase
import dev.nimbus.weather.util.Moon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.PI
import kotlin.math.acos

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class MoonLookTest {
    @get:Rule val compose = createComposeRule()

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private val n = 101
    private val r = 50f

    /** Alpha of the lit moon at [x], [y] in radii from the centre (x to the right, y down). */
    private fun alpha(px: IntArray, x: Double, y: Double = 0.0): Int {
        val i = (n / 2.0 + x * r).toInt().coerceIn(0, n - 1)
        val j = (n / 2.0 + y * r).toInt().coerceIn(0, n - 1)
        return px[j * n + i] ushr 24
    }

    /** Brightness 0..255 of the lit moon at [x], [y] as it shows on the night sky (red channel times alpha). */
    private fun lum(px: IntArray, x: Double, y: Double = 0.0): Int {
        val i = (n / 2.0 + x * r).toInt().coerceIn(0, n - 1)
        val j = (n / 2.0 + y * r).toInt().coerceIn(0, n - 1)
        val c = px[j * n + i]
        return (c ushr 24) * ((c shr 16) and 0xFF) / 255
    }

    /** The waxing phase at which [lit] of the moon is lit. */
    private fun waxing(lit: Double) = acos(1 - 2 * lit) / (2 * PI)

    /** A moon of 88 % shows only its lit part: no grey disc around it, the earthshine all but gone. */
    @Test fun onlyTheLitPartIsSeen() {
        val px = MoonImages.pixels(n, r, waxing(0.88), southern = false)
        assertEquals(0, alpha(px, -0.95))
        assertEquals(255, alpha(px, 0.9))
        val earthshine = 0x33 * (1 - 0.88)
        assertTrue("$earthshine", earthshine < 7)
        // a thin crescent keeps the dark side faintly in view
        assertTrue(0x33 * (1 - 0.1) > 40)
    }

    /** Where the sun stands low on the moon it is dimmer: towards the shadow line, not at the full moon's rim. */
    @Test fun dimmerTowardsTheShadowLine() {
        val quarter = MoonImages.pixels(n, r, 0.25, southern = false)
        assertTrue(lum(quarter, 0.08) < lum(quarter, 0.25))
        assertTrue(lum(quarter, 0.25) < lum(quarter, 0.6))
        assertEquals(0, alpha(quarter, -0.3))
        // the full moon is as bright at its rim as in its middle, but for the colour's slight fall
        val full = MoonImages.pixels(n, r, 0.5, southern = false)
        assertEquals(255, alpha(full, 0.9))
        assertTrue(lum(full, 0.9) >= 0.88 * lum(full, 0.0))
        assertTrue(lum(full, -0.9) >= 0.88 * lum(full, 0.0))
        // a crescent keeps its bright rim
        val crescent = MoonImages.pixels(n, r, 0.1, southern = false)
        assertTrue("${lum(crescent, 0.95)}", lum(crescent, 0.95) > 200)
        // on the southern hemisphere the waxing moon is lit on the left
        val south = MoonImages.pixels(n, r, 0.25, southern = true)
        assertEquals(lum(quarter, 0.6), lum(south, -0.6))
    }

    /** "100 %" only in the full moon's window: hours before it the moon is at most 99 %. */
    @Test fun aHundredPercentOnlyAtFullMoon() {
        val before = Moon.preciseIllumination(ms("2026-10-25T12:00:00Z"))
        assertTrue("${before.fraction}", before.fraction >= 0.995)
        assertEquals(99, Moon.percent(before))
        assertEquals(100, Moon.percent(Moon.preciseIllumination(ms("2026-10-26T04:12:00Z"))))
        assertEquals(0, Moon.percent(Moon.preciseIllumination(ms("2026-11-09T07:02:00Z"))))
        assertEquals(1, Moon.percent(Moon.Illumination(0.003, 0.02)))
        assertEquals(62, Moon.percent(Moon.Illumination(0.62, 0.36)))
    }

    /** The week before the full moon of 26 Oct. 2026, evenings in Germany. */
    @Test fun theWeekBeforeFullMoon() {
        val days = (19..26).map { ms("2026-10-${it}T19:00:00Z") }
        compose.setContent {
            Column(Modifier.background(Color(0xFF0B1426)).padding(8.dp)) {
                days.chunked(4).forEach { row ->
                    Row {
                        row.forEach { t ->
                            val i = Moon.preciseIllumination(t)
                            Column(Modifier.padding(6.dp)) {
                                Canvas(Modifier.size(84.dp)) { drawMoonPhase(center, size.minDimension / 2 * 0.92f, i.phase.toFloat(), false) }
                                Text("${Instant.ofEpochMilli(t).toString().substring(5, 10)} ${Moon.percent(i)} %", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/moon_week.png")
    }
}

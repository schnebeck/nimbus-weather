/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WindBarbsTest.kt
 * Wind barbs: the strength in strokes, the shaft towards where the wind comes from.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.Meteogram
import dev.nimbus.weather.ui.main.MeteoPoint
import dev.nimbus.weather.ui.radar.WeatherOverlays
import dev.nimbus.weather.util.TimeFormat
import dev.nimbus.weather.util.WindBarbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class WindBarbsTest {
    @get:Rule val compose = createComposeRule()

    private fun kn(k: Double) = k * 1.852

    /** In knots rounded to 5: a half stroke 5, a full one 10, a pennant 50; under 2.5 calm. */
    @Test fun theStrengthInStrokes() {
        assertTrue(WindBarbs.parts(kn(2.4)).calm)
        assertEquals(WindBarbs.Parts(5, 0, 0, true), WindBarbs.parts(kn(4.0)))
        assertEquals(WindBarbs.Parts(10, 0, 1, false), WindBarbs.parts(kn(11.0)))
        assertEquals(WindBarbs.Parts(25, 0, 2, true), WindBarbs.parts(kn(24.0)))
        assertEquals(WindBarbs.Parts(65, 1, 1, true), WindBarbs.parts(kn(64.0)))
        assertEquals(WindBarbs.Parts(100, 2, 0, false), WindBarbs.parts(kn(100.0)))
        // Beaufort 6 begins at 39 km/h: 21 kn – two strokes
        assertEquals(20, WindBarbs.parts(39.0).knots)
    }

    /**
     * The strokes at the shaft's far end (towards where the wind comes from), going right of it;
     * a half stroke alone set back from the end, so that it is not read as a full one.
     */
    @Test fun theStrokesAtTheFarEnd() {
        val s = WindBarbs.shape(kn(25.0))
        assertEquals(WindBarbs.Line(0f, 0f, 0f, -1f), s.shaft)
        assertEquals(3, s.strokes.size)
        assertEquals(-1f, s.strokes[0].y1, 1e-6f)
        assertTrue(s.strokes.all { it.x2 > 0 && it.y2 < it.y1 })
        assertEquals(WindBarbs.STROKE, s.strokes[0].x2, 1e-6f)
        assertEquals(WindBarbs.STROKE / 2, s.strokes[2].x2, 1e-6f)
        val half = WindBarbs.shape(kn(5.0)).strokes.single()
        assertTrue("set back: ${half.y1}", half.y1 > -1f)
        val storm = WindBarbs.shape(kn(60.0))
        assertEquals(1, storm.pennants.size)
        assertEquals(1, storm.strokes.size)
        assertTrue(storm.strokes[0].y1 > storm.pennants[0].maxOf { it.second })
        assertTrue(WindBarbs.shape(1.0).calm)
    }

    /** The radar's barbs: one picture each 5 kn, turned on their middle (the station), the strokes in it. */
    @Test fun theRadarsBarbs() {
        val b = WeatherOverlays.barbBitmap(25)
        assertEquals(64, b.width)
        fun lit(x: Int, y: Int) = android.graphics.Color.alpha(b.getPixel(x, y)) > 0
        assertTrue("the station", lit(32, 31))
        assertTrue("the shaft's end at the top", lit(32, 5))
        assertTrue("a stroke to the right at the top", (36..44).any { x -> (0..12).any { y -> lit(x, y) } })
        assertFalse("nothing left of the shaft", (0..26).any { x -> (0..63).any { y -> lit(x, y) } })
        assertFalse("nothing below the station", (0..63).any { x -> (40..63).any { y -> lit(x, y) } })
        assertEquals(31, (0..WeatherOverlays.BARB_MAX_KN step 5).count())
    }

    /** The day chart: barbs instead of arrows when the setting asks for them – their colour by strength as the arrows'. */
    @Test fun theDayChartWithBarbs() {
        val day = 1_790_812_800_000L
        val h = 3_600_000L
        val pts = (0..25).map { k ->
            MeteoPoint(day + k * h, 10.0, Condition.CLOUDY, k in 7..17, 0.0, precipitationChance = 0.0,
                windSpeed = listOf(3.0, 12.0, 25.0, 45.0, 60.0, 80.0, 100.0, 30.0, 18.0)[k / 3], windDirection = 200.0 + k * 5, sunshine = 0.0)
        }
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings(windBarbs = true), LocalTimeFormat provides TimeFormat("UTC", true)) {
                Box(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { Meteogram(pts, day, day + 24 * h, emptyList(), day + 10 * h) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wind_barbs.png")
    }
}

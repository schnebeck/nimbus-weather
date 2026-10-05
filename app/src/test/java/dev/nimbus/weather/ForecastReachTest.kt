/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ForecastReachTest.kt
 * "Könnte man eigentlich die Vorschau beim niederschlagsradar abschalten, wenn das Feature in den
 * angezeigten Radardaten nicht vorhanden ist?": without a nowcast in the area the loop ends at now,
 * the forecast cannot be chosen.
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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import dev.nimbus.weather.ui.radar.HistoryRange
import dev.nimbus.weather.ui.radar.Playback
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarTimeline
import dev.nimbus.weather.ui.radar.TimelineSlider
import dev.nimbus.weather.ui.radar.lastShown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ForecastReachTest {
    @get:Rule val compose = createComposeRule()

    private val step = 5 * 60_000L
    private val latest = 1_791_219_000_000L / step * step
    /** Two hours back, two ahead (the DWD's nowcast): "now" is step 24 of 48. */
    private val tl = RadarTimeline((-24..24).map { k -> latest + k * step }.map { t -> RadarFrame(t, t > latest, if (t > latest) latest else null, null) }, 24, "", HistoryRange.H2)

    @Test fun theLastStepToShow() {
        assertEquals(48, tl.lastShown(nowcastHere = true))
        assertEquals(24, tl.lastShown(nowcastHere = false))
        // a past day has no forecast part: all of it
        val day = tl.copy(day = latest - 24 * 3_600_000L)
        assertEquals(48, day.lastShown(nowcastHere = false))
    }

    /** Playing, the loop turns at "now" – it never runs into the empty future. */
    @Test fun theLoopTurnsAtNow() {
        var s = Playback.State(20f)
        var furthest = 0f
        repeat(400) {
            s = Playback.step(s, 16f, tl.lastShown(false), loop = true, stepMs = 100f, canShow = { true }, resumeAhead = 2, restMs = 200f)
            furthest = maxOf(furthest, s.position)
        }
        assertTrue("played to $furthest", furthest <= 24f + 1e-3f)
    }

    /** Dragged to the end, the slider stops at "now". */
    @Test fun theSliderStopsAtNow() {
        val chosen = mutableListOf<Int>()
        compose.setContent { TimelineSlider(tl, 20, tl.lastShown(false)) { chosen += it } }
        compose.onRoot().performTouchInput { swipeRight(startX = centerX, endX = right) }
        compose.waitForIdle()
        assertTrue("nothing chosen", chosen.isNotEmpty())
        assertEquals(24, chosen.max())
    }
}

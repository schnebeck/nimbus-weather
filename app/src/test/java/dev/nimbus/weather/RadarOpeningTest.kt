/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarOpeningTest.kt
 * The live radar opening: now first and paused, then the forecast, then the past; playing by
 * itself once the forecast is loaded, the loop from now until the past can play; the setting.
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.radar.Playback
import dev.nimbus.weather.ui.radar.Progressive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The 2-hour loop: 24 steps of the past, now at 24, 24 steps of the nowcast (49 in all). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w411dp-h4000dp", application = android.app.Application::class)
class RadarOpeningTest {
    @get:Rule val compose = createComposeRule()
    private val now = 24
    private val last = 48

    @Test fun nowThenTheForecastThenThePast() {
        val o = Progressive.liveOrder(49, now)
        assertEquals(49, o.size); assertEquals(49, o.toSet().size)
        assertEquals(now, o.first())
        // the forecast in playback order
        assertEquals((now + 1..last).toList(), o.subList(1, 25))
        // the past from now backwards
        assertEquals((now - 1 downTo 0).toList(), o.drop(25))
    }

    /** A long loop: of the past, the steps it plays between come before the others. */
    @Test fun ofThePastTheStepsTheLoopPlaysFirst() {
        val plays = setOf(0, 4, 8, 12, 16, 20)
        val o = Progressive.liveOrder(49, now, plays)
        assertEquals((now..last).toList(), o.take(25))
        assertEquals(listOf(20, 16, 12, 8, 4, 0), o.subList(25, 31))
        assertEquals((now - 1 downTo 0).filter { it !in plays }, o.drop(31))
    }

    /** Stands on now while the forecast loads; plays once it is all there – the past not yet. */
    @Test fun playsOnceTheForecastIsLoaded() {
        var loaded = setOf(now)
        fun canShow(p: Float) = Progressive.playable(p, 49, loaded::contains) { it * 5 * 60_000L }
        assertFalse(Playback.liveStarts(now, last, ::canShow))
        loaded = (now..last - 1).toSet()
        assertFalse("one step of the forecast missing", Playback.liveStarts(now, last, ::canShow))
        loaded = (now..last).toSet()
        assertTrue(Playback.liveStarts(now, last, ::canShow))
    }

    /** Where the area has no nowcast, the loop is the past: it plays once that is loaded. */
    @Test fun withoutAForecastOnceThePastIsLoaded() {
        var loaded = setOf(now)
        fun canShow(p: Float) = Progressive.playable(p, 49, loaded::contains) { it * 5 * 60_000L }
        assertFalse(Playback.liveStarts(now, now, ::canShow))
        loaded = (0..now).toSet()
        assertTrue(Playback.liveStarts(now, now, ::canShow))
    }

    /** The loop starts over at now while the past loads, at its start once the past can play. */
    @Test fun theLoopFromNowUntilThePastIsThere() {
        var loaded = (now..last).toSet()
        fun canShow(p: Float) = Progressive.playable(p, 49, loaded::contains) { it * 5 * 60_000L }
        assertEquals(now, Playback.liveLoopFrom(now, last, ::canShow))
        var s = Playback.State(last.toFloat())
        repeat(200) { s = Playback.step(s, 16f, last, loop = true, stepMs = 100f, canShow = ::canShow, loopFrom = Playback.liveLoopFrom(now, last, ::canShow)) }
        assertTrue("starts over at now, not in the past: ${s.position}", s.position >= now)
        loaded = (0..last).toSet()
        assertEquals(0, Playback.liveLoopFrom(now, last, ::canShow))
        // no forecast: the loop is the past, from its start
        assertEquals(0, Playback.liveLoopFrom(now, now, ::canShow))
    }

    /** "Play the radar automatically": on by default, kept, in the radar's group of the settings. */
    @Test fun theSetting() {
        assertTrue(Settings().radarAutoplay)
        assertTrue(JsonCodec.decodeFromString(Settings.serializer(), "{}").radarAutoplay)
        val off = JsonCodec.encodeToString(Settings.serializer(), Settings(radarAutoplay = false))
        assertFalse(JsonCodec.decodeFromString(Settings.serializer(), off).radarAutoplay)
        var settings by mutableStateOf(Settings())
        compose.setContent { dev.nimbus.weather.ui.settings.SettingsScreen(settings, { settings = it(settings) }, {}, {}) }
        compose.waitForIdle()
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("Radar automatisch abspielen") in top("RADAR")..top("BADEGEWÄSSER"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Radar automatisch abspielen"))
        compose.onNodeWithText("Radar automatisch abspielen").performClick()
        compose.waitForIdle()
        assertFalse(settings.radarAutoplay)
    }
}

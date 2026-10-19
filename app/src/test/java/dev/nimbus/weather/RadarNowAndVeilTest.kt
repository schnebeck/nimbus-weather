/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarNowAndVeilTest.kt
 * The live loop stands on "now" each time it comes to it; the steps of the forecast are grey
 * beyond the nowcast's area – no forecast there, not dry.
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

import dev.nimbus.weather.ui.radar.Playback
import dev.nimbus.weather.ui.radar.RadarField
import dev.nimbus.weather.ui.radar.ViewFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarNowAndVeilTest {
    private val now = 24
    private val last = 48

    /** Plays [ms] in display frames of 16 ms from [s]; the positions it showed. */
    private fun play(s: Playback.State, ms: Int, loopFrom: Int = 0): Pair<Playback.State, List<Float>> {
        var st = s
        val seen = mutableListOf<Float>()
        repeat(ms / 16) {
            st = Playback.step(st, 16f, last, loop = true, stepMs = 100f, canShow = { true }, loopFrom = loopFrom, holdAt = now, holdMs = 2000f)
            seen += st.position
        }
        return st to seen
    }

    /** Starting on now: two seconds of the current picture, then on into the forecast. */
    @Test fun theLoopStandsOnNowFirst() {
        val (s, seen) = play(Playback.State(now.toFloat()), 1900)
        assertTrue("still on now after 1.9 s", seen.all { it == now.toFloat() } && s.position == now.toFloat())
        val (after, _) = play(s, 400)
        assertTrue("moved on after 2 s: ${after.position}", after.position > now)
    }

    /** Coming from the past: it stops right on now and stands there, every time round. */
    @Test fun everyTimeRoundTheLoopStandsOnNow() {
        val (_, seen) = play(Playback.State(now - 3f), 600)
        val onNow = seen.count { it == now.toFloat() }
        assertTrue("stood on now ${onNow * 16} ms", onNow * 16 in 1900..2100 || seen.last() == now.toFloat())
        // round the loop (from the past's start) and back to now: it stands there again
        val (_, round) = play(Playback.State(now - 3f), 30_000)
        val stands = round.zipWithNext().count { (a, b) -> a != now.toFloat() && b == now.toFloat() }
        assertTrue("came to now and stood $stands times", stands >= 2)
        assertTrue(round.count { it == now.toFloat() } * 16 >= 2 * 1900)
    }

    /** Beyond the nowcast a step of the forecast is grey, inside it dry is clear; rain stays rain. */
    @Test fun noForecastIsGreyNotDry() {
        val w = 40; val h = 20
        val dbz = ByteArray(w * h); val wet = ByteArray(w * h)
        // rain in the nowcast's half (left)
        for (y in 5..10) for (x in 5..10) { dbz[y * w + x] = 60.toByte(); wet[y * w + x] = 255.toByte() }
        val frame = ViewFrame(dbz, wet, null)
        val beyond = BooleanArray(w * h) { it % w >= w / 2 }
        val out = IntArray(w * h)
        RadarField.render(frame, null, null, 0f, w, h, null, out, veil = beyond)
        assertEquals("beyond the nowcast: grey", RadarField.VEIL, out[15 * w + 30])
        assertEquals("dry inside it: clear", 0, out[15 * w + 2])
        assertTrue("rain drawn", (out[7 * w + 7] ushr 24) > 200 && out[7 * w + 7] != RadarField.VEIL)
        // a step of the past: no grey anywhere
        RadarField.render(frame, null, null, 0f, w, h, null, out)
        assertEquals(0, out[15 * w + 30])
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PlaybackTest.kt
 * Radar playback never just stands still: it moves, buffers with the hint, or loops.
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTest {
    private val frame = 16f      // ms per display frame

    /** Runs [frames] display frames; every frame must move, buffer or rest – never stand still unseen. */
    private fun run(
        start: Playback.State, frames: Int, last: Int, loop: Boolean, canShow: (Float) -> Boolean, onFrame: (Playback.State) -> Unit = {},
    ): Playback.State {
        var s = start
        repeat(frames) {
            val n = Playback.step(s, frame, last, loop, stepMs = 100f, canShow = canShow)
            val moved = n.position != s.position
            val visible = n.buffering || n.restedMs > 0f || !n.playing
            assertTrue("standing still without the loading hint at ${s.position}", moved || visible)
            s = n; onFrame(s)
        }
        return s
    }

    @Test fun liveLoopRunsToTheEndAndStartsOver() {
        // 6 hours: 97 steps, all there
        var wrapped = false
        run(Playback.State(72f), 2000, 96, loop = true, canShow = { true }) { if (it.position < 1f) wrapped = true }
        assertTrue("the loop starts over", wrapped)
    }

    @Test fun missingFramesBufferInsteadOfFreezing() {
        // the window of a long time line: only steps 60..96 are ready while the position is at the end,
        // the start is still being prepared after the jump back
        var ready = 60..96
        val s = run(Playback.State(90f), 600, 96, loop = true, canShow = { it.toInt() in ready && it <= ready.last })
        assertTrue("waits at the start with the hint", s.buffering && s.position == 0f)
        // the start arrives: playback goes on
        ready = 0..96
        val after = run(s, 100, 96, loop = true, canShow = { it.toInt() in ready })
        assertFalse(after.buffering)
        assertTrue(after.position > 0f)
    }

    @Test fun anArchivedDayStopsAtTheEnd() {
        val s = run(Playback.State(280f), 1000, 287, loop = false, canShow = { true })
        assertFalse(s.playing)
        assertEquals(287f, s.position)
    }

    @Test fun bufferingResumesOnlyWithAStretchAhead() {
        var ready = 0..20
        val s = run(Playback.State(10f), 400, 287, loop = false, canShow = { it.toInt() in ready && it <= ready.last })
        assertTrue(s.buffering)
        ready = 0..25          // a few more: not yet the 12 ahead
        val still = Playback.step(s, frame, 287, false, 100f, { it.toInt() in ready && it <= ready.last })
        assertTrue(still.buffering)
        ready = 0..40
        assertFalse(Playback.step(s, frame, 287, false, 100f, { it.toInt() in ready && it <= ready.last }).buffering)
    }

    @Test fun aSlowDisplayFrameDoesNotSlowThePlayback() {
        // 24 hours play 37.5 ms per step: a frame of 200 ms moves on about five steps, not one
        val s = Playback.step(Playback.State(10f), 200f, 312, loop = true, stepMs = 37.5f, canShow = { true })
        assertEquals(15.33f, s.position, 0.01f)
        // but never past a step that cannot be shown
        val held = Playback.step(Playback.State(10f), 200f, 312, loop = true, stepMs = 37.5f, canShow = { it < 13f })
        assertEquals(12f, held.position, 0.01f)
        assertTrue(held.buffering)
    }
}

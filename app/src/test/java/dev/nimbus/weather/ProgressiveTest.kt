/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ProgressiveTest.kt
 * Coarse-to-fine loading of the radar time line and the steps shown in between.
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

import dev.nimbus.weather.ui.radar.Progressive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveTest {
    @Test fun levelsPerStep() {
        assertArrayEquals(intArrayOf(24, 12, 6, 3, 1), Progressive.strides(5))  // archived day
        assertArrayEquals(intArrayOf(12, 6, 3, 1), Progressive.strides(10))     // live 2 h
        assertArrayEquals(intArrayOf(6, 3, 1), Progressive.strides(20))
        assertArrayEquals(intArrayOf(2, 1), Progressive.strides(60))
    }

    @Test fun aDayLoadsAnOverviewFirstThenInPlaybackOrder() {
        val o = Progressive.order(288, 0, Progressive.strides(5))
        assertEquals(288, o.size); assertEquals(288, o.toSet().size)
        // the first half hour (00:00–00:30), then every 2 hours and the end of the day, then the other hours
        assertEquals((0..6).toList(), o.take(7))
        assertEquals((24 until 288 step 24).toSet() + 287, o.drop(7).take(12).toSet())
        assertEquals((12 until 288 step 24).toSet(), o.drop(19).take(12).toSet())
        // then everything else in playback order
        val rest = o.drop(31)
        assertEquals(rest.sorted(), rest)
    }

    @Test fun afterTheOverviewAheadOfThePositionFirst() {
        val o = Progressive.order(288, 120, Progressive.strides(5))
        assertEquals((120..126).toList(), o.take(7))
        val rest = o.drop(7 + 24)               // the overview without 120, which came first
        // after the overview the steps right after the start come first, those behind it last
        assertEquals(127, rest.first())
        assertTrue(rest.indexOf(119) > rest.indexOf(287))
    }

    @Test fun rainMovesOnlyAcrossShortGaps() {
        val min = 60_000L
        assertEquals(true to 0.3f, Progressive.blend(30 * min, 0.3f))
        // an hour apart: the earlier frame, a short blend in the middle, then the later one
        assertEquals(false to 0f, Progressive.blend(60 * min, 0.3f))
        assertEquals(false, Progressive.blend(60 * min, 0.5f).first)
        assertEquals(0.5f, Progressive.blend(60 * min, 0.5f).second, 1e-4f)
        assertEquals(false to 1f, Progressive.blend(60 * min, 0.7f))
    }

    @Test fun playsOnlyWhereTheFramesAreCloseEnough() {
        val time = { i: Int -> i * 5 * 60_000L }
        // steps every 30 min loaded: playable; only every hour: not (it would only blend)
        assertTrue(Progressive.playable(3.5f, 288, { it % 6 == 0 }, time))
        assertFalse(Progressive.playable(3.5f, 288, { it % 12 == 0 }, time))
        assertTrue(Progressive.playable(12f, 288, { it % 12 == 0 }, time))      // right on a frame
    }

    @Test fun shownBetweenTheNearestStepsThereAre() {
        val has = setOf(0, 12, 24)
        assertEquals(0 to 12, Progressive.bracket(5.5f, 288, has::contains))
        assertEquals(12 to 12, Progressive.bracket(12f, 288, has::contains))
        assertEquals(12 to 24, Progressive.bracket(12.5f, 288, has::contains))
        assertEquals(24 to 24, Progressive.bracket(30f, 288, has::contains))     // past the last loaded: hold it
        assertNull(Progressive.bracket(3f, 288, setOf(12)::contains))
    }

    @Test fun aLongLiveLoopKeepsAStepEvery20To30MinutesByTheClock() {
        val m5 = 5 * 60_000L
        val t0 = 1_790_900_000_000L / m5 * m5 + m5          // not on a full hour
        // 6 hours: 97 steps of 5 min, played 20 min per beat – every 20 min by the clock
        val six = (0 until 97).map { t0 + it * m5 }
        val keep6 = Progressive.keepSteps(six, 20)
        assertTrue(keep6.all { it == 0 || it == 96 || six[it] % (20 * 60_000L) == 0L })
        assertTrue(keep6.sorted().zipWithNext().all { (a, b) -> (b - a) * 5 <= 20 })
        // 24 hours, played 60 min per beat: every 30 min at most (motion is only found across 30 min)
        val day = (0 until 313).map { t0 + it * m5 }
        val keep24 = Progressive.keepSteps(day, 60)
        assertTrue(keep24.sorted().zipWithNext().all { (a, b) -> (b - a) * 5 <= 30 })
        assertTrue((0 until 312).all { Progressive.playable(it + 0.5f, 313, keep24::contains, { i -> day[i] }) })
        // a refreshed time line, one step later: the same moments kept
        val later = day.drop(1) + (day.last() + m5)
        val k1 = keep24.map { day[it] }.toSet(); val k2 = Progressive.keepSteps(later, 60).map { later[it] }.toSet()
        assertTrue((k1 intersect k2).size >= k1.size - 3)
    }
}

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
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressiveTest {
    @Test fun levelsPerStep() {
        assertArrayEquals(intArrayOf(24, 12, 6, 3, 1), Progressive.strides(5))  // archived day
        assertArrayEquals(intArrayOf(12, 6, 3, 1), Progressive.strides(10))     // live 2 h
        assertArrayEquals(intArrayOf(6, 3, 1), Progressive.strides(20))
        assertArrayEquals(intArrayOf(2, 1), Progressive.strides(60))
    }

    @Test fun aDayLoadsEveryTwoHoursFirstThenFiner() {
        val o = Progressive.order(288, 0, Progressive.strides(5))
        assertEquals(288, o.size); assertEquals(288, o.toSet().size)
        // first every 2 hours and the end of the day (13 steps)
        assertEquals((0 until 288 step 24).toSet() + 287, o.take(13).toSet())
        // then the other hours, the half hours, the quarters, finally the rest
        assertEquals((12 until 288 step 24).toSet(), o.drop(13).take(12).toSet())
        assertEquals((6 until 288 step 12).toSet(), o.drop(25).take(24).toSet())
        assertEquals((3 until 288 step 6).toSet(), o.drop(49).take(48).toSet())
    }

    @Test fun withinALevelAheadOfThePositionFirst() {
        val o = Progressive.order(288, 120, Progressive.strides(5))
        assertEquals(120, o[0]); assertEquals(144, o[1]); assertEquals(168, o[2])
        // within a level the steps behind come after the ones close ahead
        assertEquals(true, o.indexOf(96) > o.indexOf(168))
    }

    @Test fun shownBetweenTheNearestStepsThereAre() {
        val has = setOf(0, 12, 24)
        assertEquals(0 to 12, Progressive.bracket(5.5f, 288, has::contains))
        assertEquals(12 to 12, Progressive.bracket(12f, 288, has::contains))
        assertEquals(12 to 24, Progressive.bracket(12.5f, 288, has::contains))
        assertEquals(24 to 24, Progressive.bracket(30f, 288, has::contains))     // past the last loaded: hold it
        assertNull(Progressive.bracket(3f, 288, setOf(12)::contains))
    }
}

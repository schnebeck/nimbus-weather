/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SkyCourseTest.kt
 * The sun's and the moon's direction over the day: when they stand in each of the eight
 * directions, where they rise and set, which labels the axis has room for.
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

import dev.nimbus.weather.util.Moon
import dev.nimbus.weather.util.SkyCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class SkyCourseTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val lat = 52.3759
    private val lon = 9.7320
    private val min = 60_000L
    private fun start(d: String) = LocalDate.parse(d).atStartOfDay(berlin).toInstant().toEpochMilli()
    private fun end(d: String) = LocalDate.parse(d).plusDays(1).atStartOfDay(berlin).toInstant().toEpochMilli()
    private fun local(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(berlin).toLocalTime()

    /** Hannover, a day in autumn: the sun passes all eight directions once, N at night, S at its highest. */
    @Test fun theSunPassesEveryDirectionOnce() {
        val course = SkyCourse.sun(start("2026-10-26"), end("2026-10-26"), lat, lon)
        val c = SkyCourse.crossings(course)
        // north lies near midnight (about 00:05 that day), so the round may begin with it or end with it
        assertEquals((0..7).toList(), c.map { it.direction }.sorted())
        assertTrue(c.zipWithNext().all { (a, b) -> b.direction == (a.direction + 1) % 8 })
        val noon = course.maxBy { it.altitude }
        val south = c.single { it.direction == 4 }
        assertTrue("S at ${local(south.time)}, highest at ${local(noon.time)}", abs(south.time - noon.time) <= 5 * min)
        // after the clocks went back: the highest sun about 12:05, north at night below the horizon
        assertEquals(12, local(south.time).hour)
        assertTrue(c.single { it.direction == 0 }.altitude < 0)
        // east before its rise in autumn, west after its set: both below the horizon
        assertTrue(c.single { it.direction == 2 }.altitude < 0 && c.single { it.direction == 6 }.altitude < 0)
    }

    /** Where the sun rises: north-east at midsummer, south-east at midwinter, east at the equinox. */
    @Test fun theSunRisesWhereTheSeasonSays() {
        fun riseDir(d: String) = SkyCourse.index16(Moon.sunAzimuth(Moon.sunTimes(start(d), lat, lon).first!!, lat, lon))
        assertEquals(2, riseDir("2026-06-21"))        // NE
        assertEquals(6, riseDir("2026-12-21"))        // SE
        assertEquals(4, riseDir("2026-03-20"))        // E
    }

    /** The moon at its highest stands in the south, too – its own course, about 50 minutes later each day. */
    @Test fun theMoonIsHighestInTheSouth() {
        for (d in listOf("2026-10-19", "2026-10-22", "2026-11-02")) {
            val course = SkyCourse.moon(start(d), end(d), lat, lon)
            val south = SkyCourse.crossings(course).filter { it.direction == 4 }
            assertEquals(d, 1, south.size)
            val around = course.filter { abs(it.time - south[0].time) <= 60 * min }
            assertTrue("$d: S at ${local(south[0].time)}", around.all { it.altitude <= south[0].altitude + 0.2 })
        }
        // about 50 minutes later from one day to the next
        val a = SkyCourse.crossings(SkyCourse.moon(start("2026-10-19"), end("2026-10-19"), lat, lon)).single { it.direction == 4 }
        val b = SkyCourse.crossings(SkyCourse.moon(start("2026-10-20"), end("2026-10-20"), lat, lon)).single { it.direction == 4 }
        val later = (b.time - a.time - 24 * 60 * min) / min
        assertTrue("$later min", later in 35..65)
    }

    /** Across north either way round: once, at the time the direction gets there. */
    @Test fun northIsFoundAcrossTheTurn() {
        val t = 1_000_000L
        val clockwise = listOf(SkyCourse.Point(t, -10.0, 350.0), SkyCourse.Point(t + 20 * min, -9.0, 10.0))
        assertEquals(listOf(SkyCourse.Crossing(t + 10 * min, 0, -9.5)), SkyCourse.crossings(clockwise))
        val back = listOf(SkyCourse.Point(t, 5.0, 40.0), SkyCourse.Point(t + 10 * min, 5.0, 50.0), SkyCourse.Point(t + 20 * min, 5.0, 40.0))
        assertEquals(listOf(1, 1), SkyCourse.crossings(back).map { it.direction })
    }

    /** Too little room: the direction between two main ones gives way, the main ones stay. */
    @Test fun theMainDirectionsKeepTheirPlace() {
        val c = listOf(SkyCourse.Crossing(0, 2, 0.0), SkyCourse.Crossing(10, 3, 0.0), SkyCourse.Crossing(20, 4, 0.0), SkyCourse.Crossing(60, 5, 0.0))
        val kept = SkyCourse.spread(c, { it.main }, { it.time.toFloat() }, { 8f }, 4f)
        assertEquals(listOf(2, 4, 5), kept.map { it.direction })
    }
}

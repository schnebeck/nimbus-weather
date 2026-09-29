/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SunPhasesTest.kt
 * Tests for the light phases of the day.
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
import dev.nimbus.weather.util.SunPhases
import dev.nimbus.weather.util.SunPhases.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SunPhasesTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun curve(date: String, lat: Double, lon: Double): List<Pair<Long, Double>> {
        val start = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        return (0..288).map { val t = start + it * 300_000L; t to Moon.sunAltitude(t, lat, lon) }
    }

    @Test fun phaseBoundaries() {
        assertEquals(Phase.DAY, SunPhases.phaseOf(6.5))
        assertEquals(Phase.GOLDEN, SunPhases.phaseOf(6.0))
        assertEquals(Phase.GOLDEN, SunPhases.phaseOf(-4.0))
        assertEquals(Phase.BLUE, SunPhases.phaseOf(-4.1))
        assertEquals(Phase.BLUE, SunPhases.phaseOf(-8.0))
        assertEquals(Phase.NIGHT, SunPhases.phaseOf(-8.1))
    }

    @Test fun autumnDayInHannoverHasAllPhasesInOrder() {
        val spans = SunPhases.spans(curve("2026-09-29", 52.37, 9.73))
        assertEquals(
            listOf(Phase.NIGHT, Phase.BLUE, Phase.GOLDEN, Phase.DAY, Phase.GOLDEN, Phase.BLUE, Phase.NIGHT),
            spans.map { it.phase },
        )
        assertEquals(listOf(true, true, true), spans.take(3).map { it.morning })
        assertEquals(listOf(false, false, false), spans.takeLast(3).map { it.morning })
        // Spans are contiguous
        spans.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        // Evening golden hour contains the sunset (~19:03) and lasts roughly an hour
        val evening = spans[4]
        val set = Instant.ofEpochMilli(Moon.sunTimes(LocalDate.parse("2026-09-29").atStartOfDay(zone).toInstant().toEpochMilli(), 52.37, 9.73).second!!)
        assertTrue(evening.start < set.toEpochMilli() && set.toEpochMilli() < evening.end)
        val minutes = (evening.end - evening.start) / 60_000
        assertTrue("golden hour $minutes min", minutes in 50..90)
        val blue = (spans[5].end - spans[5].start) / 60_000
        assertTrue("blue hour $blue min", blue in 20..40)
    }

    @Test fun midsummerInNorthernNorwayHasNoNight() {
        val spans = SunPhases.spans(curve("2026-06-21", 69.65, 18.96))
        assertTrue(spans.none { it.phase == Phase.NIGHT || it.phase == Phase.BLUE })
    }

    @Test fun maxAltitude() {
        assertEquals(61.07, SunPhases.maxAltitude(52.37), 0.01)
        assertEquals(90.0, SunPhases.maxAltitude(10.0), 0.0)
    }
}

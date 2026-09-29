/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/MoonTest.kt
 * Tests for the moon phase and moon times.
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MoonTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `known full and new moons`() {
        // Full moon 2024-01-25 17:54 UTC, new moon 2024-01-11 11:57 UTC
        val full = Moon.illumination(ms("2024-01-25T17:54:00Z"))
        assertTrue("full fraction ${full.fraction}", full.fraction > 0.99)
        assertEquals(Moon.Phase.FULL, Moon.phaseOf(full.phase))
        val new = Moon.illumination(ms("2024-01-11T11:57:00Z"))
        assertTrue("new fraction ${new.fraction}", new.fraction < 0.01)
        assertEquals(Moon.Phase.NEW, Moon.phaseOf(new.phase))
        // First quarter 2024-01-18 03:53 UTC
        assertEquals(Moon.Phase.FIRST_QUARTER, Moon.phaseOf(Moon.illumination(ms("2024-01-18T03:53:00Z")).phase))
    }

    @Test
    fun `next full moon is found within a few hours`() {
        val next = Moon.nextFullMoon(ms("2024-01-12T00:00:00Z"))
        val expected = ms("2024-01-25T17:54:00Z")
        assertTrue("off by ${(next - expected) / 60000} min", kotlin.math.abs(next - expected) < 5 * 60_000L)
        // another one: USNO full moon 2026-10-26 04:12 UTC
        val oct = Moon.nextFullMoon(ms("2026-10-01T00:00:00Z"))
        assertTrue("oct off by ${(oct - ms("2026-10-26T04:12:00Z")) / 60000} min", kotlin.math.abs(oct - ms("2026-10-26T04:12:00Z")) < 5 * 60_000L)
    }

    @Test
    fun `precise phase names at USNO event times`() {
        assertEquals(Moon.Phase.FULL, Moon.phaseOf(Moon.preciseIllumination(ms("2024-01-25T17:54:00Z")).phase))
        assertEquals(Moon.Phase.NEW, Moon.phaseOf(Moon.preciseIllumination(ms("2024-01-11T11:57:00Z")).phase))
        assertEquals(Moon.Phase.FIRST_QUARTER, Moon.phaseOf(Moon.preciseIllumination(ms("2024-01-18T03:52:00Z")).phase))
        assertEquals(Moon.Phase.LAST_QUARTER, Moon.phaseOf(Moon.preciseIllumination(ms("2024-02-02T23:18:00Z")).phase))
        assertEquals(Moon.Phase.WAXING_GIBBOUS, Moon.phaseOf(Moon.preciseIllumination(ms("2024-01-22T00:00:00Z")).phase))
    }

    @Test
    fun `moonrise and moonset in Berlin match USNO`() {
        // US Naval Observatory for 2024-01-25, Berlin (UTC+1): moonset 08:32, moonrise 15:57
        val zone = ZoneId.of("Europe/Berlin")
        val start = LocalDate.of(2024, 1, 25).atStartOfDay(zone).toInstant().toEpochMilli()
        val t = Moon.times(start, 52.52, 13.405)
        val rise = assertNotNull(t.rise).let { t.rise!! }
        val expectedRise = LocalDate.of(2024, 1, 25).atTime(15, 57).atZone(zone).toInstant().toEpochMilli()
        val expectedSet = LocalDate.of(2024, 1, 25).atTime(8, 32).atZone(zone).toInstant().toEpochMilli()
        assertTrue("rise off by ${(rise - expectedRise) / 60000} min", kotlin.math.abs(rise - expectedRise) <= 10 * 60_000L)
        assertTrue("set off by ${(t.set!! - expectedSet) / 60000} min", kotlin.math.abs(t.set!! - expectedSet) <= 10 * 60_000L)
    }
}

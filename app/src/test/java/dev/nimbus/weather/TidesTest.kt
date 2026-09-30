/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/TidesTest.kt
 * Tests for the tide prediction from gauge measurements.
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

import dev.nimbus.weather.util.Tides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class TidesTest {
    @Test fun recoversASyntheticSemidiurnalTide() {
        // Pure M2 tide, 150 cm amplitude around 500 cm, sampled every 10 minutes for 30 days.
        val m2 = Math.toRadians(28.9841042)
        val times = LongArray(30 * 144) { it * 600_000L }
        val values = DoubleArray(times.size) { 500 + 150 * cos(m2 * times[it] / 3_600_000.0) }
        val model = Tides.fit(times, values, now = 0L)!!
        assertEquals(500.0, model.mean, 0.5)
        assertTrue(model.rms < 0.5)
        val next = Tides.extremes(model, 31L * 24 * 3_600_000, 32L * 24 * 3_600_000)
        // Two highs and two lows a day, 12 h 25 min apart
        assertEquals(4, next.size)
        val highs = next.filter { it.high }
        assertEquals(12 * 60 + 25.0, (highs[1].time - highs[0].time) / 60_000.0, 2.0)
        assertEquals(650.0, highs[0].level, 1.0)
    }

    @Test fun needsTwoWeeksOfData() {
        val times = LongArray(10 * 144) { it * 600_000L }
        assertNull(Tides.fit(times, DoubleArray(times.size) { 500.0 }))
    }

    @Test fun predictsCuxhavenFromItsOwnMeasurements() {
        // PEGELONLINE Cuxhaven Steubenhöft, 10-minute means; fit on all but the last 48 h.
        val rows = Fixtures.text("pegel_cuxhaven_10min.csv").lines().filter { it.isNotBlank() }.map { it.split(",") }
        val t = rows.map { it[0].toLong() * 1000 }
        val v = rows.map { it[1].toDouble() }
        val split = 1790593680L * 1000
        val fitT = t.filter { it < split }
        val model = Tides.fit(fitT.toLongArray(), v.take(fitT.size).toDoubleArray(), now = split)
        assertNotNull(model)
        val pred = Tides.extremes(model!!, split, t.last())
        // High and low waters measured at the gauge (30-minute smoothed): time in s, high?
        val measured = listOf(1790625780L to false, 1790644620L to true, 1790670180L to false, 1790689200L to true, 1790714100L to false, 1790733240L to true)
        measured.forEach { (ts, high) ->
            val best = pred.filter { it.high == high }.minBy { abs(it.time - ts * 1000) }
            val minutes = abs(best.time - ts * 1000) / 60_000.0
            assertTrue("${if (high) "high" else "low"} water off by $minutes min", minutes < 60)
        }
        // Tidal range at Cuxhaven is about 3 m
        val range = pred.filter { it.high }.map { it.level }.average() - pred.filter { !it.high }.map { it.level }.average()
        assertEquals(300.0, range, 60.0)
    }
}

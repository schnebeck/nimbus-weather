/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SatelliteSunshineTest.kt
 * The learnt sunshine model in the app: the same answers as where it was learnt, and its edges.
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

import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.SatelliteHour
import dev.nimbus.weather.data.remote.SatelliteSunshine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * The trees learnt by tools/sunshine_calibration.py from the weather of the hour (no month, no
 * place): the app reads them wherever its classes end up and gives what the tool gave.
 */
class SatelliteSunshineTest {
    private val trees = requireNotNull(SatelliteSunshine.model)

    /** Found from a class of another package too – the release build moves the app's classes. */
    @Test fun theModelIsFoundFromAnywhere() {
        assertNotNull(GridSpanTest::class.java.getResourceAsStream(SatelliteSunshine.MODEL))
        assertNotNull(SatelliteSunshine.model)
    }

    /** Open-Meteo's sunshine, direct and global against a clear sky, diffuse share, sun height, low cloud – as the tool evaluated them. */
    @Test fun theSameAnswersAsTheTool() {
        val nan = Double.NaN
        assertEquals(22.054358, trees.minutes(doubleArrayOf(60.0, 0.3, 0.45, 0.6, 29.0, nan)), 1e-4)
        assertEquals(18.357722, trees.minutes(doubleArrayOf(60.0, 0.3, 0.45, 0.6, 29.0, 80.0)), 1e-4)
        assertEquals(0.277583, trees.minutes(doubleArrayOf(0.0, 0.0, 0.2, 1.0, 10.0, 100.0)), 1e-4)
        assertEquals(38.278075, trees.minutes(doubleArrayOf(45.0, 1.0, 1.0, 0.15, 40.0, 0.0)), 1e-4)
        assertEquals(22.949076, trees.minutes(doubleArrayOf(30.0, 0.5, 0.7, 0.4, 20.0, nan)), 1e-4)
    }

    /** Without the radiation, or with the sun down: Open-Meteo's own value. */
    @Test fun whereTheModelHasNothingToGoOn() {
        val noon = Instant.parse("2026-10-10T11:00:00Z").toEpochMilli()
        val night = Instant.parse("2026-10-10T22:00:00Z").toEpochMilli()
        assertEquals(42.0, SatelliteSunshine.minutes(SatelliteHour(42.0, null, 300.0, 100.0), noon, 53.6, 7.2, null)!!, 0.0)
        assertEquals(0.0, SatelliteSunshine.minutes(SatelliteHour(0.0, 0.0, 0.0, 0.0), night, 53.6, 7.2, null)!!, 0.0)
        assertNull(SatelliteSunshine.minutes(SatelliteHour(null, null, null, null), noon, 53.6, 7.2, null))
    }

    /** The model's low cloud of an hour: the mean of its start and its end (as learnt). */
    @Test fun theLowCloudOfAnHour() {
        val root = JsonCodec.parseToJsonElement("""{"hourly":{"time":[1791622800,1791626400],"cloud_cover_low":[20,60]}}""")
        val low = HistorySource.lowCloud(root)
        assertEquals(40.0, low(1791626400_000L)!!, 1e-9)
        assertNull(low(1791622800_000L))
    }
}

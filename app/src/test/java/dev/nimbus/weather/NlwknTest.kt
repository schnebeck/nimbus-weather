/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/NlwknTest.kt
 * Tests for the Lower Saxony state gauges (NLWKN Pegelonline).
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

import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.remote.NlwknSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NlwknTest {
    @Test fun stationListWithCoordinatesInTheRightOrder() {
        val list = NlwknSource.parseStations(Fixtures.json("nlwkn_stations.json"))
        assertTrue(list.size > 80)
        val heinde = list.first { it.name == "Heinde" }
        assertEquals("Innerste", heinde.water)
        assertEquals(52.102, heinde.lat, 0.01)   // the service swaps the field names
        assertEquals(10.025, heinde.lon, 0.01)
        // Groß Düngen lies right next to it (under a kilometre)
        assertTrue(NlwknSource.distanceKm(52.0968, 10.0180, heinde.lat, heinde.lon) < 1.5)
    }

    @Test fun seriesWithAlertLevels() {
        val g = NlwknSource.parseSeries(Fixtures.json("nlwkn_heinde.json"), 4.0)!!
        assertEquals("Heinde", g.name)
        assertEquals(GaugeProvider.NLWKN, g.provider)
        assertEquals(mapOf(1 to 330.0, 2 to 430.0, 3 to 530.0), g.alertLevels)
        assertEquals(0, g.alertStage)
        assertTrue(g.history.size > 600)                          // 7 days in 15-minute steps
        assertTrue(g.history.zipWithNext().all { (a, b) -> a.time < b.time })
        assertEquals(78.88, g.gaugeZero!!, 0.01)
    }

    @Test fun microsoftDates() {
        assertEquals(1790778600000L, NlwknSource.parseDate("/Date(1790778600000)/"))
        assertEquals(1790782200000L, NlwknSource.parseDate("/Date(1790782200000+0000)/"))
    }
}

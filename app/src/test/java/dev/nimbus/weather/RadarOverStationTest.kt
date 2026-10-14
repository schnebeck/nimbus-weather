/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarOverStationTest.kt
 * The radar over the place beats a distant station in the look-back – a gauge within the radar's pixel beats the radar;
 * the hour's symbol follows the amount kept.
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

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.Provenance
import dev.nimbus.weather.data.remote.SpotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Groß Düngen, 13 Oct 2026: the DWD station Alfeld, 17 km away, saw rain from 13 h on with 0.0 mm
 * in its gauge; the radar over the place measured 0.1 mm in the hour 13–14 h and nothing else
 * until the evening. Recorded answers of Bright Sky and of the DWD's RADOLAN RW.
 */
class RadarOverStationTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private val wet = setOf(
        Condition.DRIZZLE, Condition.RAIN, Condition.HEAVY_RAIN, Condition.SHOWERS, Condition.FREEZING_RAIN,
        Condition.SLEET, Condition.SNOW, Condition.HEAVY_SNOW,
    )
    private val station = HistorySource.parseObservations(Fixtures.json("brightsky_gross_duengen.json")).byTime
    private val radar = SpotSource.hourly(SpotSource.series(Fixtures.json("radolan_rw_gross_duengen.json")), emptyMap())
    /** The hours 12–18 h local (their ends 13 … 18 h, 11 … 16 UTC). */
    private val afternoon = (11..16).map { at("2026-10-13T%02d:00:00Z".format(it)) }

    /** RW names its value after its file (RW_HDF5), RY GRAY_INDEX: both are read. */
    @Test fun theRadarsHourlySumIsRead() {
        assertEquals(13, radar.size)
        assertEquals(0.1, radar.getValue(at("2026-10-13T12:00:00Z")), 1e-6)
        assertEquals(0.4, radar.getValue(at("2026-10-13T20:00:00Z")), 1e-6)
    }

    /** The station alone: "rain" with 0.0 mm shows its sky – no rain symbol over an hour without an amount. */
    @Test fun noRainSymbolWithoutAnAmount() {
        val hours = HistorySource.overSpot(station, emptyMap(), emptyMap(), Long.MAX_VALUE)
        afternoon.forEach { t ->
            assertEquals(0.0, hours.getValue(t).precipitation!!, 0.0)
            assertTrue("$t: ${hours.getValue(t).condition}", hours.getValue(t).condition !in wet)
        }
        // 19–20 h: 0.1 mm in the gauge – drizzle
        assertEquals(Condition.DRIZZLE, hours.getValue(at("2026-10-13T17:00:00Z")).condition)
    }

    /** The radar over the place beats the station 17 km away: its amount, and the symbol by it. */
    @Test fun theRadarBeatsADistantStation() {
        val hours = HistorySource.overSpot(station, radar, emptyMap(), Long.MAX_VALUE)
        afternoon.forEach { t -> assertEquals(Provenance.RADAR, hours.getValue(t).precipitationFrom) }
        // 13–14 h: the radar's 0.1 mm – drizzle; the other hours dry
        assertEquals(Condition.DRIZZLE, hours.getValue(at("2026-10-13T12:00:00Z")).condition)
        (afternoon - at("2026-10-13T12:00:00Z")).forEach { t -> assertTrue("$t", hours.getValue(t).condition !in wet) }
        // 21–22 h: the station's 0.7 mm, the radar's 0.4 – rain by the radar's amount
        val evening = hours.getValue(at("2026-10-13T20:00:00Z"))
        assertEquals(0.4, evening.precipitation!!, 1e-6)
        assertEquals(Condition.RAIN, evening.condition)
    }

    /** Rain the radar measured over the place where the distant station saw it dry. */
    @Test fun theRadarsRainWhereTheStationWasDry() {
        val t = at("2026-10-13T08:00:00Z")
        val dry = station.getValue(t)
        assertEquals(Condition.CLOUDY, dry.condition)
        val hours = HistorySource.overSpot(mapOf(t to dry), mapOf(t to 2.0), emptyMap(), Long.MAX_VALUE)
        assertEquals(Condition.RAIN, hours.getValue(t).condition)
    }

    /** A gauge within the radar's pixel measures the place itself: its amount stays, the radar's is not taken. */
    @Test fun aGaugeWithinThePixelBeatsTheRadar() {
        assertEquals(17.377, station.getValue(afternoon.first()).precipitationKm!!, 1e-3)
        val near = station.mapValues { (_, m) -> m.copy(precipitationKm = 0.6) }
        val hours = HistorySource.overSpot(near, radar, emptyMap(), Long.MAX_VALUE)
        val evening = hours.getValue(at("2026-10-13T20:00:00Z"))
        assertEquals(Provenance.STATION, evening.precipitationFrom)
        assertEquals(0.7, evening.precipitation!!, 1e-6)
        // where the gauge has no value, the radar's stands
        val gap = at("2026-10-13T12:00:00Z")
        val withGap = HistorySource.overSpot(near + (gap to near.getValue(gap).copy(precipitation = null)), radar, emptyMap(), Long.MAX_VALUE)
        assertEquals(Provenance.RADAR, withGap.getValue(gap).precipitationFrom)
    }
}

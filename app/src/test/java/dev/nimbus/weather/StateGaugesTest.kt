/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/StateGaugesTest.kt
 * Tests for the state gauge sources (NRW, Hessen, Sachsen), the LHP and the choice of gauges.
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

import dev.nimbus.weather.data.model.AlertSeverity
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.remote.GaugeGeo
import dev.nimbus.weather.data.remote.GaugeSource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.LhpSource
import dev.nimbus.weather.data.remote.SachsenSource
import dev.nimbus.weather.data.remote.WiskiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateGaugesTest {
    @Test fun wiskiStationLists() {
        val nrw = WiskiSource.parseStations(Fixtures.json("wiski_nrw_stations.json"))
        assertTrue(nrw.size > 250)
        assertTrue(nrw.none { it.name.endsWith("_NRW") })
        val he = WiskiSource.parseStations(Fixtures.json("wiski_hessen_stations.json"))
        val marburg = he.first { it.no == "25830056" }
        assertEquals("Lahn", marburg.water)
        assertEquals(50.8, marburg.lat, 0.1)
    }

    @Test fun nrwWeekAndInformationLevels() {
        val week = WiskiSource.parseWeek(Fixtures.json("wiski_nrw_bachum_week.json"))
        assertTrue(week.measured.size > 600)
        assertTrue(week.forecast.isEmpty())
        val alarm = WiskiSource.parseAlarm(Fixtures.json("wiski_nrw_bachum_alarm.json"))
        assertEquals(mapOf(1 to 210.0, 2 to 400.0, 3 to 440.0), alarm.levels)
        assertEquals(107.0, alarm.marks.getValue("MW"), 0.0)
    }

    @Test fun hessenWeekWithForecast() {
        val week = WiskiSource.parseWeek(Fixtures.json("wiski_hessen_marburg_week.json"))
        assertTrue(week.measured.size > 600)
        assertTrue("forecast continues the measured series", week.forecast.isNotEmpty() && week.forecast.first().time > week.measured.last().time)
        val alarm = WiskiSource.parseAlarm(Fixtures.json("wiski_hessen_marburg_alarm.json"))
        assertEquals(mapOf(1 to 400.0, 2 to 450.0, 3 to 480.0), alarm.levels)
    }

    @Test fun sachsenCurrentValues() {
        val list = SachsenSource.parse(Fixtures.json("sachsen_dresden.json"), 51.05, 13.74)
        val dresden = list.first { it.name == "Dresden" }
        assertEquals("Elbe", dresden.water)
        assertNotNull(dresden.level)
        assertEquals(mapOf(1 to 400.0, 2 to 500.0, 3 to 600.0, 4 to 700.0), dresden.alertLevels)
        assertEquals(0, dresden.alertStage)
        assertEquals(0, dresden.tendency)
        assertTrue(dresden.distanceKm < 2)
        // "k.A." discharge is no number
        assertNull(list.first { it.name == "Hainsberg 6" }.discharge)
        assertEquals(0.679, list.first { it.name == "Hainsberg 5" }.discharge!!, 1e-9)
    }

    @Test fun lhpStationsAndEntities() {
        val all = LhpSource.parseStations(Fixtures.json("lhp_stations.json"), 51.416, 9.650)
        val werra = all.first { it.water == "Werra" }
        assertEquals(GaugeProvider.LHP, werra.provider)
        assertTrue(werra.link!!.startsWith("http"))
        assertEquals("< 2", LhpSource.decodeEntities("&#60; 2"))
    }

    @Test fun onePerWaterBodyAndDuplicatesMerged() {
        fun g(id: String, water: String, d: Double, p: GaugeProvider, level: Double? = 100.0, cls: Int? = null) =
            GaugeInfo(uuid = id, name = id, water = water, distanceKm = d, tidal = false, level = level, provider = p, lhpClass = cls)
        val chosen = GaugeSource.select(
            listOf(
                g("HANN.MUENDEN", "WESER", 1.3, GaugeProvider.PEGELONLINE),
                g("Hann.Muenden", "Weser", 1.3, GaugeProvider.LHP, level = null, cls = 0),       // same gauge
                g("BONAFORTH", "FULDA", 1.9, GaugeProvider.PEGELONLINE),
                g("LETZTER HELLER", "WERRA", 1.9, GaugeProvider.PEGELONLINE),
                g("Ziegenhagen", "Rautenbach", 9.2, GaugeProvider.LHP, level = null, cls = 0),
                g("Far away", "Weser", 8.0, GaugeProvider.PEGELONLINE),                               // same river, farther
                g("Outside", "Elbe", 12.0, GaugeProvider.PEGELONLINE),                                // beyond 10 km
            ),
        )
        assertEquals(listOf("HANN.MUENDEN", "BONAFORTH", "LETZTER HELLER", "Ziegenhagen"), chosen.map { it.uuid })
        assertEquals(0, chosen.first().lhpClass)                  // classification taken over from the LHP
        assertEquals(GaugeProvider.PEGELONLINE, chosen.first().provider)
    }

    @Test fun fartherTideGaugeOnlyWhenNoneIsNear() {
        fun t(id: String, water: String, d: Double) = GaugeInfo(id, id, water, d, true, level = 400.0, provider = GaugeProvider.PEGELONLINE)
        // Cuxhaven: tide gauges at 1.2 and 9 km – the Oste at 22.7 km stays out
        assertEquals(listOf("CUX", "MITTELGRUND"), GaugeSource.select(listOf(t("CUX", "Elbe", 1.2), t("MITTELGRUND", "Nordsee", 9.0), t("BELUM", "Oste", 22.7))).map { it.uuid })
        // Norden: nothing within 10 km – Norderney at 11.6 km stands in
        assertEquals(listOf("NORDERNEY"), GaugeSource.select(listOf(t("NORDERNEY", "Nordsee", 11.6), t("FAR", "Ems", 20.0))).map { it.uuid })
    }

    @Test fun nearestGaugeStandsInWhenNoneWithinTenKm() {
        fun g(id: String, water: String, d: Double) = GaugeInfo(id, id, water, d, false, level = 100.0, provider = GaugeProvider.PEGELONLINE)
        // East of Hannover: the Leine gauge Herrenhausen 11.4 km away, another river at 17 km
        assertEquals(listOf("HERRENHAUSEN"), GaugeSource.select(listOf(g("HERRENHAUSEN", "Leine", 11.4), g("OTHER", "Fuhse", 17.0))).map { it.uuid })
        // nothing within 20 km: no card
        assertTrue(GaugeSource.select(listOf(g("FAR", "Weser", 21.0))).isEmpty())
        // a gauge within 10 km: another water up to 15 km fills a free place, beyond that nothing
        assertEquals(listOf("NEAR", "HERRENHAUSEN"), GaugeSource.select(listOf(g("NEAR", "Ihme", 6.0), g("HERRENHAUSEN", "Leine", 11.4))).map { it.uuid })
        assertEquals(listOf("NEAR"), GaugeSource.select(listOf(g("NEAR", "Ihme", 6.0), g("FAR", "Fuhse", 16.0))).map { it.uuid })
    }

    @Test fun braunschweigShowsSchunterAndOker() {
        fun g(id: String, water: String, d: Double) = GaugeInfo(id, id, water, d, false, level = 100.0, provider = GaugeProvider.NLWKN)
        // NLWKN gauges around the city centre (1 October 2026)
        val chosen = GaugeSource.select(
            listOf(g("Harxbüttel", "Schunter", 8.3), g("Groß Schwülper", "Oker", 11.6), g("Ohrum", "Oker", 16.2), g("Glentorf", "Schunter", 20.5)),
        )
        assertEquals(listOf("Harxbüttel", "Groß Schwülper"), chosen.map { it.uuid })
    }

    @Test fun measuredGaugePreferredOverClassificationOnly() {
        val chosen = GaugeSource.select(
            listOf(
                GaugeInfo("LHP:1", "A", "Lahn", 1.0, false, provider = GaugeProvider.LHP, lhpClass = 0),
                GaugeInfo("HE:1", "B", "Lahn", 3.5, false, level = 150.0, provider = GaugeProvider.HLNUG_HESSEN),
            ),
        )
        assertEquals("HE:1", chosen.single().uuid)
    }

    @Test fun floodAlertAreas() {
        val json = JsonCodec.parseToJsonElement(
            """{"type":"FeatureCollection","features":[
              {"kind":"AlertArea","id":"BY_577","type":"Feature",
               "geometry":{"type":"Polygon","coordinates":[[[10.8,49.0],[11.1,49.0],[11.1,49.2],[10.8,49.2],[10.8,49.0]]]},
               "properties":{"areaDesc":"Lkr. Weißenburg-Gunzenhausen","areaType":"Region","alertHeadline":"Hochwasserwarnung vor Ausuferungen","alertLink":"https://www.hnd.bayern.de","lhpClass":"4","lhpClassName":"Hochwasser"}},
              {"kind":"AlertArea","id":"XX_1","type":"Feature",
               "geometry":{"type":"LineString","coordinates":[[9.0,50.0],[9.2,50.0]]},
               "properties":{"areaType":"Flussabschnitt","alertHeadline":"Main","lhpClass":"2","lhpClassName":"Kleines Hochwasser"}}]}""",
        )
        val inside = LhpSource.parseAlerts(json, 49.1, 10.95)
        assertEquals(1, inside.size)
        assertEquals(AlertSeverity.SEVERE, inside[0].severity)
        assertEquals("LHP", inside[0].source)
        assertEquals(1, LhpSource.parseAlerts(json, 50.03, 9.1).size)     // 3 km from the river section
        assertTrue(LhpSource.parseAlerts(json, 52.0, 10.0).isEmpty())
        assertFalse(GaugeGeo.inRing(listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0), 2.0, 2.0))
    }
}

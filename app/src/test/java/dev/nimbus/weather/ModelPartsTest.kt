/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ModelPartsTest.kt
 * Which single model Open-Meteo's best match takes (with its grid), and the day in which the
 * chosen model gives way to it. Recorded answers for Norden and Cuxhaven of 5 October 2026.
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
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.remote.ModelForecast
import dev.nimbus.weather.data.remote.OpenMeteoSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ModelPartsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, berlin).toInstant().toEpochMilli()

    /**
     * "Bekommt man die Info, was Best Match lokal auswählt?" – "wir sollte aber nicht nur das Modell
     * benennen, sondern auch noch die Modellauflösung mit angeben": in Norden the Dutch model
     * first, the European one of ECMWF later.
     */
    @Test fun inNordenKnmiFirstThenEcmwf() {
        val parts = OpenMeteoSource.bestMatchParts(Fixtures.json("openmeteo_parts_norden.json"))
        assertEquals("KNMI Harmonie" to 2.0, parts.getValue(at(5, 12)).let { it.name to it.km })
        assertEquals("ECMWF IFS" to 9.0, parts.getValue(at(10, 12)).let { it.name to it.km })
    }

    /** Its last hours, where it has none after them: still the model's (the hours before count). */
    @Test fun aModelsLastHoursAreItsOwn() {
        val root = Fixtures.json("openmeteo_parts_norden.json")
        val parts = OpenMeteoSource.bestMatchParts(root)
        val h = root.obj()!!.getValue("hourly") as kotlinx.serialization.json.JsonObject
        val times = (h.getValue("time") as kotlinx.serialization.json.JsonArray).map { it.toString().toLong() * 1000 }
        val knmi = (h.getValue("temperature_2m_knmi_harmonie_arome_netherlands") as kotlinx.serialization.json.JsonArray)
        val best = (h.getValue("temperature_2m_best_match") as kotlinx.serialization.json.JsonArray)
        // the last hour KNMI Harmonie has and the best match takes
        val last = times.indices.last { knmi[it].toString() != "null" && knmi[it] == best[it] }
        assertEquals("KNMI Harmonie", parts.getValue(times[last]).name)
    }

    /** In Cuxhaven the best match is the DWD's model of 2 km. */
    @Test fun inCuxhavenIconD2() {
        val parts = OpenMeteoSource.bestMatchParts(Fixtures.json("openmeteo_parts_cuxhaven.json"))
        assertEquals("DWD ICON-D2" to 2.0, parts.getValue(at(5, 12)).let { it.name to it.km })
    }

    private fun kotlinx.serialization.json.JsonElement.obj() = this as? kotlinx.serialization.json.JsonObject

    /**
     * The day the chosen model's hours end in: its highest and lowest from the hours shown – the
     * model's until 18:00, the best match's after (Norden, 7 Oct.: the row said 20.6°, the curve
     * reached 19.3°).
     */
    @Test fun theDayTheModelEndsInTakesItsHours() {
        fun hour(t: Long, temp: Double) = HourlyPoint(t, temp, null, Condition.CLOUDY, true)
        val model = ModelForecast(
            "Europe/Berlin", 7200, null,
            hourly = (0..42).map { i -> at(6, 0) + i * 3_600_000L }.map { t -> hour(t, if (t == at(7, 14)) 19.3 else 14.0) },
            daily = listOf(DailyPoint(at(6, 0), Condition.CLOUDY, 18.0, 12.0)),
            minutely = emptyList(),
        )
        val best = ModelForecast(
            "Europe/Berlin", 7200, null,
            hourly = (0..71).map { i -> at(6, 0) + i * 3_600_000L }.map { t -> hour(t, if (t == at(7, 16)) 20.6 else 13.0) },
            daily = listOf(6, 7, 8).map { d -> DailyPoint(at(d, 0), Condition.CLOUDY, if (d == 7) 20.6 else 17.0, if (d == 7) 11.0 else 10.0) },
            minutely = emptyList(),
        )
        val merged = model.mergedWith(best).daily.associateBy { it.date }
        // the 7th: the model's hours up to 18:00 (19.3° at 14:00), the best match's after (13°)
        assertEquals(19.3, merged.getValue(at(7, 0)).tempMax, 0.01)
        assertEquals(13.0, merged.getValue(at(7, 0)).tempMin, 0.01)
        // the days wholly of one: as they were
        assertEquals(18.0, merged.getValue(at(6, 0)).tempMax, 0.01)
        assertEquals(17.0, merged.getValue(at(8, 0)).tempMax, 0.01)
    }
}

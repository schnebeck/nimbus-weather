/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/MetNorwayTest.kt
 * MET Nordic (MET Norway) as the forecast model: about 1 km for Scandinavia and northern Germany,
 * 2½ days – the rest from Open-Meteo's best match; outside its area the best match alone, in the
 * look-back too. Recorded answers for Cuxhaven of 5 October 2026.
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

import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.SourceKind
import dev.nimbus.weather.data.model.modelFor
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.repo.WeatherRepository
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MetNorwayTest {
    private val server = MockWebServer()
    /** The place lies outside MET Nordic's area (Hannover): the model has nothing for it. */
    @Volatile private var outside = false
    private val cuxhaven = Place("c", "Cuxhaven", latitude = 53.871, longitude = 8.694)
    private val metno = Fixtures.json("openmeteo_cuxhaven_metno_nordic.json")
    private val best = Fixtures.json("openmeteo_cuxhaven_best_match.json")

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                fun ok(name: String) = MockResponse.Builder().code(200).body(Fixtures.text(name)).build()
                val noData = MockResponse.Builder().code(400).body("""{"reason":"No data is available for this location","error":true}""").build()
                return when {
                    // which single models the best match takes
                    url.encodedPath == "/v1/forecast" && url.queryParameter("models").orEmpty().startsWith("best_match,") -> ok("openmeteo_parts_cuxhaven.json")
                    url.encodedPath == "/v1/forecast" && url.queryParameter("models") == "knmi_harmonie_arome_netherlands" -> ok("openmeteo_norden_knmi_harmonie_arome_netherlands.json")
                    url.encodedPath == "/v1/forecast" && url.queryParameter("models") == "metno_nordic" ->
                        if (outside) noData else if (url.queryParameter("past_days") != null) ok("openmeteo_history.json") else ok("openmeteo_cuxhaven_metno_nordic.json")
                    url.encodedPath == "/v1/forecast" && url.queryParameter("past_days") != null -> ok("openmeteo_history.json")
                    url.encodedPath == "/v1/forecast" -> ok("openmeteo_cuxhaven_best_match.json")
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun base() = server.url("/").toString().trimEnd('/')

    private fun repo(): WeatherRepository {
        val http = OkHttpClient()
        val b = base()
        // the time of the recording
        val now = (metno.obj()!!["current"]!!.obj()!!["time"]!!.toString().toLong()) * 1000
        return WeatherRepository(
            OpenMeteoSource(http, b, b, b), BrightSkySource(http, b), CommunitySource(http, b),
            PollenSource(http, "$b/pollen.json", "$b/wms", b), clock = { now },
        )
    }

    private fun kotlinx.serialization.json.JsonElement.obj() = this as? kotlinx.serialization.json.JsonObject

    /** In its area: MET Nordic's values for its 2½ days, the best match after them and for what it lacks. */
    @Test fun inItsAreaMetNordicForItsDays() = runTest {
        val data = repo().load(cuxhaven, Settings(model = ForecastModel.MET_NORWAY), german = true)
        assertEquals(SourceKind.MODEL_REGIONAL, data.sources.first().kind)
        assertEquals("MET Nordic" to 1.0, data.sources.first().part?.let { it.name to it.km })
        assertTrue("no gap fill named: ${data.sources}", data.sources.any { it.kind == SourceKind.GAP_FILL })
        // now: MET Nordic's 16.2 °C, not the best match's 15.4 °C
        assertEquals(16.2, data.current.temperature, 0.01)
        // ten days all the same: two of MET Nordic, the rest from the best match
        assertEquals(10, data.daily.size)
        val bestMax = best.obj()!!["daily"]!!.obj()!!["temperature_2m_max"]!!.let { it as kotlinx.serialization.json.JsonArray }
        assertEquals(bestMax[6].toString().toDouble(), data.daily[6].tempMax, 0.01)
        // what MET Nordic does not give (the chance of precipitation) comes from the best match
        assertTrue(data.hourly.take(48).all { it.precipitationProbability != null })
        // the sources say from when on the best match gives the forecast, and with which model
        val fill = data.sources.single { it.kind == SourceKind.GAP_FILL }
        val metLast = OpenMeteoSource.parseForecast(metno).hourly.last().time
        assertEquals(metLast + 3_600_000L, fill.since)
        val parts = OpenMeteoSource.bestMatchParts(Fixtures.json("openmeteo_parts_cuxhaven.json"))
        assertEquals(parts.entries.first { it.key >= fill.since!! }.value, fill.part)
        assertNotNull("no model named for the gap fill", fill.part)
    }

    /** Outside its area (Hannover): the forecast all the same – the best match, named as such. */
    @Test fun outsideItsAreaTheBestMatch() = runTest {
        outside = true
        val data = repo().load(Place("h", "Hannover", latitude = 52.3759, longitude = 9.732), Settings(model = ForecastModel.MET_NORWAY), german = true)
        assertEquals(SourceKind.MODEL_BEST_MATCH, data.sources.first().kind)
        assertEquals(15.4, data.current.temperature, 0.01)
        assertEquals(10, data.daily.size)
    }

    /** The look-back outside its area: from the best match – it failed altogether before. */
    @Test fun theLookBackOutsideItsAreaTakesTheBestMatch() = runTest {
        outside = true
        val history = HistorySource(OkHttpClient(), base(), base()).load(52.3759, 9.732, "metno_nordic", inGermany = false)
        assertEquals("best_match", history.modelId)
        assertEquals(3, history.days.size)
    }

    /** In its area the look-back is MET Nordic's own. */
    @Test fun theLookBackInItsAreaIsMetNordic() = runTest {
        val history = HistorySource(OkHttpClient(), base(), base()).load(cuxhaven.latitude, cuxhaven.longitude, "metno_nordic", inGermany = false)
        assertEquals("metno_nordic", history.modelId)
    }

    /** "Die globale Einstellung bleibt als Standard … Jeder gespeicherte Ort bekommt optional ein eigenes Modell". */
    @Test fun thePlacesModelElseTheSettings() {
        val settings = Settings(model = ForecastModel.DWD_ICON)
        assertEquals(ForecastModel.DWD_ICON, settings.modelFor(cuxhaven))
        assertEquals(ForecastModel.MET_NORWAY, settings.modelFor(cuxhaven.copy(model = ForecastModel.MET_NORWAY)))
        // "my location" moves: always the settings' one
        val here = Place(dev.nimbus.weather.data.repo.LocationProvider.CURRENT_LOCATION_ID, "Hier", latitude = 53.9, longitude = 8.7, isCurrentLocation = true, model = ForecastModel.MET_NORWAY)
        assertEquals(ForecastModel.DWD_ICON, settings.modelFor(here))
    }

    /** Cuxhaven on MET Nordic while the settings say DWD ICON: its forecast is MET Nordic's. */
    @Test fun aPlaceIsLoadedWithItsOwnModel() = runTest {
        val data = repo().load(cuxhaven.copy(model = ForecastModel.MET_NORWAY), Settings(model = ForecastModel.DWD_ICON), german = true)
        assertEquals(SourceKind.MODEL_REGIONAL, data.sources.first().kind)
        assertEquals("MET Nordic" to 1.0, data.sources.first().part?.let { it.name to it.km })
        assertEquals(16.2, data.current.temperature, 0.01)
    }

    /** Norden on KNMI Harmonie, named with its grid. */
    @Test fun nordenOnKnmiHarmonie() = runTest {
        val norden = Place("n", "Norden", latitude = 53.596, longitude = 7.206, model = ForecastModel.KNMI)
        val data = repo().load(norden, Settings(), german = true)
        assertEquals(SourceKind.MODEL_REGIONAL, data.sources.first().kind)
        assertEquals("KNMI Harmonie" to 2.0, data.sources.first().part?.let { it.name to it.km })
        val knmi = OpenMeteoSource.parseForecast(Fixtures.json("openmeteo_norden_knmi_harmonie_arome_netherlands.json"))
        assertEquals(knmi.current!!.temperature, data.current.temperature, 0.01)
    }

    /** Every regional model: one grid, named and known to the best match's naming. */
    @Test fun everyRegionalModelHasItsGrid() {
        val regional = ForecastModel.entries.filter { it.part != null }
        assertEquals(8, regional.size)
        regional.forEach { m ->
            assertEquals(m.openMeteoId, m.part!!.id)
            assertTrue("$m not among the best match's parts", dev.nimbus.weather.data.model.BestMatchParts.any { it.id == m.openMeteoId })
        }
    }

    /** "Automatic" heads the list of models and is the default. */
    @Test fun automaticIsFirstAndTheDefault() {
        assertEquals(ForecastModel.BEST_MATCH, Settings().model)
        assertEquals(ForecastModel.BEST_MATCH, dev.nimbus.weather.ui.settings.ModelChoices.first().first)
        assertEquals(ForecastModel.entries.toSet(), dev.nimbus.weather.ui.settings.ModelChoices.map { it.first }.toSet())
    }
}

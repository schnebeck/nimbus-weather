/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SpreadTest.kt
 * The spread of the forecast: the ensemble's runs read, kept for six hours, drawn as a fan.
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

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.Spread
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.CommunitySource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.data.repo.Freshness
import dev.nimbus.weather.data.repo.RecordState
import dev.nimbus.weather.data.repo.Shelf
import dev.nimbus.weather.data.repo.StandIn
import dev.nimbus.weather.data.repo.WeatherRepository
import dev.nimbus.weather.ui.components.CardFold
import dev.nimbus.weather.ui.components.LocalCardFold
import dev.nimbus.weather.ui.main.LocalSettings
import dev.nimbus.weather.ui.main.LocalTimeFormat
import dev.nimbus.weather.ui.main.SpreadCard
import dev.nimbus.weather.util.TimeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SpreadTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val zone = ZoneId.of("Europe/Berlin")
    /** Today: Tuesday, 3 November 2026, midnight in Berlin. */
    private val day0 = LocalDate.of(2026, 11, 3).atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = day0 + 10 * h + 30 * 60_000L
    private val brunswick = Place("bs", "Braunschweig", latitude = 52.27, longitude = 10.52)

    /**
     * Five runs over ten days: the same daily course, the runs [r] apart by a margin growing with
     * the hours; run r rains 0.5 mm an hour from 12 to 15 o'clock (4 hours, 2 mm) on the odd
     * days when r ≥ 2 – and only the last run in the hour up to midnight of the first day.
     */
    private fun temp(r: Int, k: Int) = 8.0 + 4 * sin((k % 24 - 9) / 24.0 * 2 * Math.PI) + (r - 2) * (0.2 + k / 48.0)
    private fun precip(r: Int, k: Int): Double {
        val d = k / 24
        val hour = k % 24
        return when {
            k == 24 && r == 4 -> 3.0           // the hour 23–24 of day 0, stamped 00:00 of day 1
            d % 2 == 1 && r >= 2 && hour in 13..16 -> 0.5
            else -> 0.0
        }
    }
    private fun body(runs: Int = 5): String {
        val hours = (0 until 240)
        fun key(name: String, r: Int) = if (r == 0) name else "${name}_member%02d".format(r)
        val series = (0 until runs).flatMap { r ->
            listOf(
                "\"${key("temperature_2m", r)}\":[${hours.joinToString(",") { "%.2f".format(java.util.Locale.US, temp(r, it)) }}]",
                "\"${key("precipitation", r)}\":[${hours.joinToString(",") { precip(r, it).toString() }}]",
            )
        }
        return """{"latitude":52.25,"longitude":10.5,"timezone":"Europe/Berlin","utc_offset_seconds":3600,""" +
            """"hourly":{"time":[${hours.joinToString(",") { ((day0 + it * h) / 1000).toString() }}],${series.joinToString(",")}}}"""
    }
    private fun parsed(at: Long = now): Spread =
        OpenMeteoSource.parseSpread(Json.parseToJsonElement(body()), at, brunswick.latitude, brunswick.longitude)!!

    private val server = MockWebServer()
    private var asked = 0
    private var failing = false

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath != "/v1/ensemble") return MockResponse.Builder().code(404).build()
                asked++
                assertEquals(OpenMeteoSource.SPREAD_MODEL, request.url.queryParameter("models"))
                assertEquals("10", request.url.queryParameter("forecast_days"))
                return if (failing) MockResponse.Builder().code(500).build() else MockResponse.Builder().code(200).body(body()).build()
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun repo(at: Long): WeatherRepository {
        val http = OkHttpClient()
        val base = server.url("/").toString().trimEnd('/')
        return WeatherRepository(
            OpenMeteoSource(http, base, base, base, ensembleUrl = base), BrightSkySource(http, base), CommunitySource(http, base),
            PollenSource(http, "$base/pollen.json", "$base/wms", base), clock = { at },
        )
    }

    /** The runs from the hour of the fetch on: the middle one, 80 % of them and all as bands; the days in the place's time. */
    @Test fun theRunsAsBandsFromTheHourOn() {
        val s = parsed()
        assertEquals(5, s.members)
        assertEquals(day0 + 10 * h, s.times.first())
        assertEquals(230, s.times.size)
        val k = 10 + 48                      // two days on: the runs 0.2 + 58/48 apart
        val i = k - 10
        assertEquals(temp(2, k), s.median[i]!!, 0.01)
        assertEquals(temp(0, k), s.lowest[i]!!, 0.01)
        assertEquals(temp(4, k), s.highest[i]!!, 0.01)
        // 10 %: between the coldest and the next run, at 0.4 of the way
        assertEquals(temp(0, k) + 0.4 * (temp(1, k) - temp(0, k)), s.low[i]!!, 0.01)
        assertTrue(s.high[i]!! > s.median[i]!! && s.high[i]!! < s.highest[i]!!)
        assertEquals(10, s.days.size)
        assertEquals(day0, s.days.first().start)
        assertEquals(LocalDate.of(2026, 11, 4).atStartOfDay(zone).toInstant().toEpochMilli(), s.days[1].start)
        // today's highs from 10 o'clock on, each run its own
        assertEquals((10..23).maxOf { temp(0, it) }, s.days.first().highs.first(), 0.01)
    }

    /** The precipitation of an hour counts on the day it fell: the value at midnight belongs to the day before. */
    @Test fun thePrecipitationOfAnHourOnTheDayItFell() {
        val s = parsed()
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 3.0), s.days[0].sums)
        assertEquals(listOf(0.0, 0.0, 2.0, 2.0, 2.0), s.days[1].sums)
        assertEquals(0.2, s.days[0].wetShare()!!, 1e-9)
        assertEquals(0.6, s.days[1].wetShare()!!, 1e-9)
        assertEquals(0.0, s.days[2].wetShare()!!, 1e-9)
    }

    /** Fewer than two runs: no spread to show. */
    @Test fun oneRunIsNoSpread() {
        val one = body().replace(Regex(""","temperature_2m_member\d\d":\[[^\]]*\]"""), "")
        assertNull(OpenMeteoSource.parseSpread(Json.parseToJsonElement(one), now, 52.27, 10.52))
    }

    /** Kept through the store: written and read back the same. */
    @Test fun storedAsItCame() {
        val s = parsed()
        assertEquals(s, JsonCodec.decodeFromString(Spread.serializer(), JsonCodec.encodeToString(Spread.serializer(), s)))
    }

    /** Four times a day: a spread younger than six hours for the spot is shown as it is – nothing asked. */
    @Test fun aCurrentSpreadAsksNothing() = runBlocking {
        val kept = parsed()
        val a = repo(now + Freshness.SPREAD_MS - 60_000L).spread(brunswick, kept)
        assertEquals(0, asked)
        assertTrue(a.current && !a.fresh)
        assertEquals(kept, a.spread)
        assertEquals(6 * h, Freshness.SPREAD_MS)
    }

    /** Six hours old: asked anew, current, to be stored. */
    @Test fun sixHoursOnAskedAnew() = runBlocking {
        val later = now + Freshness.SPREAD_MS
        val a = repo(later).spread(brunswick, parsed())
        assertEquals(1, asked)
        assertTrue(a.current && a.fresh)
        assertEquals(later, a.spread!!.fetchedAt)
        assertEquals(day0 + 16 * h, a.spread!!.times.first())
    }

    /** The ensemble's server fails: the last spread stays, not current (its card yellow, asked again soon). */
    @Test fun failingTheLastStandsIn() = runBlocking {
        failing = true
        val kept = parsed()
        val a = repo(now + 8 * h).spread(brunswick, kept)
        assertEquals(1, asked)
        assertFalse(a.current)
        assertEquals(kept, a.spread)
        // a day old it no longer stands in; nor one for a spot farther than the grid holds
        assertNull(repo(now + StandIn.SPREAD_MS + 60_000L).spread(brunswick, kept).spread)
        assertNull(repo(now + 8 * h).spread(brunswick.copy(latitude = 52.37), kept).spread)
        // a few kilometres on ("my location"): still the spot
        assertEquals(kept, repo(now + h).spread(brunswick.copy(latitude = 52.29), kept).spread)
    }

    /** Nothing kept, nothing to be had: no spread, not current. */
    @Test fun nothingToStandIn() = runBlocking {
        failing = true
        val a = repo(now).spread(brunswick, null)
        assertNull(a.spread)
        assertFalse(a.current)
    }

    /** Its record: current for six hours, then due by itself; given up with the place. */
    @Test fun itsRecordSixHours() = runTest {
        val due = mutableListOf<Any>()
        val shelf = Shelf(backgroundScope, clock = { now + currentTime }, onDue = { due += it.key })
        val r = shelf.spread("bs")
        assertEquals(RecordState.STALE, shelf.spreadState("bs"))
        r.arrived(now)
        assertEquals(RecordState.CURRENT, shelf.spreadState("bs"))
        advanceTimeBy(Freshness.SPREAD_MS - 1); runCurrent()
        assertEquals(RecordState.CURRENT, shelf.spreadState("bs"))
        advanceTimeBy(1); runCurrent()
        assertEquals(RecordState.STALE, shelf.spreadState("bs"))
        assertEquals(listOf<Any>(dev.nimbus.weather.data.repo.RecordKey.Spread("bs")), due)
    }

    /** A card added: on the page right after the model comparison, also for an order arranged by hand; folded with the special cards. */
    @Test fun theCardAfterTheModelComparison() {
        val own = WeatherCard.DEFAULT_ORDER.filter { it != WeatherCard.SPREAD }.reversed()
        val order = Settings(cardOrder = own).orderedCards()
        assertEquals(order.indexOf(WeatherCard.MODELS) + 1, order.indexOf(WeatherCard.SPREAD))
        assertTrue(WeatherCard.SPREAD in WeatherCard.SPECIAL)
        assertTrue(Settings().shows(WeatherCard.SPREAD))
    }

    @Composable
    private fun Card(folded: Boolean = false, content: @Composable () -> Unit) = CompositionLocalProvider(
        LocalSettings provides Settings(), LocalTimeFormat provides TimeFormat("Europe/Berlin", true),
        LocalCardFold provides CardFold(folded) {},
    ) {
        Box(Modifier.width(380.dp).background(Color(0xFF2B3A4E)).padding(12.dp)) { content() }
    }

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** Folded: tomorrow's high and the last day's as 80 % of the runs have them. */
    @Test fun foldedHowFarTheHighsLieApart() {
        val s = parsed()
        compose.setContent { Card(folded = true) { SpreadCard(s, true, now) {} } }
        val tf = TimeFormat("Europe/Berlin", true)
        fun t(v: Double) = dev.nimbus.weather.util.Units.temp(v, dev.nimbus.weather.data.model.TemperatureUnit.CELSIUS)
        fun inner(d: dev.nimbus.weather.data.model.SpreadDay) = Spread.quantile(d.highs, 0.1)!! to Spread.quantile(d.highs, 0.9)!!
        val (a, b) = inner(s.days[1])
        val (c, d) = inner(s.days.last())
        val text = ctx.getString(R.string.spread_summary, t(a), t(b), tf.weekdayShort(s.days.last().start + 12 * h), t(c), t(d))
        compose.onNodeWithText(text).assertExists()
    }

    /** Asked for when shown; open, the fan and the days' bars – and nothing to be had: said so. */
    @Test fun theCardOpen() {
        var requested = 0
        val s = parsed()
        compose.setContent { Card { SpreadCard(s, true, now) { requested++ } } }
        compose.waitForIdle()
        assertEquals(1, requested)
        compose.onNodeWithTag("spread-chart").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.spread_hint)).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/spread_card.png")
    }

    @Test fun nothingToBeHadSaysSo() {
        compose.setContent { Card { SpreadCard(null, true, now) {} } }
        compose.onNodeWithText(ctx.getString(R.string.spread_unavailable)).assertExists()
        assertNotNull(ctx.getString(R.string.term_spread_body))
    }
}

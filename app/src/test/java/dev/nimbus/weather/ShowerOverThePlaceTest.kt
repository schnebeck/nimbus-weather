/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ShowerOverThePlaceTest.kt
 * A shower over the place counts wherever the app speaks of precipitation: in the next minutes, in
 * the symbol now, in the hours and in the look-back – by the radar's 5-minute steps, as the radar
 * map shows them. The day of reference: Groß Düngen on 22 October 2026, showers in the late
 * afternoon, 0.3 mm in the gauge at Holle 6.5 km away, nothing in the radar's hourly sums.
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
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.PrecipitationUnit
import dev.nimbus.weather.data.model.RadarNow
import dev.nimbus.weather.data.model.SkyBasis
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.data.remote.HistoryHour
import dev.nimbus.weather.data.remote.HistorySource
import dev.nimbus.weather.data.remote.Provenance
import dev.nimbus.weather.data.remote.SpotSource
import dev.nimbus.weather.data.repo.NowWeather
import dev.nimbus.weather.data.repo.RadarAhead
import dev.nimbus.weather.ui.main.Insights
import dev.nimbus.weather.util.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ShowerOverThePlaceTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private val min = 60_000L
    private val q = 15 * min
    private val h = 60 * min

    /** The radar map's steps over Groß Düngen, 15:55–19:30 local (13:55–17:30 UTC): time → mm/h. */
    private val steps = SpotSource.analysis(Fixtures.json("radar_wn_gross_duengen.json"))

    /** The steps as the radar tells them ahead: mm in each 5 minutes. */
    private fun radar(time: Long) = RadarNow(time, 0.0, steps.mapValues { it.value / 12 })

    /** The model's quarter hours, dry, 15:45–19:30 local (by their ends). */
    private val model = (0..15).map { MinutelyPoint(at("2026-10-22T13:45:00Z") + it * q, 0.0, 12.0) }

    private fun utc(t: Long) = Instant.ofEpochMilli(t).toString().substring(11, 16)

    /** The analysis' answer is read as rates; each hour with all its twelve steps gets its strongest. */
    @Test fun theRadarMapsStepsAreRead() {
        assertEquals(44, steps.size)
        assertEquals(0.29, steps.getValue(at("2026-10-22T14:05:00Z")), 1e-9)   // 14.4 dBZ
        assertEquals(0.0, steps.getValue(at("2026-10-22T14:10:00Z")), 1e-9)    // −2.7 dBZ: no echo
        val hours = SpotSource.hourEchoes(steps)
        // the hour ending 16:00 local has only two of its steps: not known
        assertEquals(listOf("15:00", "16:00", "17:00"), hours.keys.sorted().map(::utc))
        assertEquals(0.29, hours.getValue(at("2026-10-22T15:00:00Z")), 1e-9)
        assertEquals(0.41, hours.getValue(at("2026-10-22T16:00:00Z")), 1e-9)
        assertEquals(0.40, hours.getValue(at("2026-10-22T17:00:00Z")), 1e-9)
    }

    /**
     * Showers of five minutes: by the quarter hours' sums one quarter hour was wet (17:15–17:30),
     * by their steps five were – and four with an echo too weak to be sure are perhaps drizzle.
     */
    @Test fun aShowerOfFiveMinutesCounts() {
        val quarters = RadarAhead.minutely(model, radar(at("2026-10-22T13:55:00Z")), at("2026-10-22T14:00:00Z"))
        val told = quarters.filter { it.peak != null }
        assertEquals(listOf("15:30"), told.filter { it.precipitation >= Insights.RAIN_THRESHOLD_MM_15 }.map { utc(it.time) })
        assertEquals(listOf("14:15", "15:30", "15:45", "16:30", "17:30"), told.filter(Insights::wet).map { utc(it.time) })
        assertEquals(listOf("14:45", "15:15", "16:00", "16:15"), told.filter(Insights::possible).map { utc(it.time) })
    }

    /** At 16:00 the walk of 40 minutes is warned of: light rain from about 16:15 – not "no precipitation". */
    @Test fun theDogWalkIsWarnedOf() {
        val now = at("2026-10-22T14:00:00Z")
        val quarters = RadarAhead.minutely(model, radar(at("2026-10-22T13:55:00Z")), now)
        val hours = (15..18).map { HourlyPoint(at("2026-10-22T%02d:00:00Z".format(it)), 12.0, condition = Condition.CLOUDY, isDay = true, precipitation = 0.0) }
        val notice = Insights.precipNotice(quarters, hours, Condition.CLOUDY, now, rainingNow = false)
        assertNotNull(notice)
        notice!!
        assertFalse(notice.possible)
        assertTrue(notice.state is Insights.Nowcast.StartsIn)
        assertEquals("14:15", utc(notice.at!!))
        assertEquals(Insights.Intensity.LIGHT, notice.intensity)
    }

    /** Only echoes too weak to be sure ahead: perhaps some drizzle – said, not left out. */
    @Test fun perhapsSomeDrizzle() {
        val now = at("2026-10-22T14:45:00Z")
        // the steps after 17:00 local left out: from 16:45 to 17:15 only weak echoes (8.9–10.3 dBZ)
        val weak = RadarNow(at("2026-10-22T14:40:00Z"), 0.0, steps.filterKeys { it <= at("2026-10-22T15:15:00Z") }.mapValues { it.value / 12 })
        val quarters = RadarAhead.minutely(model, weak, now)
        val notice = Insights.precipNotice(quarters, emptyList(), Condition.CLOUDY, now, rainingNow = false, horizonMs = 30 * min)!!
        assertTrue(notice.possible)
        assertEquals(Insights.PrecipKind.DRIZZLE, notice.kind)
    }

    /** A shower in the newest quarter hour falls now, however little its average: the symbol shows it. */
    @Test fun aShowerNowShowsInTheSymbol() {
        val now = at("2026-10-22T15:21:00Z")
        val current = CurrentWeather(
            now, 12.0, 11.0, Condition.PARTLY_CLOUDY, true, 80.0, 8.0, 1015.0, 10.0, 20.0, 200.0, 60.0, 20_000.0, 1.0, 0.0,
            stationName = "Algermissen-Groß Lobke", stationDistanceKm = 19.8, sky = SkyBasis.OBSERVED,
        )
        // RY's newest three steps: 0.02 mm (0.24 mm/h), then none – 0.08 mm/h on average
        val ry = mapOf(at("2026-10-22T15:10:00Z") to 0.0, at("2026-10-22T15:15:00Z") to 0.0, at("2026-10-22T15:20:00Z") to 0.02)
        val c = NowWeather.withRadar(current, RadarNow(at("2026-10-22T15:20:00Z"), 0.08, ry), now)
        assertTrue("${c.condition}", c.condition.isPrecipitation)
        assertEquals(SkyBasis.RADAR, c.sky)
        // without the shower: the sky stays
        val dry = NowWeather.withRadar(current, RadarNow(at("2026-10-22T15:20:00Z"), 0.0, ry.mapValues { 0.0 }), now)
        assertEquals(Condition.PARTLY_CLOUDY, dry.condition)
    }

    /** An hour the radar saw a shower in: a precipitation symbol, and its amount reads ">0" where it rounds to nothing. */
    @Test fun anHourWithAShower() {
        // 16–17 local: a step of 0.29 mm/h and one of 0.14 – 0.04 mm in all
        val now = at("2026-10-22T14:00:00Z")
        val hour = HourlyPoint(at("2026-10-22T15:00:00Z"), 12.0, condition = Condition.CLOUDY, isDay = true, precipitation = 0.0, cloudCover = 90.0)
        val told = RadarAhead.hours(listOf(hour), radar(at("2026-10-22T13:55:00Z")), now).single()
        assertEquals(0.04, told.precipitation!!, 1e-9)
        assertTrue(told.radar)
        assertTrue("${told.condition}", told.condition.isPrecipitation)
        assertEquals(">0", Units.precipitationNumber(told.precipitation, PrecipitationUnit.MM, told.condition.isPrecipitation))
        assertEquals("0", Units.precipitationNumber(0.0, PrecipitationUnit.MM))
    }

    // ---- the look-back

    private val five = at("2026-10-22T16:00:00Z")   // the hour 17–18 local
    private val echoes = SpotSource.hourEchoes(steps)
    private fun gauge(mm: Double, km: Double = 6.5) = HistoryHour.Measured(11.0, mm, 10.0, 20.0, 200.0, 0.0, 90.0, Condition.CLOUDY, precipitationKm = km)

    /**
     * The gauge at Holle measured 0.3 mm, the radar's hourly sum over the place says 0.0 – but its
     * map's steps saw the showers: the radar's 0.0 does not dry the gauge's amount.
     */
    @Test fun theRadarsZeroDoesNotDryAShowerItSaw() {
        val spot = HistorySource.overSpot(mapOf(five to gauge(0.3)), mapOf(five to 0.0), emptyMap(), Long.MAX_VALUE, echoes = echoes).getValue(five)
        assertEquals(0.3, spot.precipitation!!, 1e-9)
        assertEquals(Provenance.STATION, spot.precipitationFrom)
        assertFalse(spot.trace)
        assertTrue("${spot.condition}", spot.condition!!.isPrecipitation)
    }

    /** Not known whether a shower fell over the place (no steps): a sum below a tenth leaves the gauge's amount too. */
    @Test fun withoutTheStepsTheGaugeStays() {
        val spot = HistorySource.overSpot(mapOf(five to gauge(0.3)), mapOf(five to 0.0), emptyMap(), Long.MAX_VALUE).getValue(five)
        assertEquals(0.3, spot.precipitation!!, 1e-9)
    }

    /** The steps saw nothing over the place: rain at a gauge 20 km off leaves the place dry, as before. */
    @Test fun aShowerElsewhereLeavesThePlaceDry() {
        val spot = HistorySource.overSpot(mapOf(five to gauge(0.3, 19.8)), mapOf(five to 0.0), emptyMap(), Long.MAX_VALUE, echoes = mapOf(five to 0.0)).getValue(five)
        assertEquals(0.0, spot.precipitation!!, 1e-9)
        assertEquals(Provenance.RADAR, spot.precipitationFrom)
        assertFalse(spot.condition!!.isPrecipitation)
    }

    /**
     * The gauge dry, the radar's sum 0.0, its steps a shower: precipitation fell, however little – the
     * hour reads ">0 mm" with drizzle, the day's summary too.
     */
    @Test fun aShowerNoGaugeCaughtReadsMoreThanNothing() {
        val spot = HistorySource.overSpot(mapOf(five to gauge(0.0)), mapOf(five to 0.0), emptyMap(), Long.MAX_VALUE, echoes = echoes).getValue(five)
        assertTrue(spot.trace)
        assertEquals(0.0, spot.precipitation!!, 1e-9)
        assertEquals(Condition.DRIZZLE, spot.condition)
        // and where nothing else measured the hour
        val alone = HistorySource.overSpot(emptyMap(), emptyMap(), emptyMap(), Long.MAX_VALUE, echoes = echoes).getValue(five)
        assertTrue(alone.trace)
        assertEquals(Provenance.RADAR, alone.precipitationFrom)
        val day = HistoryDay(LocalDate.of(2026, 10, 22), listOf(HistoryHour(five, spot, null), HistoryHour(five + h, gauge(0.0), null)))
        val sum = DaySummary.of(day)
        assertTrue(sum.precipitationFell)
        assertEquals(">0", Units.precipitationNumber(sum.precipitation, PrecipitationUnit.MM, sum.precipitationFell))
    }

    /** The radar's hourly sums are asked for at their full hours only: one list of times, the whole look-back at once. */
    @Test fun theFullHoursAsAList() {
        assertEquals(
            "2026-10-22T14:00:00Z,2026-10-22T15:00:00Z,2026-10-22T16:00:00Z",
            SpotSource.fullHours(at("2026-10-22T13:30:00Z"), at("2026-10-22T16:00:00Z")),
        )
    }
}

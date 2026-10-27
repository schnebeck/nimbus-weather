/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SkyBlendTest.kt
 * Tests for the sky of the hours from the model and the DWD's MOSMIX, and for the sky now.
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
import dev.nimbus.weather.data.model.SkyBasis
import dev.nimbus.weather.data.remote.BrightSkySource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.remote.Mosmix
import dev.nimbus.weather.data.remote.MosmixHour
import dev.nimbus.weather.data.remote.StationObservation
import dev.nimbus.weather.data.remote.StationSite
import dev.nimbus.weather.data.repo.NowWeather
import dev.nimbus.weather.data.repo.SkyBlend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyBlendTest {
    private val h = 3_600_000L
    private val day = 1_793_059_200_000L               // 2026-10-27 00:00 UTC
    private val herrenhausen = StationSite("HANNOVER-HERRENHAUSE", 5.3, 50.0)

    private fun hour(k: Int, sun: Double?, cloud: Double?, c: Condition = Condition.CLEAR) =
        HourlyPoint(day + k * h, 12.0, condition = c, isDay = true, precipitation = 0.0, cloudCover = cloud, sunshine = sun)

    /**
     * Hannover, a morning of low stratus: the model reckons full sun from 10 o'clock, MOSMIX at
     * Herrenhausen (5 km, 50 m) 7 to 12 minutes – the hours are not sunny.
     */
    @Test fun theStationsSkyWeighsInNearThePlace() {
        val model = listOf(hour(10, 60.0, 87.0), hour(11, 60.0, 0.0), hour(12, 60.0, 41.0))
        val mos = Mosmix(herrenhausen, listOf(
            MosmixHour(day + 10 * h, 7.0, 86.0, false), MosmixHour(day + 11 * h, 12.0, 79.0, false), MosmixHour(day + 12 * h, 11.0, 74.0, false),
        ))
        val share = SkyBlend.share(mos, 55.0)
        assertEquals(SkyBlend.MOSMIX_SHARE, share, 1e-9)
        val hours = SkyBlend.hours(model, mos, share)
        for (x in hours) {
            assertTrue("${x.sunshine}", x.sunshine!! < 22.0)
            assertNotEquals(Condition.CLEAR, x.condition)
            assertNotEquals(Condition.MOSTLY_CLEAR, x.condition)
        }
        assertEquals(0.8 * 7 + 0.2 * 60, hours[0].sunshine!!, 1e-9)
        assertEquals(0.8 * 79 + 0.2 * 0, hours[1].cloudCover!!, 1e-9)
    }

    /** The farther the station, the less its share – none from 30 km on, none at another height. */
    @Test fun aStationFarOffOrAtAnotherHeightCountsLessOrNot() {
        fun at(km: Double, m: Double) = SkyBlend.share(Mosmix(StationSite("S", km, m), emptyList()), 50.0)
        assertEquals(SkyBlend.MOSMIX_SHARE, at(10.0, 50.0), 1e-9)
        assertEquals(SkyBlend.MOSMIX_SHARE / 2, at(20.0, 50.0), 1e-9)
        assertEquals(0.0, at(30.0, 50.0), 1e-9)
        assertEquals(0.0, at(5.0, 900.0), 1e-9)
        val model = listOf(hour(11, 60.0, 0.0))
        assertEquals(model, SkyBlend.hours(model, Mosmix(herrenhausen, listOf(MosmixHour(day + 11 * h, 0.0, 100.0, true))), 0.0))
    }

    /** Fog where the two together say so; the sun that shines through it lifts it. Precipitation stays the model's. */
    @Test fun fogWhereTheTwoTogetherSaySoPrecipitationAsTheModelHasIt() {
        val mos = Mosmix(herrenhausen, listOf(MosmixHour(day + 8 * h, 0.0, 100.0, true), MosmixHour(day + 9 * h, 50.0, 20.0, true), MosmixHour(day + 10 * h, 0.0, 100.0, false)))
        val hours = SkyBlend.hours(listOf(hour(8, 0.0, 100.0, Condition.CLOUDY), hour(9, 50.0, 20.0, Condition.CLEAR), hour(10, 0.0, 100.0, Condition.RAIN)), mos, SkyBlend.MOSMIX_SHARE)
        assertEquals(Condition.FOG, hours[0].condition)
        assertEquals(Condition.CLEAR, hours[1].condition)
        assertEquals(Condition.RAIN, hours[2].condition)
        assertEquals(Condition.CLOUDY, SkyBlend.hours(listOf(hour(8, 0.0, 100.0, Condition.CLOUDY)), mos, 0.4).single().condition)
    }

    /** The quarter hours keep the model's course through the hour, their sum the hour's sunshine. */
    @Test fun theQuarterHoursSumToTheirHoursSunshine() {
        fun q(k: Int, sun: Double?) = MinutelyPoint(day + 10 * h + k * 15 * 60_000L, 0.0, sunshine = sun)
        val hourly = listOf(hour(11, 20.0, 50.0), hour(12, 8.0, 80.0))
        val quarters = SkyBlend.minutely(listOf(q(1, 15.0), q(2, 15.0), q(3, 10.0), q(4, 0.0), q(5, 0.0), q(6, 0.0), q(7, 0.0), q(8, 0.0)), hourly)
        assertEquals(20.0, quarters.take(4).sumOf { it.sunshine!! }, 1e-9)
        assertEquals(7.5, quarters[0].sunshine!!, 1e-9)
        assertEquals(0.0, quarters[3].sunshine!!, 1e-9)
        // the model's quarters without sun, the hour with: spread evenly
        assertTrue(quarters.drop(4).all { it.sunshine == 2.0 })
    }

    /** MOSMIX's hours at the station Bright Sky takes: no measured hour, no value from another station. */
    @Test fun mosmixAsBrightSkyGivesIt() {
        val json = """
            {"weather":[
             {"timestamp":"2026-10-27T09:00:00+01:00","source_id":9721,"sunshine":0,"cloud_cover":100,"condition":"dry"},
             {"timestamp":"2026-10-27T10:00:00+01:00","source_id":4135,"sunshine":7,"cloud_cover":86,"condition":"dry"},
             {"timestamp":"2026-10-27T11:00:00+01:00","source_id":4135,"sunshine":12,"cloud_cover":79,"condition":"fog","fallback_source_ids":{"sunshine":4136}},
             {"timestamp":"2026-10-27T12:00:00+01:00","source_id":4136,"sunshine":30,"cloud_cover":50,"condition":"dry"}],
             "sources":[
             {"id":9721,"observation_type":"synop","station_name":"Hannover-Herrenhause","distance":5227,"height":50.0},
             {"id":4135,"observation_type":"forecast","station_name":"HANNOVER-HERRENHAUSE","distance":5270,"height":50.0},
             {"id":4136,"observation_type":"forecast","station_name":"HANNOVER","distance":10500,"height":55.0}]}
        """.trimIndent()
        val m = BrightSkySource.parseMosmix(JsonCodec.parseToJsonElement(json))!!
        assertEquals("HANNOVER-HERRENHAUSE", m.site.name)
        assertEquals(5.27, m.site.distanceKm, 1e-9)
        assertEquals(listOf(day + 9 * h, day + 10 * h), m.hours.map { it.time })
        assertEquals(MosmixHour(day + 9 * h, 7.0, 86.0, false), m.hours[0])
        assertEquals(MosmixHour(day + 10 * h, null, 79.0, true), m.hours[1])
    }

    private val now = day + 9 * h + 16 * 60_000L
    private val model = CurrentWeather(now, 12.0, 11.0, Condition.CLEAR, true, 80.0, 9.0, 1015.0, 10.0, 20.0, 180.0, 0.0, 30000.0, 1.0, 0.0)
    private fun obs(cloud: Double?, sunshine: Double? = null) = StationObservation(
        now - 46 * 60_000L, "Hannover", 10.5, 12.0, 83.0, 9.0, 1015.0, 10.0, 20.0, 180.0, 40000.0, cloud, 0.0,
        condition = null, observedDry = true, heightM = 55.0, sunshine = sunshine,
    )

    /** The station measures the sky overcast and no sunshine is reported: no sun now, whatever the model reckons. */
    @Test fun aSkyMeasuredOvercastHasNoSunNow() {
        val c = NowWeather.mergeObservation(model, obs(100.0))
        assertEquals(Condition.CLOUDY, c.condition)
        assertEquals(SkyBasis.MEASURED_CLOUD, c.sky)
        assertEquals("Hannover", c.measured.single { it.value == dev.nimbus.weather.data.model.NowValue.SKY }.station)
        // a sky measured partly clouded may be veiled above: the model's stays
        assertEquals(Condition.CLEAR, NowWeather.mergeObservation(model, obs(50.0)).condition)
        // sunshine measured decides
        assertEquals(SkyBasis.MEASURED_SUNSHINE, NowWeather.mergeObservation(model, obs(100.0, sunshine = 50.0)).sky)
    }

    /**
     * What is measured over the place goes before a station's: the satellite saw a full hour of sun
     * over Hannover while the airport, 10.5 km off, reported 8/8 – sunny. A station within the
     * satellite's pixel measures the place itself and stays.
     */
    @Test fun theSatelliteOverThePlaceBeforeAStationFartherOff() {
        val sunny = NowWeather.SatelliteNow(60.0, day + 10 * h, sunHigh = true)
        val far = NowWeather.withSatellite(NowWeather.mergeObservation(model, obs(100.0)), sunny)
        assertEquals(Condition.CLEAR, far.condition)
        assertEquals(SkyBasis.SATELLITE, far.sky)
        assertTrue(far.measured.none { it.value == dev.nimbus.weather.data.model.NowValue.SKY })
        // a station's sunshine farther off gives way too
        assertEquals(SkyBasis.SATELLITE, NowWeather.withSatellite(NowWeather.mergeObservation(model, obs(100.0, sunshine = 0.0)), sunny).sky)
        // within the pixel: the station's
        val here = NowWeather.mergeObservation(model, obs(100.0).copy(distanceKm = 1.0))
        assertEquals(SkyBasis.MEASURED_CLOUD, NowWeather.withSatellite(here, sunny).sky)
        // partly: from 15 minutes
        assertEquals(Condition.PARTLY_CLOUDY, NowWeather.withSatellite(model, sunny.copy(minutes = 20.0)).condition)
    }

    /** No sun seen under a high sun: clouded, whatever the model reckons; under a low sun, or at night, it tells nothing. */
    @Test fun noSunSeenUnderAHighSunIsCloud() {
        val dark = NowWeather.SatelliteNow(0.0, day + 10 * h, sunHigh = true)
        assertEquals(Condition.CLOUDY, NowWeather.withSatellite(model, dark).condition)
        assertEquals(model, NowWeather.withSatellite(model, dark.copy(sunHigh = false)))
        assertEquals(Condition.FOG, NowWeather.withSatellite(model.copy(condition = Condition.FOG), dark).condition)
        assertEquals(model.copy(isDay = false), NowWeather.withSatellite(model.copy(isDay = false), dark))
        assertEquals(Condition.RAIN, NowWeather.withSatellite(model.copy(condition = Condition.RAIN), dark).condition)
    }

    /**
     * The satellite's newest hour stands for now up to 90 minutes after its end – until the next
     * one has come, 20 to 25 minutes after its own end; the sun high at noon, low at dawn.
     */
    @Test fun theSatellitesNewestHourWhileFresh() {
        val lat = 52.3759; val lon = 9.7320
        val minutes = mapOf(day + 9 * h to 18.0, day + 10 * h to 60.0, day + 11 * h to 0.0)
        val n = NowWeather.satelliteNow(minutes, lat, lon, day + 10 * h + 32 * 60_000L)!!
        assertEquals(60.0, n.minutes, 0.0)
        assertTrue(n.sunHigh)
        // the next hour not come yet, 77 minutes after the end of the one before: that one still;
        // 100 minutes after: no more
        assertEquals(18.0, NowWeather.satelliteNow(mapOf(day + 9 * h to 18.0), lat, lon, day + 10 * h + 17 * 60_000L)!!.minutes, 0.0)
        assertEquals(null, NowWeather.satelliteNow(mapOf(day + 9 * h to 18.0), lat, lon, day + 10 * h + 40 * 60_000L))
        assertFalse(NowWeather.satelliteNow(mapOf(day + 7 * h to 0.0), lat, lon, day + 7 * h + 20 * 60_000L)!!.sunHigh)
    }

    /** Nothing measured of the sky: now as the hour running now, MOSMIX weighed in. */
    @Test fun nowAsTheHourRunningNow() {
        val hours = listOf(hour(10, 17.6, 86.0, Condition.PARTLY_CLOUDY))
        val c = NowWeather.withSkyOfHour(model, hours, now)
        assertEquals(Condition.PARTLY_CLOUDY, c.condition)
        assertEquals(SkyBasis.MOSMIX, c.sky)
        // precipitation now stays the model's
        assertEquals(Condition.RAIN, NowWeather.withSkyOfHour(model.copy(condition = Condition.RAIN), hours, now).condition)
        assertFalse(NowWeather.withSkyOfHour(model, emptyList(), now).sky == SkyBasis.MOSMIX)
    }
}

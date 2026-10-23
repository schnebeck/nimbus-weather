/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/NoticesTest.kt
 * The notices tell what is news and nothing else: a warning once, a gauge per level, precipitation
 * only after hours without – and nothing that nags.
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
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.GaugeInfo
import dev.nimbus.weather.data.model.GaugeProvider
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherAlert
import dev.nimbus.weather.notify.GaugeMemory
import dev.nimbus.weather.notify.NoticeStep
import dev.nimbus.weather.notify.NoticeWork
import dev.nimbus.weather.notify.Notices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesTest {
    private val min = 60_000L
    private val h = 60 * min
    private val day = 1_792_195_200_000L                // 2026-10-17 00:00 UTC
    private val now = day + 17 * h + 30 * min           // 17:30

    private fun alert(id: String, s: AlertSeverity, source: String = "DWD") =
        WeatherAlert(id, "Amtliche WARNUNG vor WINDBÖEN", "Windböen", "", null, s, now, now + 6 * h, source)

    // ---- weather warnings

    /** A new warning from the level chosen is told once; the next check with it tells nothing. */
    @Test fun aWarningOnce() {
        val (steps, told) = Notices.warnings(listOf(alert("a", AlertSeverity.MODERATE)), 2, floods = false, emptyMap())
        assertEquals(1, steps.size)
        assertTrue((steps[0] as NoticeStep.Warning).loud)
        assertTrue(Notices.warnings(listOf(alert("a", AlertSeverity.MODERATE)), 2, floods = false, told).first.isEmpty())
    }

    /** Below the level chosen: nothing – the warnings card shows it in a line of its own. */
    @Test fun belowTheLevelNothing() {
        assertTrue(Notices.warnings(listOf(alert("a", AlertSeverity.MINOR)), 2, floods = false, emptyMap()).first.isEmpty())
        assertEquals(1, Notices.warnings(listOf(alert("a", AlertSeverity.MINOR)), 1, floods = false, emptyMap()).first.size)
    }

    /** The same warning at a higher level: told again, loud; over or lifted: taken away. */
    @Test fun worseAgainGoneAway() {
        val (_, told) = Notices.warnings(listOf(alert("a", AlertSeverity.MODERATE)), 2, floods = false, emptyMap())
        val (steps, told2) = Notices.warnings(listOf(alert("a", AlertSeverity.SEVERE)), 2, floods = false, told)
        assertTrue((steps.single() as NoticeStep.Warning).loud)
        assertEquals(NoticeStep.WarningGone("a"), Notices.warnings(emptyList(), 2, floods = false, told2).first.single())
    }

    /** The states' flood warnings only when asked for, whatever their level. */
    @Test fun floodWarningsWhenAskedFor() {
        val flood = alert("LHP:x", AlertSeverity.MINOR, source = "LHP")
        assertTrue(Notices.warnings(listOf(flood), 2, floods = false, emptyMap()).first.isEmpty())
        assertEquals(1, Notices.warnings(listOf(flood), 2, floods = true, emptyMap()).first.size)
    }

    // ---- gauges

    private fun gauge(stage: Int?, provider: GaugeProvider = GaugeProvider.NLWKN) =
        GaugeInfo("nlwkn-heinde", "HEINDE", "INNERSTE", 9.5, false, level = 241.0, alertStage = stage, provider = provider, tendency = 1)

    /** Meldestufe 1 reached: told; Meldestufe 2: told again; staying: nothing. */
    @Test fun eachLevelOnce() {
        val (s1, m1) = Notices.gauges(listOf(gauge(1)), 1, emptyMap(), now)
        assertTrue((s1.single() as NoticeStep.Gauge).loud)
        assertTrue(Notices.gauges(listOf(gauge(1)), 1, m1, now + 30 * min).first.isEmpty())
        val (s2, _) = Notices.gauges(listOf(gauge(2)), 1, m1, now + h)
        assertEquals(2, (s2.single() as NoticeStep.Gauge).stage)
    }

    /**
     * Back below: a quiet update; hovering at the level for a while no new sound; six hours below:
     * the notice goes, and a rise after that is news again.
     */
    @Test fun belowQuietlyThenGone() {
        val (_, m1) = Notices.gauges(listOf(gauge(1)), 1, emptyMap(), now)
        val (s2, m2) = Notices.gauges(listOf(gauge(0)), 1, m1, now + h)
        val below = s2.single() as NoticeStep.Gauge
        assertTrue(below.below && !below.loud)
        val (s3, m3) = Notices.gauges(listOf(gauge(1)), 1, m2, now + 2 * h)
        assertTrue("up again to the level told: no sound", !(s3.single() as NoticeStep.Gauge).loud)
        val (_, m4) = Notices.gauges(listOf(gauge(0)), 1, m3, now + 3 * h)
        val (s5, m5) = Notices.gauges(listOf(gauge(0)), 1, m4, now + 3 * h + Notices.GAUGE_CALM_MS)
        assertEquals(NoticeStep.GaugeGone("nlwkn-heinde"), s5.single())
        assertTrue((Notices.gauges(listOf(gauge(1)), 1, m5, now + 10 * h).first.single() as NoticeStep.Gauge).loud)
    }

    /** A gauge whose source did not answer keeps what was told; one without alert levels tells nothing. */
    @Test fun missingOrWithoutLevels() {
        val told = mapOf("nlwkn-heinde" to GaugeMemory(1))
        assertEquals(told, Notices.gauges(emptyList(), 1, told, now).second)
        assertTrue(Notices.gauges(listOf(gauge(null, GaugeProvider.PEGELONLINE)), 1, emptyMap(), now).first.isEmpty())
    }

    // ---- precipitation after hours without

    private val q = 15 * min
    /** The radar's measured steps of the last three hours: dry, or wet at [wetAt]. */
    private fun steps(wetAt: Long? = null, count: Int = 36) = (0 until count).associate { k ->
        val t = now - k * 5 * min
        t to if (wetAt != null && t == wetAt) 0.05 else 0.0
    }
    /** The radar's quarter hours ahead: dry, precipitation from [from] (minutes ahead) on, [peak] alone where given. */
    private fun quarters(from: Int?, mm: Double = 0.1, peak: Double? = null) = (-1..12).map { k ->
        val t = (now / q + 1) * q + k * q
        val wet = from != null && t - now >= from * min
        MinutelyPoint(t, if (wet) mm else 0.0, 12.0, peak = if (wet) peak ?: mm * 4 else 0.0)
    }
    private val hours = (17..21).map { HourlyPoint(day + it * h, 12.0, condition = Condition.PARTLY_CLOUDY, isDay = it < 18, precipitation = 0.0) }

    /** A dry afternoon, a shower in half an hour: told once – the next check, with the shower nearer, tells nothing. */
    @Test fun aDryAfternoonAndAShower() {
        val r = Notices.rain(quarters(30), hours, Condition.PARTLY_CLOUDY, steps(), emptyList(), now, day, quiet = false)
        assertNotNull(r)
        assertTrue(r!!.notice.state is dev.nimbus.weather.ui.main.Insights.Nowcast.StartsIn)
        assertNull(Notices.rain(quarters(15), hours, Condition.PARTLY_CLOUDY, steps(), listOf(now), now + 15 * min, day, quiet = false))
    }

    /** A rainy day with breaks: a wet step within three hours – nothing; falling now – nothing. */
    @Test fun aRainyDayTellsNothing() {
        assertNull(Notices.rain(quarters(30), hours, Condition.PARTLY_CLOUDY, steps(wetAt = now - 2 * h), emptyList(), now, day, quiet = false))
        assertNull(Notices.rain(quarters(30), hours, Condition.RAIN, steps(), emptyList(), now, day, quiet = false))
    }

    /** Without the radar's steps of the last hours nothing is known of dry weather: nothing. */
    @Test fun missingStepsAreNoProof() {
        assertNull(Notices.rain(quarters(30), hours, Condition.PARTLY_CLOUDY, steps(count = 10), emptyList(), now, day, quiet = false))
    }

    /** At most two a day; do not disturb (or bedtime): nothing. */
    @Test fun twoADayAndQuiet() {
        assertNull(Notices.rain(quarters(30), hours, Condition.PARTLY_CLOUDY, steps(), listOf(day + 8 * h, day + 12 * h), now, day, quiet = false))
        assertNotNull(Notices.rain(quarters(30), hours, Condition.PARTLY_CLOUDY, steps(), listOf(day + 8 * h), now, day, quiet = false))
        assertNull(Notices.rain(quarters(30), hours, Condition.PARTLY_CLOUDY, steps(), emptyList(), now, day, quiet = true))
    }

    /** Only perhaps some drizzle, or the shower further than an hour off: nothing. */
    @Test fun notSureOrNotNear() {
        assertNull(Notices.rain(quarters(30, mm = 0.01, peak = 0.12), hours, Condition.PARTLY_CLOUDY, steps(), emptyList(), now, day, quiet = false))
        assertNull(Notices.rain(quarters(90), hours, Condition.PARTLY_CLOUDY, steps(), emptyList(), now, day, quiet = false))
    }

    // ---- the check

    /** The forecast anew after ten minutes (the radar moves on), gauges and flood warnings when asked for and out of date. */
    @Test fun whatTheCheckFetches() {
        val fresh: (DataPart) -> Long? = { now - 5 * min }
        assertTrue(NoticeWork.due(Settings(notices = true), fresh, now).isEmpty())
        assertEquals(setOf(DataPart.FORECAST, DataPart.GAUGES), NoticeWork.due(Settings(notices = true), { now - 20 * min }, now))
        assertEquals(setOf(DataPart.FORECAST, DataPart.GAUGES, DataPart.FLOOD), NoticeWork.due(Settings(notices = true, noticeFloods = true), { null }, now))
    }
}

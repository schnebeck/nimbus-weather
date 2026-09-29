/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/OutlookTest.kt
 * Tests for the short text forecast.
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
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.ui.main.Outlook
import dev.nimbus.weather.ui.radar.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlookTest {
    private val now = 1_800_000_000_000L
    private fun h(i: Int, t: Double, c: Condition = Condition.CLOUDY, p: Double? = 0.0, mm: Double? = 0.0) =
        HourlyPoint(now + i * 3_600_000L, t, condition = c, isDay = true, precipitationProbability = p, precipitation = mm)

    @Test
    fun `temperature trend picks the first notable extreme`() {
        assertEquals(Outlook.Temp.Rise(26.0, now + 4 * 3_600_000L), Outlook.temperature(20.0, (1..8).map { h(it, if (it == 4) 26.0 else 22.0) }))
        assertEquals(Outlook.Temp.Fall(12.0, now + 6 * 3_600_000L), Outlook.temperature(18.0, (1..8).map { h(it, if (it == 6) 12.0 else 17.0) }))
        assertEquals(Outlook.Temp.Steady(18.0), Outlook.temperature(18.0, (1..8).map { h(it, 18.5) }))
    }

    @Test
    fun `precipitation outlook`() {
        val dry = (1..12).map { h(it, 15.0) }
        assertEquals(Outlook.Precip.Dry, Outlook.precipitation(Condition.CLOUDY, dry, emptyList(), now))
        val later = (1..12).map { h(it, 15.0, if (it >= 5) Condition.RAIN else Condition.CLOUDY, if (it >= 5) 80.0 else 5.0, if (it >= 5) 1.0 else 0.0) }
        assertEquals(Outlook.Precip.Likely(Condition.RAIN, now + 5 * 3_600_000L, 80), Outlook.precipitation(Condition.CLOUDY, later, emptyList(), now))
        val maybe = (1..12).map { h(it, 15.0, p = if (it == 7) 40.0 else 10.0) }
        assertEquals(Outlook.Precip.Possible(Condition.SHOWERS, now + 7 * 3_600_000L, 40), Outlook.precipitation(Condition.CLOUDY, maybe, emptyList(), now))
        val cold = (1..12).map { h(it, 0.0, p = if (it == 3) 45.0 else 0.0) }
        assertEquals(Condition.SNOW, (Outlook.precipitation(Condition.CLOUDY, cold, emptyList(), now) as Outlook.Precip.Possible).condition)
    }

    @Test
    fun `ongoing rain ends by nowcast`() {
        val mins = listOf(0.5, 0.3, 0.0, 0.0).mapIndexed { i, p -> MinutelyPoint(now + i * 900_000L, p) }
        val o = Outlook.precipitation(Condition.RAIN, (1..12).map { h(it, 12.0, Condition.RAIN) }, mins, now)
        assertEquals(Outlook.Precip.Ongoing(now + 30 * 60_000L), o)
    }

    @Test
    fun `map style colours are parsed and re-toned`() {
        val c = MapStyle.parseColor("rgb(27 ,27 ,29)")!!
        assertEquals(27 / 255.0, c.r, 1e-9)
        assertEquals(0.5, MapStyle.parseColor("hsla(0,0%,50%,0.5)")!!.a, 1e-9)
        assertEquals(1.0, MapStyle.parseColor("#fff")!!.g, 1e-9)
        val slate = MapStyle.toSlate(MapStyle.parseColor("rgb(12,12,12)")!!, isLine = false)
        assertTrue(slate.lightness in 0.3..0.45)
        assertTrue(slate.b > slate.r)                                   // bluish
        val road = MapStyle.toSlate(MapStyle.parseColor("hsl(0,0%,25%)")!!, isLine = true)
        assertTrue(road.lightness > slate.lightness + 0.2)            // roads clearly lighter than land
    }
}

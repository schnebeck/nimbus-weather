/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/NightTest.kt
 * Day and night behind the day charts: from the sun at the place, to the minute, the whole axis.
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

import dev.nimbus.weather.ui.main.HourAxis
import dev.nimbus.weather.ui.main.nights
import dev.nimbus.weather.util.Moon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class NightTest {
    private val h = 3_600_000L
    private val min = 60_000L
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun dayStart(d: String, zone: ZoneId = berlin) = LocalDate.parse(d).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun isNight(n: List<LongRange>, t: Long) = n.any { t in it }

    @Test fun sunriseAndSunsetToTheMinuteAsOnTheSunCard() {
        // Hannover, 1 October 2026
        val start = dayStart("2026-10-01")
        val n = nights(start, HourAxis.dayAxisEnd(start + 24 * h), 52.3759, 9.7320)
        val (rise, set) = Moon.sunTimes(start, 52.3759, 9.7320)
        // night until sunrise, day until sunset, night after it
        assertEquals(2, n.size)
        assertEquals(start, n[0].first)
        assertTrue("sunrise ${n[0].last} vs $rise", abs(n[0].last + 1 - rise!!) < 2 * min)
        assertTrue("sunset ${n[1].first} vs $set", abs(n[1].first - set!!) < 2 * min)
        // about 07:23 and 19:00 local time there
        val local = { t: Long -> java.time.Instant.ofEpochMilli(t).atZone(berlin).toLocalTime() }
        assertTrue(local(rise).hour == 7 && local(set).hour in 18..19)
    }

    @Test fun the24ColumnIsNightAfterAnAutumnDay() {
        val start = dayStart("2026-10-01")
        val n = nights(start, HourAxis.dayAxisEnd(start + 24 * h), 52.3759, 9.7320)
        // 24:00 … 01:00 (the 24 column) dark to its very end, like the 00 column
        assertTrue(isNight(n, start + 24 * h + 30 * min))
        assertEquals(start + 25 * h, n.last().last + 1)
        assertTrue(isNight(n, start + 30 * min))
        assertTrue(!isNight(n, start + 12 * h))
    }

    @Test fun midsummerAtTheNorthCapeNoNightPolarNightAllNight() {
        val oslo = ZoneId.of("Europe/Oslo")
        val june = dayStart("2026-06-21", oslo)
        assertTrue(nights(june, june + 25 * h, 71.17, 25.78).isEmpty())
        val dec = dayStart("2026-12-21", oslo)
        assertEquals(listOf(dec until dec + 25 * h), nights(dec, dec + 25 * h, 71.17, 25.78))
    }

    @Test fun nightNotOnFullHours() {
        // the old shading from hourly flags started and ended on full hours only
        val start = dayStart("2026-10-01")
        val n = nights(start, start + 25 * h, 52.3759, 9.7320)
        assertTrue(n.drop(1).any { it.first % h != 0L } || n.any { (it.last + 1) % h != 0L && it.last + 1 != start + 25 * h })
    }

    /** Every day chart takes its night from the sun ([nights]) over its whole axis – no other source. */
    @Test fun everyDayChartUsesTheSun() {
        val main = File("src/main/java/dev/nimbus/weather/ui/main")
        val src = main.listFiles()!!.filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue("nightsFromDaily/nightsFromFlags are gone", "nightsFromDaily" !in src && "nightsFromFlags" !in src)
        for (f in listOf("ForecastCards.kt", "HistoryPage.kt")) {
            val t = File(main, f).readText()
            assertTrue("$f: nights over the axis with the 24 column", Regex("""nights\([^)]*dayAxisEnd""").containsMatchIn(t))
        }
    }
}

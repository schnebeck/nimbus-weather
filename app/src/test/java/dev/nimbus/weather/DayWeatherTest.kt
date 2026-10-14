/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DayWeatherTest.kt
 * The day's weather in one line and one symbol, from its hours: the words for the stretches of the day,
 * how a day is told, and what its symbol shows.
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

import androidx.compose.ui.test.junit4.createComposeRule
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DaySymbol
import dev.nimbus.weather.data.model.DayWeather
import dev.nimbus.weather.data.model.DayWeather.Term
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.ui.main.dayWeatherText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/**
 * A day is told by its hours, not by the model's daily code (the day's worst weather): the
 * stretch where something falls, thunders or the fog lies, named by the shortest word of the day
 * that holds it, and the sky around it. The pseudo days are those of the rendered previews.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de", application = android.app.Application::class)
class DayWeatherTest {
    @get:Rule val compose = createComposeRule()
    private val h = 3_600_000L
    private val d0 = 1_790_812_800_000L

    /** A day of 24 hours by their starts; daylight 7 … 17 with [sun] minutes each. */
    private class Day(sun: Double = 55.0, sky: Condition = Condition.CLEAR) {
        val c = Array(24) { if (it in 7..17) sky else Condition.CLEAR }
        val p = DoubleArray(24)
        val sun = DoubleArray(24) { if (it in 7..17) sun else 0.0 }
        fun fall(r: IntRange, cond: Condition, mm: Double, sun: Double = 0.0) = apply { r.forEach { c[it] = cond; p[it] = mm; this.sun[it] = sun } }
    }

    /** [chance]: the chance of precipitation of every hour. */
    private fun weather(d: Day, chance: Double? = null) = DayWeather.of(
        (0..23).map { s ->
            HourlyPoint(
                d0 + (s + 1) * h, 10.0, condition = d.c[s], isDay = s in 7..17, precipitation = d.p[s],
                precipitationProbability = chance, sunshine = if (s in 7..17) d.sun[s] else null,
            )
        },
        ZoneOffset.UTC,
    )!!

    private fun texts(vararg days: Day): List<String> {
        val out = mutableListOf<String>()
        compose.setContent { days.forEach { out += dayWeatherText(weather(it)) } }
        compose.waitForIdle()
        return out
    }

    /** The words for the stretches of the day and their hours (the hour's start, until the end). */
    @Test fun theWordsOfTheDay() {
        val hours = mapOf(
            Term.EARLY to 0..4, Term.MORNING to 5..8, Term.FORENOON to 8..11, Term.NOON to 12..12, Term.AFTERNOON to 12..17,
            Term.EVENING to 18..20, Term.NIGHT to 21..23, Term.DAYTIME to 8..17, Term.LATE_FORENOON to 10..11,
            Term.LATE_AFTERNOON to 16..17, Term.EARLY_EVENING to 18..19, Term.LATE_EVENING to 20..21, Term.SMALL_HOURS to 0..2,
            Term.EARLY_FORENOON to 8..9,
        )
        hours.forEach { (t, r) -> assertEquals(t.name, r.toSet(), t.hours) }
        assertEquals((21..23).toSet() + (0..4).toSet(), Term.OVERNIGHT.hours)
        var words = emptyList<String>()
        compose.setContent { words = androidx.compose.ui.res.stringArrayResource(R.array.day_term).toList() }
        compose.waitForIdle()
        assertEquals(
            listOf("früh morgens", "früh", "morgens", "am frühen Vormittag", "vormittags", "am späten Vormittag", "mittags", "nachmittags",
                "am späten Nachmittag", "am frühen Abend", "abends", "am späten Abend", "nachts", "in der Nacht", "tagsüber", "bis mittags", "ab mittags"),
            words,
        )
    }

    /** Drizzle before dawn, then six hours of sun: no day of drizzle. */
    @Test fun drizzleBeforeDawnThenSun() {
        val d = Day(40.0, Condition.PARTLY_CLOUDY).fall(3..8, Condition.DRIZZLE, 0.15)
        assertEquals(listOf("Morgens Niesel, dann heiter"), texts(d))
        val s = weather(d).symbol
        assertEquals(DaySymbol.Sky.BRIGHT, s.sky)
        assertEquals(DaySymbol.Fall.DRIZZLE, s.fall)
    }

    /** Sunny 9 … 17, rain the rest of the day; steady rain told as such. */
    @Test fun sunnyByDayRainyOtherwise() {
        val rainy = Day().fall(0..8, Condition.RAIN, 0.5).fall(17..23, Condition.RAIN, 0.5)
        val steady = Day().fall(0..8, Condition.RAIN, 1.0).fall(17..23, Condition.RAIN, 1.0)
        assertEquals(listOf("Tagsüber sonnig, sonst regnerisch", "Tagsüber sonnig, sonst Dauerregen"), texts(rainy, steady))
    }

    /** One weather prevails, the other comes now and then. */
    @Test fun sunnyWithSomeRainRainyWithSomeSun() {
        val sunny = Day().fall(10..10, Condition.RAIN, 0.3).fall(15..15, Condition.RAIN, 0.3)
        val rainy = Day(0.0, Condition.RAIN).fall(0..23, Condition.RAIN, 0.5).fall(12..14, Condition.PARTLY_CLOUDY, 0.0, 40.0)
        assertEquals(listOf("Sonnig mit etwas Regen", "Regnerisch mit etwas Sonne"), texts(sunny, rainy))
        // the sun shows in the symbol too
        assertEquals(DaySymbol.Sky.CLOUDY, weather(rainy).symbol.sky)
    }

    /** "Mostly" leaves the other sky unsaid; a sky the same all day needs none. */
    @Test fun mostlyCloudyWithShowers() {
        val showers = Day(30.0, Condition.PARTLY_CLOUDY).apply { listOf(10, 13, 16, 19).forEach { fall(it..it, Condition.SHOWERS, 0.5, 15.0) } }
        assertEquals(listOf("Meist wolkig mit Schauern", "Heiter"), texts(showers, Day(35.0, Condition.PARTLY_CLOUDY)))
    }

    /** Sun until the afternoon, then a thunderstorm with rain. */
    @Test fun sunnyByDayThunderyWithRainInTheAfternoon() {
        val d = Day().fall(15..17, Condition.THUNDERSTORM, 4.0, 10.0)
        assertEquals(listOf("Tagsüber sonnig, nachmittags gewittrig mit Regen"), texts(d))
    }

    /** Rain from 7 until 14 o'clock is more than the morning. */
    @Test fun rainSevenToTwoIsMoreThanTheMorning() {
        val d = Day().fall(7..13, Condition.RAIN, 0.6)
        assertEquals(listOf("Bis mittags Regen, dann sonnig"), texts(d))
    }

    /** Too few hours tell nothing of the day (its symbol stays the model's); without sunshine, the clouds tell the sky. */
    @Test fun tooFewHoursAndNoSunshine() {
        val half = (0..10).map { HourlyPoint(d0 + (it + 1) * h, 10.0, condition = Condition.CLEAR, isDay = it in 7..17, sunshine = 55.0) }
        assertEquals(null, DayWeather.of(half, ZoneOffset.UTC))
        val clouds = (0..23).map { HourlyPoint(d0 + (it + 1) * h, 10.0, condition = Condition.CLEAR, isDay = it in 7..17, cloudCover = 10.0) }
        assertEquals(DaySymbol.Sky.SUNNY, DayWeather.of(clouds, ZoneOffset.UTC)!!.symbol.sky)
    }

    /** One symbol for all of it: sky, rain or snow, thunder, fog – the fog beside the drops. */
    @Test fun theSymbolHoldsItAll() {
        val storm = weather(Day().fall(1..3, Condition.THUNDERSTORM, 3.0)).symbol
        assertEquals(DaySymbol.Sky.SUNNY, storm.sky)
        assertTrue(storm.thunder)
        val snow = weather(Day(0.0, Condition.SNOW).fall(0..23, Condition.SNOW, 1.0)).symbol
        assertEquals(DaySymbol.Fall.SNOW, snow.fall)
        assertEquals(3, snow.amount)
        val foggy = Day(0.0, Condition.FOG).fall(0..23, Condition.DRIZZLE, 0.15).apply { (0..23 step 2).forEach { c[it] = Condition.FOG } }
        val fog = weather(foggy).symbol
        assertTrue(fog.fog)
        assertEquals(DaySymbol.Fall.DRIZZLE, fog.fall)
        assertEquals(listOf("Nieselig"), texts(foggy))
    }

    /**
     * The drops count the day's amount: neither the hours it is spread over nor its chance make
     * more of it – a day sure to bring 1.8 mm is a day of little rain, 6 mm in two showers are
     * as much as 6 mm all afternoon.
     */
    @Test fun theDropsCountTheAmount() {
        fun drops(d: Day, chance: Double) = weather(d, chance).symbol.amount
        assertEquals(1, drops(Day().fall(9..14, Condition.RAIN, 0.3), chance = 100.0))
        assertEquals(1, drops(Day().fall(9..14, Condition.RAIN, 0.3), chance = 20.0))
        assertEquals(2, drops(Day().fall(15..16, Condition.SHOWERS, 3.0), chance = 40.0))
        assertEquals(2, drops(Day().fall(12..23, Condition.RAIN, 0.5), chance = 40.0))
        assertEquals(3, drops(Day().fall(15..17, Condition.SHOWERS, 4.0), chance = 30.0))
    }
}

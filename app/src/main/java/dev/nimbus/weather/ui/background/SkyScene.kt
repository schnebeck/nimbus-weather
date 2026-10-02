/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/background/SkyScene.kt
 * What the animated background shows: sky colours, clouds, precipitation, wind.
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

package dev.nimbus.weather.ui.background

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.WeatherData
import kotlin.math.abs

/**
 * Everything the animated background needs to know about the weather.
 * @param daylight 0 = night, 1 = full day (smooth around sunrise/sunset)
 * @param twilight 0..1 strength of sunrise/sunset glow
 * @param wind -1..1 horizontal drift/slant of clouds and precipitation (sign = direction)
 * @param gustiness 0..1 how much stronger gusts are than the mean wind
 */
data class SkyScene(
    val condition: Condition,
    val daylight: Float,
    val twilight: Float,
    val wind: Float,
    val gustiness: Float = 0f,
    val season: Season = Season.SUMMER,
    val temperature: Double = 15.0,
    /** 0..1 amount of airborne pollen (from CAMS pollen data). */
    val pollen: Float = 0f,
    /** 0 = early autumn (green/yellow leaves) .. 1 = late autumn (brown leaves). */
    val autumnProgress: Float = 0.5f,
    /** Real moon phase 0..1 (0 new, 0.5 full) and whether the moon is above the horizon. */
    val moonPhase: Float = 0.3f,
    val moonUp: Boolean = true,
    val southern: Boolean = false,
) {
    /** Seasonal particles – only when it is dry. */
    val ambient: Ambient
        get() {
            if (condition.isPrecipitation || condition == Condition.FOG) return Ambient.NONE
            return when (season) {
                Season.SPRING -> if (!isNight) Ambient.BLOSSOMS else Ambient.NONE
                Season.SUMMER -> if (isNight) (if (temperature >= 14.0) Ambient.FIREFLIES else Ambient.NONE) else Ambient.SEEDS
                Season.AUTUMN -> Ambient.LEAVES
                Season.WINTER -> if (temperature <= -2.0 && cloudiness <= 0.5f) Ambient.ICE_CRYSTALS else Ambient.NONE
            }
        }

    /** Pollen is only visible when it is dry. */
    val visiblePollen: Float
        get() = if (condition.isPrecipitation || condition == Condition.FOG) 0f else pollen

    val isNight: Boolean get() = daylight < 0.5f

    /** 0 (no clouds) .. 1 (overcast). */
    val cloudiness: Float
        get() = when (condition) {
            Condition.CLEAR -> 0f
            Condition.MOSTLY_CLEAR -> 0.25f
            Condition.PARTLY_CLOUDY -> 0.5f
            Condition.FOG -> 0.7f
            Condition.CLOUDY -> 0.9f
            Condition.DRIZZLE, Condition.SHOWERS, Condition.SNOW, Condition.SLEET -> 0.85f
            else -> 1f
        }

    /** Luminance of the upper sky plus what daytime clouds add: ~0.05 (night) .. ~0.6 (overcast day, fog, snow). */
    val brightness: Float
        get() = skyColors[1].luminance() * 0.6f + (if (cloudiness > 0.6f && !isNight) 0.25f else 0f)

    /**
     * The brightest thing that can be behind the cards: the sky and the clouds (white on a fair
     * day) or fog – the glass is made dark enough for it ([dev.nimbus.weather.ui.theme.Legibility]).
     * The moon (small) and a lightning flash (a moment) are left out.
     */
    val brightestBehind: Color
        get() {
            val day = daylight.coerceIn(0f, 1f)
            val candidates = buildList {
                addAll(skyColors)
                if (cloudiness > 0f) add(lerp(cloudColor(condition, true), cloudColor(condition, false), day))
                if (condition == Condition.FOG) add(lerp(Color(0xFF6B7482), Color(0xFFE6EAEE), day))
            }
            return candidates.maxBy { dev.nimbus.weather.ui.theme.Contrast.luminance(it) }
        }

    /** The same for the header, which the sun can be behind as well (it stands in the upper sky). */
    val brightestBehindHeader: Color
        get() {
            val day = daylight.coerceIn(0f, 1f)
            val sun = if (sunVisible(condition) && day > 0.02f) lerp(skyColors[0], Color(0xFFFFF8E1), day) else null
            return listOfNotNull(brightestBehind, sun).maxBy { dev.nimbus.weather.ui.theme.Contrast.luminance(it) }
        }

    /** Glass of the cards for this sky: as dark as the brightest thing behind it requires. */
    val cardFill: Color get() = dev.nimbus.weather.ui.theme.Legibility.cardFill(brightestBehind)

    /** Opacity of the shade behind the header for this sky. */
    val headerShade: Float get() = dev.nimbus.weather.ui.theme.Legibility.headerShade(brightestBehindHeader)

    val skyColors: List<Color>
        get() {
            val day = dayPalette(condition)
            val night = nightPalette(condition)
            val base = day.indices.map { lerp(night[it], day[it], daylight) }
            if (twilight <= 0f) return base
            val glowStrength = twilight * (1f - cloudiness * 0.75f)
            val glow = listOf(Color(0xFF3A4A86), Color(0xFFB7688A), Color(0xFFF3A263))
            return base.indices.map { lerp(base[it], glow[it], glowStrength * (0.35f + 0.3f * it)) }
        }

    companion object {
        /** Clouds as the background draws them. */
        fun cloudColor(condition: Condition, night: Boolean): Color = if (!night) when (condition) {
            Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY -> Color(0xFFFFFFFF)
            Condition.CLOUDY, Condition.FOG -> Color(0xFFD5DCE4)
            Condition.SNOW, Condition.HEAVY_SNOW -> Color(0xFFE2E8EF)
            Condition.DRIZZLE, Condition.SHOWERS -> Color(0xFFA9B4C0)
            Condition.THUNDERSTORM -> Color(0xFF5C6470)
            else -> Color(0xFF8A96A3)
        } else when (condition) {
            Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY -> Color(0xFF6A7690)
            Condition.CLOUDY, Condition.FOG, Condition.SNOW, Condition.HEAVY_SNOW -> Color(0xFF4E5868)
            Condition.THUNDERSTORM -> Color(0xFF2A2F38)
            else -> Color(0xFF3B4452)
        }

        /** The sun is drawn on clear to partly cloudy days. */
        fun sunVisible(condition: Condition) = condition in setOf(Condition.CLEAR, Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY)

        fun dayPalette(c: Condition): List<Color> = when (c) {
            Condition.CLEAR, Condition.MOSTLY_CLEAR -> listOf(Color(0xFF1C5DC2), Color(0xFF3F8BDD), Color(0xFF86C1F0))
            Condition.PARTLY_CLOUDY -> listOf(Color(0xFF2E68B4), Color(0xFF5B92CF), Color(0xFF9DC3E6))
            Condition.CLOUDY -> listOf(Color(0xFF55667A), Color(0xFF788797), Color(0xFF9EABB7))
            Condition.FOG -> listOf(Color(0xFF6E7985), Color(0xFF8C96A0), Color(0xFFAEB6BE))
            Condition.DRIZZLE, Condition.SHOWERS -> listOf(Color(0xFF465A70), Color(0xFF627488), Color(0xFF8394A5))
            Condition.RAIN, Condition.FREEZING_RAIN, Condition.SLEET -> listOf(Color(0xFF3B4858), Color(0xFF55636F), Color(0xFF717E89))
            Condition.HEAVY_RAIN -> listOf(Color(0xFF2C3642), Color(0xFF444F5B), Color(0xFF5C6772))
            Condition.THUNDERSTORM -> listOf(Color(0xFF1E232D), Color(0xFF2F3541), Color(0xFF474E5B))
            Condition.SNOW, Condition.HEAVY_SNOW -> listOf(Color(0xFF5E7288), Color(0xFF8295A9), Color(0xFFADBBCA))
        }

        fun nightPalette(c: Condition): List<Color> = when (c) {
            Condition.CLEAR, Condition.MOSTLY_CLEAR -> listOf(Color(0xFF040A1C), Color(0xFF0C1836), Color(0xFF1B2B52))
            Condition.PARTLY_CLOUDY -> listOf(Color(0xFF0A1128), Color(0xFF16213F), Color(0xFF26345A))
            Condition.CLOUDY, Condition.FOG -> listOf(Color(0xFF12171F), Color(0xFF212833), Color(0xFF323B48))
            Condition.SNOW, Condition.HEAVY_SNOW -> listOf(Color(0xFF151C28), Color(0xFF26303F), Color(0xFF3A4556))
            Condition.THUNDERSTORM -> listOf(Color(0xFF07090D), Color(0xFF12151B), Color(0xFF1F242C))
            else -> listOf(Color(0xFF0D1118), Color(0xFF191F29), Color(0xFF28303B))
        }

        /** Builds the scene for "now" at the place of [data]. */
        fun from(data: WeatherData, now: Long = System.currentTimeMillis()): SkyScene {
            val today = data.daily.lastOrNull { it.date <= now } ?: data.daily.firstOrNull()
            val sunrise = today?.sunrise
            val sunset = today?.sunset
            val ramp = 40 * 60_000f
            val daylight: Float
            val twilight: Float
            if (sunrise != null && sunset != null) {
                val sinceRise = (now - sunrise) / ramp
                val toSet = (sunset - now) / ramp
                daylight = (minOf(sinceRise, toSet) * 0.5f + 0.5f).coerceIn(0f, 1f)
                val d = minOf(abs(now - sunrise), abs(now - sunset)) / (50 * 60_000f)
                twilight = (1f - d).coerceIn(0f, 1f)
            } else {
                daylight = if (data.current.isDay) 1f else 0f
                twilight = 0f
            }
            val w = data.current.windSpeed ?: 10.0
            val dir = data.current.windDirection ?: 270.0
            // Wind from the west pushes things to the right (east).
            val east = -kotlin.math.sin(Math.toRadians(dir)).toFloat()
            // 0 km/h -> 0.08 (almost still), ~60 km/h (Bft 8) and more -> 1.
            val strength = (w / 60.0).toFloat().coerceIn(0.08f, 1f)
            val gust = data.current.windGust
            val gustiness = if (gust != null && gust > w) ((gust - w) / 40.0).toFloat().coerceIn(0f, 1f) else 0f
            val zone = runCatching { java.time.ZoneId.of(data.timezone) }.getOrDefault(java.time.ZoneId.systemDefault())
            val date = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val (season, autumnProgress) = seasonOf(date, data.place.latitude < 0)
            val pollenSum = data.airQuality?.pollen?.values?.sum() ?: 0.0
            val pollen = (kotlin.math.log10(1.0 + pollenSum) / 2.5).toFloat().coerceIn(0f, 1f)
            return SkyScene(
                condition = data.current.condition,
                daylight = daylight,
                twilight = twilight,
                wind = (east * strength).coerceIn(-1f, 1f),
                gustiness = gustiness,
                season = season,
                temperature = data.current.temperature,
                pollen = pollen,
                autumnProgress = autumnProgress,
                moonPhase = dev.nimbus.weather.util.Moon.preciseIllumination(now).phase.toFloat(),
                moonUp = dev.nimbus.weather.util.Moon.altitude(now, data.place.latitude, data.place.longitude) > 0.0,
                southern = data.place.latitude < 0,
            )
        }

        /** Meteorological seasons; mirrored on the southern hemisphere. */
        fun seasonOf(date: java.time.LocalDate, southern: Boolean): Pair<Season, Float> {
            val d = if (southern) date.plusMonths(6) else date
            val season = when (d.monthValue) {
                3, 4, 5 -> Season.SPRING
                6, 7, 8 -> Season.SUMMER
                9, 10, 11 -> Season.AUTUMN
                else -> Season.WINTER
            }
            val autumnStart = java.time.LocalDate.of(d.year, 9, 1)
            val progress = (java.time.temporal.ChronoUnit.DAYS.between(autumnStart, d) / 90f).coerceIn(0f, 1f)
            return season to progress
        }
    }
}

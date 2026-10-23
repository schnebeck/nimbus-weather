/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetContent.kt
 * What the widget shows of a place's weather – the same values and words as the weather page.
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

package dev.nimbus.weather.widget

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.windiness
import dev.nimbus.weather.ui.main.Insights

/**
 * A place's weather for the widget: the header's values ([temperature], [condition] with [wind],
 * the day's [max] and [min]) – with a precipitation symbol the running hour's [chance] in percent
 * as the hour strip writes it –, the [rain] and sunshine of the next 90 minutes and the chosen
 * [pollen] of today. [asOf]: when the forecast was fetched.
 */
data class WidgetWeather(
    val place: String,
    val myLocation: Boolean,
    val timezone: String,
    val temperature: Double,
    val condition: Condition,
    val isDay: Boolean,
    val wind: Float,
    val max: Double?,
    val min: Double?,
    val chance: String?,
    val rain: Rain?,
    val pollen: List<Pair<PollenType, Float>>,
    val asOf: Long,
) {
    /** The chosen types that fly at all today, the strongest first – the others are no news. */
    val pollenFlying: List<Pair<PollenType, Float>> get() = pollen.filter { it.second > 0f }.sortedByDescending { it.second }

    /**
     * Six quarter hours on the clock's quarters, [from] the running one's start (10:15 at 10:20)
     * [to] 90 minutes later: the [quarters], each by the time the precipitation notice reads it at
     * – the very quarter hours it looks at, so bars and words agree (the radar's nowcast in them
     * where it covers them); [notice] as the precipitation card words it, looking just as far –
     * null: it stays dry until [to]. [now] lies in the first quarter hour. [chance]: the chance of
     * the hour the notice's precipitation falls in ([chanceAt]); null: none known, or dry.
     */
    data class Rain(
        val from: Long, val to: Long, val quarters: List<Quarter>, val notice: Insights.PrecipNotice?, val now: Long = from,
        val chance: String? = null,
    ) {
        /** Anything to draw: sunshine or precipitation in one of the quarter hours. */
        val eventful: Boolean get() = quarters.any { (it.sunMinutes ?: 0.0) >= SUN_LEAST || it.wet || it.possible }

        companion object {
            const val SPAN_MS = 90 * 60_000L
            /** Less sunshine than this in a quarter hour is none to draw. */
            const val SUN_LEAST = 0.5
        }
    }

    /**
     * A quarter hour from [at]: precipitation in [mmPerHour], [sunMinutes] of sunshine (0–15) – the
     * model's own quarter hour, else a quarter of its hour's; null: none known. [peak]: the radar's
     * strongest 5-minute step (mm/h), null where the radar does not tell it.
     */
    data class Quarter(val at: Long, val mmPerHour: Double, val sunMinutes: Double?, val peak: Double? = null) {
        private val point get() = dev.nimbus.weather.data.model.MinutelyPoint(at, mmPerHour / 4, peak = peak)
        /** Precipitation, as the notice counts it ([Insights.wet]). */
        val wet: Boolean get() = Insights.wet(point)
        /** Only an echo too weak to be sure: perhaps drizzle ([Insights.possible]). */
        val possible: Boolean get() = Insights.possible(point)
        /** The bar's rate: the strongest 5 minutes where the radar tells them, else the quarter's. */
        val rate: Double get() = maxOf(mmPerHour, peak ?: 0.0)
    }

    companion object {
        private const val QUARTER_MS = 15 * 60_000L
        private const val DAY_MS = 24 * 3_600_000L

        /** [data] at [now], with the [pollenTypes] chosen for the widget (in their order). */
        fun of(data: WeatherData, now: Long, pollenTypes: List<PollenType>): WidgetWeather {
            val c = data.current
            val today = data.daily.lastOrNull { it.date <= now } ?: data.daily.firstOrNull()
            return WidgetWeather(
                place = data.place.name,
                myLocation = data.place.isCurrentLocation,
                timezone = data.timezone,
                temperature = c.temperature,
                condition = c.condition,
                isDay = c.isDay,
                wind = windiness(c.windSpeed, c.windGust),
                max = today?.tempMax,
                min = today?.tempMin,
                chance = if (c.condition.isPrecipitation) chance(data, now) else null,
                rain = rain(data, now),
                pollen = pollen(data, now, pollenTypes),
                asOf = data.fetchedAt(DataPart.FORECAST),
            )
        }

        /** The quarter hours reaching into the next 90 minutes; null when the forecast no longer covers them. */
        fun rain(data: WeatherData, now: Long): Rain? {
            // the clock's quarters (every zone's offset is whole quarter hours)
            val from = now / QUARTER_MS * QUARTER_MS
            val to = from + Rain.SPAN_MS
            val quarters = Insights.nowcastPoints(data.minutely, now).filter { it.time >= from && it.time < to }
            if (quarters.isEmpty() || quarters.last().time + QUARTER_MS < to) return null
            // the words of the weather page's card ("raining" as the page has it: by the sky now)
            val notice = Insights.precipNotice(
                data.minutely, data.hourly, data.current.condition, now, data.current.condition.isPrecipitation, horizonMs = to - now,
            )
            // the hour it starts in – falling now: the running one; not for perhaps some drizzle (the
            // radar's weak echo, not the model's precipitation the chance is of)
            val chance = notice?.takeIf { !it.possible }?.let { n -> chanceAt(data, if (n.state is Insights.Nowcast.StartsIn) n.at ?: now else now) }
            return Rain(from, to, quarters.map { q ->
                // the hour a quarter lies in: the data point at its end
                val hourSun = data.hourly.firstOrNull { it.time > q.time }?.sunshine?.let { it / 4 }
                Quarter(q.time, q.precipitation * 4, (q.sunshine ?: hourSun)?.coerceIn(0.0, 15.0), q.peak)
            }, notice, now, chance)
        }

        /**
         * The running hour's chance as the hour strip writes it ("40", "<10") – an hour the radar
         * tells with the model's chance, as the day chart's line has it; null: none known.
         */
        fun chance(data: WeatherData, now: Long): String? = chanceAt(data, now)

        /**
         * The chance of the hour running at [time], written as [chance] writes it; none at 0: beside
         * precipitation the radar sees (or the symbol shows) "0%" says nothing but a contradiction.
         */
        fun chanceAt(data: WeatherData, time: Long): String? {
            val hour = data.hourly.firstOrNull { it.time > time } ?: return null
            return Insights.chanceText(hour.precipitationProbability ?: hour.modelChance, hour.precipitation)?.takeIf { it != "0" }
        }

        /** Today's level (0..3) of each of [types] the forecast gives; the others are left out. */
        fun pollen(data: WeatherData, now: Long, types: List<PollenType>): List<Pair<PollenType, Float>> {
            val today = data.pollen?.days?.firstOrNull { it.date + DAY_MS > now } ?: return emptyList()
            return types.mapNotNull { t -> today.levels[t]?.let { t to it } }
        }
    }
}

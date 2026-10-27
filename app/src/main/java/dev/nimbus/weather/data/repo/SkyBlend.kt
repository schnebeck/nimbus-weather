/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/SkyBlend.kt
 * The sky of the hours to come: the model's and the DWD's MOSMIX weighed by what each does best.
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

package dev.nimbus.weather.data.repo

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.MinutelyPoint
import dev.nimbus.weather.data.model.Representative
import dev.nimbus.weather.data.model.WeatherCodes
import dev.nimbus.weather.data.remote.Mosmix

/**
 * The sun, the cloud and the fog of the hours to come, from two forecasts by what each does best.
 *
 * MOSMIX: the DWD reads its models (ICON, ECMWF IFS) at each station against years of what the
 * station measured, and in the first hours against what it measures now. That corrects where a
 * model errs the same way again and again – low stratus and fog that it clears too early, sunshine
 * it reckons from the direct beam alone – but it holds for the station, by the hour.
 *
 * The model: the place itself, in quarter hours; the precipitation stays its own (and the radar's).
 *
 * So the sunshine and the cloud cover of an hour are MOSMIX's at [MOSMIX_SHARE], the model's for
 * the rest – MOSMIX's share less the farther its station ([NEAR_KM] to [FAR_KM]), none where it
 * stands at another height than the place ([Representative.height]). Fog where the two together
 * say so. The quarter hours keep the model's course through the hour, summing to the hour's
 * sunshine.
 */
object SkyBlend {
    /** MOSMIX's share of an hour's sunshine and cloud cover, its station at the place. */
    const val MOSMIX_SHARE = 0.8
    /** Up to this distance the station's sky is the place's … */
    const val NEAR_KM = 10.0
    /** … from here on no more (in between less and less). */
    const val FAR_KM = 30.0

    /** MOSMIX's share at a place at [elevationM] metres: by its station's distance and height. */
    fun share(mosmix: Mosmix, elevationM: Double?): Double {
        val site = mosmix.site
        if (!Representative.height(site.heightM, elevationM)) return 0.0
        val near = ((FAR_KM - site.distanceKm) / (FAR_KM - NEAR_KM)).coerceIn(0.0, 1.0)
        return MOSMIX_SHARE * near
    }

    /**
     * The [hourly] of the model with [mosmix]'s sky weighed in ([share]): the sunshine and the
     * cloud cover, the sky's condition from them – fog where the two together say so; the sunshine
     * brightens it ([WeatherCodes.withSunshine]). Precipitation and thunder stay the model's.
     */
    fun hours(hourly: List<HourlyPoint>, mosmix: Mosmix?, share: Double): List<HourlyPoint> {
        if (mosmix == null || share <= 0.0) return hourly
        val byTime = mosmix.hours.associateBy { it.time }
        return hourly.map { h ->
            val m = byTime[h.time] ?: return@map h
            fun mix(model: Double?, mos: Double?) = when {
                model == null -> mos
                mos == null -> model
                else -> share * mos + (1 - share) * model
            }
            val sun = mix(h.sunshine, m.sunshine)
            val cloud = mix(h.cloudCover, m.cloudCover)
            val condition = if (!WeatherCodes.isSky(h.condition)) h.condition else {
                val fog = share * (if (m.fog) 1.0 else 0.0) + (1 - share) * (if (h.condition == Condition.FOG) 1.0 else 0.0) >= 0.5
                WeatherCodes.withSunshine(if (fog) Condition.FOG else WeatherCodes.sky(cloud ?: 100.0), sun)
            }
            h.copy(sunshine = sun, cloudCover = cloud, condition = condition)
        }
    }

    /**
     * The quarter hours' sunshine ([minutely]) summing to their hour's in [hourly]: the model's
     * course through the hour kept, its amount the hour's – which the model's own trees
     * ([dev.nimbus.weather.data.remote.ForecastSunshine]) and MOSMIX ([hours]) have read. Where
     * the model's quarters have no sun but the hour has, it is spread evenly.
     */
    fun minutely(minutely: List<MinutelyPoint>, hourly: List<HourlyPoint>): List<MinutelyPoint> {
        if (minutely.isEmpty()) return minutely
        // a quarter (its end) belongs to the hour ending at or after it
        fun hourOf(t: Long) = (t + HOUR_MS - 1) / HOUR_MS * HOUR_MS
        val hourSun = hourly.associate { it.time to it.sunshine }
        val quarters = minutely.groupBy { hourOf(it.time) }
        return minutely.map { q ->
            val raw = q.sunshine ?: return@map q
            val hour = hourOf(q.time)
            val target = hourSun[hour] ?: return@map q
            val known = quarters.getValue(hour).mapNotNull { it.sunshine }
            // an hour cut off at the ends: its known quarters stand for all four
            val rawHour = known.sum() * QUARTERS / known.size
            val sun = if (rawHour > 0.0) raw * target / rawHour else target / QUARTERS
            q.copy(sunshine = sun.coerceIn(0.0, QUARTER_MINUTES))
        }
    }

    private const val HOUR_MS = 3_600_000L
    private const val QUARTERS = 4
    private const val QUARTER_MINUTES = 15.0
}

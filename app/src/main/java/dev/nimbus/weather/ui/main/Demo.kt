/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/Demo.kt
 * Demo overrides of weather and sky (visual QA), and the sky while no data are there.
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

package dev.nimbus.weather.ui.main

import androidx.compose.foundation.background
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.Demo
import dev.nimbus.weather.ui.background.SkyScene
import java.util.Calendar

/** Applies demo overrides (visual QA) to the real weather. */
fun WeatherData.withDemo(demo: Demo?): WeatherData {
    if (demo == null) return this
    var c = current
    if (demo.condition != null) c = c.copy(condition = demo.condition, isDay = !demo.night)
    if (demo.wind != null) c = c.copy(windSpeed = kotlin.math.abs(demo.wind) * 60.0, windGust = kotlin.math.abs(demo.wind) * 85.0)
    return copy(current = c)
}

fun SkyScene.withDemo(demo: Demo?): SkyScene {
    if (demo == null) return this
    return copy(
        condition = demo.condition ?: condition,
        daylight = if (demo.condition != null) (if (demo.night) 0f else 1f) else daylight,
        twilight = if (demo.condition != null) 0f else twilight,
        season = demo.season ?: season,
        wind = demo.wind ?: wind,
        pollen = demo.pollen ?: pollen,
        temperature = if (demo.season == dev.nimbus.weather.ui.background.Season.WINTER) -5.0
        else if (demo.season == dev.nimbus.weather.ui.background.Season.SUMMER) 20.0 else temperature,
        autumnProgress = if (demo.season == dev.nimbus.weather.ui.background.Season.AUTUMN) 0.5f else autumnProgress,
    )
}

/** Scene used while no data is available yet. */
fun placeholderScene(): SkyScene {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val (season, autumn) = SkyScene.seasonOf(java.time.LocalDate.now(), southern = false)
    return SkyScene(
        Condition.PARTLY_CLOUDY, if (h in 7..19) 1f else 0f, 0f, 0.25f, season = season, autumnProgress = autumn,
        moonPhase = dev.nimbus.weather.util.Moon.preciseIllumination(System.currentTimeMillis()).phase.toFloat(),
    )
}

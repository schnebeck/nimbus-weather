/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/TodayMeasured.kt
 * Today's station readings (DWD via Bright Sky) for the day charts: measured next to forecast.
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

import dev.nimbus.weather.data.remote.History
import java.time.LocalDate

/**
 * Hourly readings of the nearest DWD station for today, keyed by the hour's time stamp (sums
 * cover the hour before it, like the model values): precipitation in mm, pressure in hPa.
 */
data class TodayMeasured(val precipitation: Map<Long, Double>, val pressure: Map<Long, Double>, val station: String?) {
    companion object {
        fun of(history: History?, today: LocalDate): TodayMeasured? {
            val day = history?.days?.firstOrNull { it.date == today } ?: return null
            val precip = day.hours.mapNotNull { h -> h.measured?.precipitation?.let { h.time to it } }.toMap()
            val pressure = day.hours.mapNotNull { h -> h.measured?.pressure?.let { h.time to it } }.toMap()
            if (precip.isEmpty() && pressure.isEmpty()) return null
            return TodayMeasured(precip, pressure, history.stationName)
        }
    }
}

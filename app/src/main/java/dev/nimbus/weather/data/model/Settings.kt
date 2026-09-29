/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/model/Settings.kt
 * User settings: forecast model, units (defaults by country) and options.
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

package dev.nimbus.weather.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ForecastModel(val openMeteoId: String) {
    DWD_ICON("icon_seamless"),
    BEST_MATCH("best_match"),
    ECMWF("ecmwf_ifs025"),
    METEO_FRANCE("meteofrance_seamless"),
}

@Serializable
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

@Serializable
enum class WindUnit { KMH, MS, MPH, KNOTS, BEAUFORT }

@Serializable
enum class PrecipitationUnit { MM, INCH }

@Serializable
data class Settings(
    val model: ForecastModel = ForecastModel.DWD_ICON,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    val useStationObservations: Boolean = true,
    val animationsEnabled: Boolean = true,
    /** Load the radar loop in the background once the location is known (Wi-Fi only). */
    val preloadRadar: Boolean = true,
) {
    companion object {
        private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

        /** Defaults for a first start: units as customary in the country of the device locale. */
        fun forLocale(locale: java.util.Locale): Settings {
            val country = locale.country.uppercase()
            val imperial = country in FAHRENHEIT_COUNTRIES
            return Settings(
                temperatureUnit = if (imperial) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS,
                windUnit = if (imperial || country == "GB") WindUnit.MPH else WindUnit.KMH,
                precipitationUnit = if (imperial) PrecipitationUnit.INCH else PrecipitationUnit.MM,
            )
        }
    }
}

/** Models shown in the model comparison chart (Open-Meteo ids and display names). */
val ComparisonModels = listOf(
    "icon_d2" to "ICON-D2 (DWD)",
    "icon_eu" to "ICON-EU (DWD)",
    "ecmwf_ifs025" to "ECMWF IFS",
    "meteofrance_seamless" to "ARPEGE/AROME",
    "ukmo_seamless" to "UK Met Office",
    "knmi_seamless" to "KNMI Harmonie",
    "metno_seamless" to "MET Norway",
    "gfs_seamless" to "NOAA GFS",
)

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

/**
 * Cards of the weather page that can be sorted and switched off in the settings. [TILES] is the
 * block of small tiles (feels like … pressure), which have an order of their own.
 */
@Serializable
enum class WeatherCard {
    ALERTS, HOURLY, DAILY, PRECIPITATION, RADAR, FEELS_LIKE, UV_INDEX, WIND, HUMIDITY, VISIBILITY, PRESSURE,
    SUN, MOON, AIR_QUALITY, POLLEN, GAUGES, BATHING, COMMUNITY, MODELS, TILES, PRESSURE_CHART;

    companion object {
        /** Default order of the page below the alerts (which always stay on top). */
        val DEFAULT_ORDER = listOf(HOURLY, DAILY, PRECIPITATION, RADAR, TILES, PRESSURE_CHART, SUN, MOON, AIR_QUALITY, POLLEN, GAUGES, BATHING, COMMUNITY, MODELS)
        /** Alternatives that are off until switched on (e.g. the pressure chart next to the small tile). */
        val OPT_IN = setOf(PRESSURE_CHART)
        val DEFAULT_TILES = listOf(FEELS_LIKE, UV_INDEX, WIND, HUMIDITY, VISIBILITY, PRESSURE)
    }
}

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
    /** Pollen types shown in the pollen card (at least one). */
    val pollenTypes: Set<PollenType> = PollenType.entries.toSet(),
    /** Cards the user switched off. */
    val hiddenCards: Set<WeatherCard> = emptySet(),
    /** Opt-in cards ([WeatherCard.OPT_IN]) the user switched on. */
    val enabledCards: Set<WeatherCard> = emptySet(),
    /** Own order of the cards (see [orderedCards]); empty = default. */
    val cardOrder: List<WeatherCard> = emptyList(),
    /** Own order of the small tiles (see [orderedTiles]); empty = default. */
    val tileOrder: List<WeatherCard> = emptyList(),
    /** Radius of the bathing water card in km. */
    val bathingRadiusKm: Int = 50,
    /** Favourite bathing waters (EU ids), shown at any distance. */
    val bathingFavorites: Set<String> = emptySet(),
) {
    fun shows(card: WeatherCard) = if (card in WeatherCard.OPT_IN) card in enabledCards else card !in hiddenCards

    /** Switches a card on or off. */
    fun withCard(card: WeatherCard, on: Boolean): Settings =
        if (card in WeatherCard.OPT_IN) copy(enabledCards = if (on) enabledCards + card else enabledCards - card)
        else copy(hiddenCards = if (on) hiddenCards - card else hiddenCards + card)

    /** The page order: the user's, cards added in later versions at their default place. */
    fun orderedCards(): List<WeatherCard> = merged(cardOrder, WeatherCard.DEFAULT_ORDER)
    fun orderedTiles(): List<WeatherCard> = merged(tileOrder, WeatherCard.DEFAULT_TILES)

    companion object {
        /** [own] order with the cards it lacks inserted after their default predecessor. */
        fun merged(own: List<WeatherCard>, default: List<WeatherCard>): List<WeatherCard> {
            val out = own.filter { it in default }.distinct().toMutableList()
            default.forEachIndexed { i, c ->
                if (c in out) return@forEachIndexed
                val before = default.subList(0, i).lastOrNull { it in out }
                out.add(before?.let { out.indexOf(it) + 1 } ?: 0, c)
            }
            return out
        }

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

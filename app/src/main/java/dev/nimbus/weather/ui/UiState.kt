/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/UiState.kt
 * What the screens show: places and their data, location, navigation, demo.
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

package dev.nimbus.weather.ui

import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.ModelSeries
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData

data class PlaceState(
    val data: WeatherData? = null,
    /** The forecast being fetched (the page's spinner); the extras after it show theirs by their cards' dots. */
    val loading: Boolean = false,
    val error: Boolean = false,
    val models: List<ModelSeries>? = null,
    val history: dev.nimbus.weather.data.remote.History? = null,
    val historyLoading: Boolean = false,
    val historyError: Boolean = false,
)

enum class LocationStatus { UNKNOWN, LOADING, AVAILABLE, DENIED, UNAVAILABLE }

sealed interface Screen {
    data object Main : Screen
    data object Places : Screen
    data object Settings : Screen
    data object Licenses : Screen
    /** [day]: start (local midnight) of a past day from the look-back, null for the live radar. */
    data class Radar(val placeId: String?, val day: Long? = null, val lightning: Boolean = false) : Screen
}

data class Demo(
    val condition: Condition?,
    val night: Boolean,
    val season: dev.nimbus.weather.ui.background.Season? = null,
    val wind: Float? = null,
    val pollen: Float? = null,
)

data class UiState(
    val initialized: Boolean = false,
    val currentPlace: Place? = null,
    val savedPlaces: List<Place> = emptyList(),
    val states: Map<String, PlaceState> = emptyMap(),
    val settings: Settings = Settings(),
    val selectedPlaceId: String? = null,
    val locationStatus: LocationStatus = LocationStatus.UNKNOWN,
    /** When the position of "my location" was taken (wall clock): older than [dev.nimbus.weather.data.repo.Freshness.LOCATION_MS], its page is not current. */
    val locationFixedAt: Long = 0L,
    /** When the last search found no new position – the next one waits a little. */
    val locationTriedAt: Long = 0L,
    /** Searches in a row without a new position: the pause before the next grows. */
    val locationMisses: Int = 0,
    /** The device's location is switched off: no position can come until it is on again. */
    val locationOff: Boolean = false,
    /**
     * The position of "my location" is asked for anew on request (reload, the pin): until it is
     * confirmed or the new place taken, its page counts as not current and loads nothing.
     */
    val locationForced: Boolean = false,
    val backStack: List<Screen> = listOf(Screen.Main),
    val demo: Demo? = null,
) {
    /** Pages shown in the pager: current location first, then saved places. */
    val pages: List<Place> get() = listOfNotNull(currentPlace) + savedPlaces.filter { it.id != currentPlace?.id }
    val screen: Screen get() = backStack.last()
}

/**
 * Whether a request for [place] waits for the position: "my location" while it is looked for –
 * the search loads its data afterwards, for the place confirmed or the new one.
 */
internal fun waitsForLocation(place: Place, st: UiState): Boolean =
    place.isCurrentLocation && st.locationStatus == LocationStatus.LOADING

/** Whether [a] and [b] are the same spot (not only the same id: "my location" keeps its id). */
internal fun sameSpot(a: Place?, b: Place): Boolean = a != null && a.latitude == b.latitude && a.longitude == b.longitude

/**
 * Whether "my location" is on screen – its page chosen, the list of places open, or the places
 * beside the weather (tablet, [sidebar]): only then is the position looked for. For another
 * place the GPS stays off, the page of "my location" shows on its return whether it is current.
 */
internal fun locationWanted(st: UiState, sidebar: Boolean): Boolean {
    val current = st.currentPlace ?: return false
    val selected = st.pages.firstOrNull { it.id == st.selectedPlaceId } ?: st.pages.firstOrNull()
    return selected?.id == current.id || st.screen == Screen.Places || sidebar
}

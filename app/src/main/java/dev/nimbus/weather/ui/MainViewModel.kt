/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/MainViewModel.kt
 * Screen state: places, loading, history, radar and settings navigation.
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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.ModelSeries
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.ui.radar.RadarPrefetcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class PlaceState(
    val data: WeatherData? = null,
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
    data class Radar(val placeId: String?) : Screen
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
    val backStack: List<Screen> = listOf(Screen.Main),
    val demo: Demo? = null,
) {
    /** Pages shown in the pager: current location first, then saved places. */
    val pages: List<Place> get() = listOfNotNull(currentPlace) + savedPlaces.filter { it.id != currentPlace?.id }
    val screen: Screen get() = backStack.last()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NimbusApp).container
    private val store = container.store
    private val repo = container.repository
    private val location = container.location

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val jobs = HashMap<String, Job>()
    private var lastSettings: Settings? = null

    init {
        viewModelScope.launch {
            // Restore last known current location from cache for an instant start.
            val cachedCurrent = store.cachedWeather(LocationProvider.CURRENT_LOCATION_ID)
            if (cachedCurrent != null && location.hasPermission()) {
                _state.update {
                    it.copy(
                        currentPlace = cachedCurrent.place,
                        states = it.states + (cachedCurrent.place.id to PlaceState(cachedCurrent)),
                    )
                }
            }
            combine(store.settings, store.places) { s, p -> s to p }.collect { (settings, places) ->
                val first = !_state.value.initialized
                val prev = lastSettings
                lastSettings = settings
                val cached = places.filter { _state.value.states[it.id]?.data == null }
                    .mapNotNull { p -> store.cachedWeather(p.id)?.let { p.id to PlaceState(it) } }
                _state.update { st ->
                    st.copy(
                        initialized = true,
                        settings = settings,
                        savedPlaces = places,
                        states = st.states + cached,
                        selectedPlaceId = st.selectedPlaceId ?: st.pages.firstOrNull()?.id ?: places.firstOrNull()?.id,
                    )
                }
                val needsReload = prev != null && (prev.model != settings.model ||
                    prev.useStationObservations != settings.useStationObservations)
                if (first) {
                    refreshLocation()
                    _state.value.pages.forEach { load(it, force = false) }
                } else if (needsReload) {
                    _state.value.pages.forEach { load(it, force = true) }
                } else {
                    places.forEach { if (_state.value.states[it.id]?.data == null) load(it, force = false) }
                }
            }
        }
    }

    private val german: Boolean
        get() = getApplication<Application>().resources.configuration.locales[0].language == Locale.GERMAN.language

    /** Refreshes the radar cache of the shown place while the app is open (Wi-Fi only). */
    private var radarTicker: kotlinx.coroutines.Job? = null

    fun onPause() {
        radarTicker?.cancel()
        radarTicker = null
    }

    fun onResume() {
        radarTicker?.cancel()
        radarTicker = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(RADAR_REFRESH_MS)
                val st = _state.value
                val place = st.pages.firstOrNull { it.id == st.selectedPlaceId } ?: st.pages.firstOrNull() ?: continue
                maybePrefetchRadar(place, st.settings)
            }
        }
        if (!_state.value.initialized) return
        viewModelScope.launch {
            // Take over what the hourly background refresh stored in the meantime.
            _state.value.pages.forEach { p ->
                val cached = store.cachedWeather(p.id) ?: return@forEach
                val current = _state.value.states[p.id]?.data
                if (current == null || cached.fetchedAt > current.fetchedAt) updatePlace(p.id) { it.copy(data = cached) }
            }
            refreshLocation()
            _state.value.pages.forEach { load(it, force = false) }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) refreshLocation(force = true)
        else _state.update { it.copy(locationStatus = LocationStatus.DENIED) }
    }

    fun refreshLocation(force: Boolean = false) {
        if (!location.hasPermission()) {
            _state.update { it.copy(locationStatus = if (it.locationStatus == LocationStatus.DENIED) LocationStatus.DENIED else LocationStatus.UNKNOWN, currentPlace = null) }
            return
        }
        if (_state.value.locationStatus == LocationStatus.LOADING) return
        viewModelScope.launch {
            _state.update { it.copy(locationStatus = LocationStatus.LOADING) }
            val loc = runCatching { location.currentLocation() }.getOrNull()
            if (loc == null) {
                _state.update { it.copy(locationStatus = if (it.currentPlace != null) LocationStatus.AVAILABLE else LocationStatus.UNAVAILABLE) }
                return@launch
            }
            val old = _state.value.currentPlace
            val moved = old == null || distanceKm(old.latitude, old.longitude, loc.latitude, loc.longitude) > 1.5
            val place = if (moved || old == null) {
                location.toPlace(loc, getApplication<Application>().getString(R.string.my_location))
            } else old
            _state.update { st ->
                st.copy(
                    currentPlace = place,
                    locationStatus = LocationStatus.AVAILABLE,
                    selectedPlaceId = if (st.selectedPlaceId == null || old == null && st.savedPlaces.isEmpty()) place.id else st.selectedPlaceId,
                )
            }
            load(place, force = moved || force)
        }
    }

    fun load(place: Place, force: Boolean) {
        val current = _state.value.states[place.id]
        val data = current?.data
        val fresh = data != null && System.currentTimeMillis() - data.fetchedAt < 10 * 60_000L &&
            data.place.latitude == place.latitude && data.place.longitude == place.longitude
        if (!force && fresh) return
        if (jobs[place.id]?.isActive == true) return
        jobs[place.id] = viewModelScope.launch {
            updatePlace(place.id) { it.copy(loading = true, error = false) }
            val settings = store.settings.first()
            val result = runCatching { repo.load(place, settings, german) }
            result.onSuccess { d ->
                updatePlace(place.id) { it.copy(data = d, loading = false, error = false, models = null) }
                runCatching { store.cacheWeather(d) }
                maybePrefetchRadar(place, settings)
            }.onFailure {
                updatePlace(place.id) { it.copy(loading = false, error = true) }
            }
        }
    }

    private fun maybePrefetchRadar(place: Place, settings: Settings) {
        val app = getApplication<Application>()
        val selected = _state.value.selectedPlaceId
        if (!settings.preloadRadar || (selected != null && selected != place.id) || !RadarPrefetcher.isUnmetered(app)) return
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_000)      // let the page settle first
            runCatching { RadarPrefetcher.prefetch(app, container.mapHttp, place) }
        }
    }

    fun refresh(placeId: String) {
        val place = _state.value.pages.firstOrNull { it.id == placeId } ?: return
        if (place.isCurrentLocation) refreshLocation(force = true) else load(place, force = true)
    }

    /** Past 48 h for the place, fetched on demand when the user swipes back; kept for 30 min. */
    fun loadHistory(placeId: String, force: Boolean = false) {
        val st = _state.value.states[placeId] ?: return
        val place = st.data?.place ?: _state.value.pages.firstOrNull { it.id == placeId } ?: return
        val h = st.history
        if (!force && (st.historyLoading || (h != null && System.currentTimeMillis() - h.fetchedAt < 30 * 60_000L))) return
        viewModelScope.launch {
            updatePlace(placeId) { it.copy(historyLoading = true, historyError = false) }
            val model = _state.value.settings.model.openMeteoId
            // Station data is always loaded for the look back, independent of the "use station
            // measurements" setting (that only decides what the current conditions show): the
            // comparison measurement vs. forecast is the point of the history.
            val inGermany = dev.nimbus.weather.data.repo.WeatherRepository.isInDwdArea(place.latitude, place.longitude)
            val result = runCatching { container.history.load(place.latitude, place.longitude, model, inGermany) }
            updatePlace(placeId) {
                it.copy(history = result.getOrNull() ?: it.history, historyLoading = false, historyError = result.isFailure)
            }
        }
    }

    fun loadModels(placeId: String) {
        val st = _state.value.states[placeId] ?: return
        if (st.models != null) return
        val place = st.data?.place ?: return
        viewModelScope.launch {
            val series = runCatching { repo.modelComparison(place) }.getOrDefault(emptyList())
            updatePlace(placeId) { it.copy(models = series) }
        }
    }

    private fun updatePlace(id: String, f: (PlaceState) -> PlaceState) {
        _state.update { st -> st.copy(states = st.states + (id to f(st.states[id] ?: PlaceState()))) }
    }

    // ---- places -------------------------------------------------------------

    suspend fun search(query: String): List<Place> =
        runCatching { repo.search(query, if (german) "de" else "en") }.getOrDefault(emptyList())

    fun addPlace(place: Place) {
        viewModelScope.launch {
            store.updatePlaces { list -> if (list.any { it.id == place.id }) list else list + place }
            _state.update { it.copy(selectedPlaceId = place.id) }
            load(place, force = true)
        }
    }

    fun removePlace(place: Place) {
        viewModelScope.launch {
            store.updatePlaces { list -> list.filterNot { it.id == place.id } }
            store.deleteCache(place.id)
            _state.update { st ->
                st.copy(
                    states = st.states - place.id,
                    selectedPlaceId = if (st.selectedPlaceId == place.id) null else st.selectedPlaceId,
                )
            }
        }
    }

    fun select(placeId: String) {
        _state.update { it.copy(selectedPlaceId = placeId) }
        val st = _state.value
        val place = st.pages.firstOrNull { it.id == placeId } ?: return
        if (st.states[placeId]?.data != null) maybePrefetchRadar(place, st.settings)
    }

    fun updateSettings(f: (Settings) -> Settings) {
        viewModelScope.launch { store.updateSettings(f) }
    }

    // ---- navigation ---------------------------------------------------------

    fun navigate(screen: Screen) = _state.update { it.copy(backStack = it.backStack + screen) }

    fun back(): Boolean {
        if (_state.value.backStack.size <= 1) return false
        _state.update { it.copy(backStack = it.backStack.dropLast(1)) }
        return true
    }

    fun setDemo(demo: Demo?, screen: String?) {
        _state.update {
            it.copy(
                demo = demo,
                backStack = when (screen) {
                    "radar" -> listOf(Screen.Main, Screen.Radar(null))
                    "places" -> listOf(Screen.Main, Screen.Places)
                    "settings" -> listOf(Screen.Main, Screen.Settings)
                    else -> it.backStack
                },
            )
        }
    }

    companion object {
        /** Matches the nowcast lifetime in the cache: the radar never shows frames older than this. */
        private const val RADAR_REFRESH_MS = 10 * 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
            return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
        }
    }
}

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
import dev.nimbus.weather.BuildConfig
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.modelFor
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
import kotlinx.coroutines.job
import kotlinx.coroutines.isActive
import java.util.Locale

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
    data class Radar(val placeId: String?, val day: Long? = null) : Screen
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
 * Whether "my location" is on screen – its page chosen, the list of places open, or the places
 * beside the weather (tablet, [sidebar]): only then is the position looked for. For another
 * place the GPS stays off, the page of "my location" shows on its return whether it is current.
 */
/**
 * Whether a request for [place] waits for the position: "my location" while it is looked for –
 * the search loads its data afterwards, for the place confirmed or the new one.
 */
internal fun waitsForLocation(place: Place, st: UiState): Boolean =
    place.isCurrentLocation && st.locationStatus == LocationStatus.LOADING

/** Whether [a] and [b] are the same spot (not only the same id: "my location" keeps its id). */
internal fun sameSpot(a: Place?, b: Place): Boolean = a != null && a.latitude == b.latitude && a.longitude == b.longitude

internal fun locationWanted(st: UiState, sidebar: Boolean): Boolean {
    val current = st.currentPlace ?: return false
    val selected = st.pages.firstOrNull { it.id == st.selectedPlaceId } ?: st.pages.firstOrNull()
    return selected?.id == current.id || st.screen == Screen.Places || sidebar
}

/** What the view model works with: the app's own ([of]) – or stand-ins in a test. */
class ViewModelDeps(
    val store: dev.nimbus.weather.data.repo.Store,
    val repository: dev.nimbus.weather.data.repo.WeatherRepository,
    val location: LocationProvider,
    val history: dev.nimbus.weather.data.remote.HistorySource,
    val http: okhttp3.OkHttpClient,
    val mapHttp: okhttp3.OkHttpClient,
) {
    companion object {
        fun of(c: dev.nimbus.weather.AppContainer) = ViewModelDeps(c.store, c.repository, c.location, c.history, c.http, c.mapHttp)
    }
}

/** A forced reload shows "all yellow" at least this long – a fast answer made it invisible. */
internal const val FORCED_YELLOW_MS = 500L

class MainViewModel(
    app: Application,
    private val container: ViewModelDeps = ViewModelDeps.of((app as NimbusApp).container),
) : AndroidViewModel(app) {
    private val store = container.store
    private val repo = container.repository
    private val location = container.location

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val jobs = HashMap<String, Job>()
    /** The place each of [jobs] loads – "my location" keeps its id when it moves. */
    private val jobSpots = HashMap<String, Place>()
    /** Look-backs asked for while the position of "my location" was looked for: loaded after. */
    private val historyWaiting = HashSet<String>()
    /** The places shown beside the weather (tablet sideways): "my location" among them. */
    private var sidebar = false
    /** The app in front: only then does an out-of-date record fetch anew (see [onDue]). */
    private var resumed = false
    /** Places whose records went out of date while a load ran for them: loaded again after it. */
    private val dueAfter = HashSet<String>()

    /**
     * The model of what is current: every record of data tells its card when it goes out of date
     * – and this controller, which fetches it anew ([onDue]).
     */
    val shelf = dev.nimbus.weather.data.repo.Shelf(viewModelScope, onDue = ::onDue)
    private var lastSettings: Settings? = null

    init {
        // DWD radar area for the RainViewer tiles (from disk after the first time)
        viewModelScope.launch { runCatching { dev.nimbus.weather.ui.radar.DwdCoverage.ensure(container.http) } }
        // Once the preview card has its size: prepare the radar previews of all places, so that
        // switching places shows a picture at once (base maps only on Wi-Fi, see RadarPreview).
        viewModelScope.launch {
            dev.nimbus.weather.ui.radar.RadarPreview.cardSizeFlow.first { it != null }
            kotlinx.coroutines.delay(5_000L)            // the visible place first
            val app = getApplication<Application>()
            _state.value.pages.forEach { p ->
                runCatching {
                    dev.nimbus.weather.ui.radar.RadarPreview.prefetch(
                        app, container.http, container.mapHttp, p.latitude, p.longitude,
                        app.resources.displayMetrics.density, app.resources.configuration.locales[0].language,
                    )
                }
            }
        }
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
                shelf.take(cachedCurrent)
            }
            combine(store.settings, store.places) { s, p -> s to p }.collect { (settings, places) ->
                dev.nimbus.weather.ui.radar.RadarPalette.scheme = settings.radarColors
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
                cached.forEach { (_, ps) -> ps.data?.let(shelf::take) }
                // anew: the places whose model changed (the settings' one – not for a place with its
                // own), all of them when the station measurements are switched
                val stations = prev != null && prev.useStationObservations != settings.useStationObservations
                val changed = if (prev == null) emptyList() else _state.value.pages.filter { stations || prev.modelFor(it) != settings.modelFor(it) }
                if (first) {
                    if (_state.value.currentPlace == null || locationWanted(_state.value, sidebar)) refreshLocation()
                    _state.value.pages.forEach { load(it, force = false) }
                } else if (changed.isNotEmpty()) {
                    changed.forEach { load(it, force = true) }
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

    /**
     * A record went out of date (its time was up, or a failed one is to be asked again): fetched
     * anew – while the app is in front; brought back, [onResume] fetches what went out of date.
     */
    private fun onDue(record: dev.nimbus.weather.data.repo.DataRecord) {
        if (!resumed || !_state.value.initialized) return
        when (val key = record.key) {
            dev.nimbus.weather.data.repo.RecordKey.Position -> locateIfDue()
            is dev.nimbus.weather.data.repo.RecordKey.Part ->
                _state.value.pages.firstOrNull { it.id == key.placeId }?.let { load(it, force = false) }
            is dev.nimbus.weather.data.repo.RecordKey.LookBack ->
                if (_state.value.states[key.placeId]?.history != null) loadHistory(key.placeId)
        }
    }

    /**
     * Brought back from the background: the records against the clock (no timer ran while the
     * device slept) – then what is out of date is fetched: the position, every place's weather,
     * the look-backs already loaded.
     */
    private fun refreshExpired() {
        shelf.checkAll()
        locateIfDue()
        val st = _state.value
        // "my location" while its position is looked for: loaded for the place found (or, without one, for the old place) afterwards
        st.pages.forEach { if (!(it.isCurrentLocation && st.locationStatus == LocationStatus.LOADING)) load(it, force = false) }
        st.states.forEach { (id, ps) -> if (ps.history != null) loadHistory(id) }
    }

    fun onPause() {
        resumed = false
        radarTicker?.cancel()
        radarTicker = null
    }

    fun onResume() {
        resumed = true
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
        // what went out of date while the app was away: at once, before anything is fetched
        shelf.checkAll()
        viewModelScope.launch {
            // Take over what the hourly background refresh stored in the meantime.
            _state.value.pages.forEach { p ->
                val cached = store.cachedWeather(p.id) ?: return@forEach
                val current = _state.value.states[p.id]?.data
                if (current == null || cached.fetchedAt > current.fetchedAt) {
                    updatePlace(p.id) { it.copy(data = cached) }
                    shelf.take(cached)
                }
            }
            // the position only while "my location" is shown (the first one always: it makes the place)
            if (_state.value.currentPlace == null || locationWanted(_state.value, sidebar)) refreshLocation()
            refreshExpired()
        }
    }

    /**
     * Looks for the position when its record is out of date – and "my location" is on screen;
     * after searches without result not before their pause.
     */
    private fun locateIfDue() {
        val st = _state.value
        if (locationWanted(st, sidebar) && shelf.position.state == dev.nimbus.weather.data.repo.RecordState.STALE &&
            dev.nimbus.weather.data.repo.Freshness.locationRetryDue(st.locationTriedAt, System.currentTimeMillis(), st.locationMisses)
        ) refreshLocation()
    }

    /** The places beside the weather appear or go (tablet sideways). */
    fun onSidebar(shown: Boolean) {
        sidebar = shown
        locateIfDue()
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
        if (force) {
            // Asked for anew: everything for "my location" waits for the answer – a load running
            // for the place shown gives way, its cards turn yellow at once
            _state.value.currentPlace?.let { jobs[it.id]?.cancel() }
            _state.update { it.copy(locationForced = true) }
            // the position out of date – and with it every record of "my location"
            shelf.position.stale()
        }
        if (_state.value.locationStatus == LocationStatus.LOADING) return
        _state.update { it.copy(locationStatus = LocationStatus.LOADING) }
        viewModelScope.launch {
            // asked for anew (reload, the pin): a new position – the system's last one, even
            // a minute old, made the check a matter of milliseconds and nothing was seen of it
            val asked = System.currentTimeMillis()
            val found = runCatching { location.currentLocation(fresh = _state.value.locationForced) }.getOrNull()
            val forced = _state.value.locationForced
            // asked for anew: "all yellow" stays at least this long, however fast the position is there
            if (forced) kotlinx.coroutines.delay((FORCED_YELLOW_MS - (System.currentTimeMillis() - asked)).coerceAtLeast(0L))
            val off = !location.enabled()
            val known = _state.value.locationFixedAt
            val now = System.currentTimeMillis()
            // No position newer than the one shown: the page stays with its place – and is loaded
            // all the same (pulled: anew; it reloaded nothing when the position was the same)
            if (found == null || found.at <= known && _state.value.currentPlace != null) {
                val missed = dev.nimbus.weather.data.repo.Freshness.locationMissed(found?.at, now)
                _state.update {
                    it.copy(
                        locationStatus = if (it.currentPlace != null) LocationStatus.AVAILABLE else LocationStatus.UNAVAILABLE,
                        // the same, still current position is no miss: no pause before the next search
                        locationTriedAt = if (missed) now else it.locationTriedAt,
                        locationMisses = if (missed) it.locationMisses + 1 else it.locationMisses,
                        locationOff = off,
                        locationForced = false,
                    )
                }
                // the position: confirmed (the same, still current) – or out of date, asked again after the pause
                if (missed) shelf.position.stale(retryMs = dev.nimbus.weather.data.repo.Freshness.locationPauseMs(_state.value.locationMisses))
                else found?.let { shelf.position.arrived(it.at) }
                // the place stays: its data now (asked for anew: anew), the look-back waiting too
                _state.value.currentPlace?.let { p ->
                    load(p, force = forced)
                    if (historyWaiting.remove(p.id)) loadHistory(p.id)
                }
                return@launch
            }
            val loc = found.value
            val old = _state.value.currentPlace
            val fallbackName = getApplication<Application>().getString(R.string.my_location)
            val moved = old == null || distanceKm(old.latitude, old.longitude, loc.latitude, loc.longitude) > 1.5
            // A place without a real name (geocoding failed last time) asks again.
            val unnamed = old != null && old.name == fallbackName
            val place = if (moved || unnamed || old == null) {
                val found = location.toPlace(loc, fallbackName)
                // Geocoding failed again, but we are still near the last named place: keep its name.
                if (found.name == fallbackName && old != null && old.name != fallbackName &&
                    distanceKm(old.latitude, old.longitude, loc.latitude, loc.longitude) < 5.0
                ) found.copy(name = old.name, region = old.region, country = old.country, countryCode = old.countryCode) else found
            } else old
            // an older position (the system's last one) is no success: the pause keeps growing
            val current = !dev.nimbus.weather.data.repo.Freshness.locationMissed(found.at, System.currentTimeMillis())
            _state.update { st ->
                st.copy(
                    currentPlace = place,
                    locationStatus = LocationStatus.AVAILABLE,
                    locationFixedAt = found.at,
                    locationTriedAt = if (current) 0L else System.currentTimeMillis(),
                    locationMisses = if (current) 0 else st.locationMisses + 1,
                    locationOff = off,
                    locationForced = false,
                    selectedPlaceId = if (st.selectedPlaceId == null || old == null && st.savedPlaces.isEmpty()) place.id else st.selectedPlaceId,
                )
            }
            // the position's record: current (its dot green) – an older one out of date, asked again after the pause
            if (current) shelf.position.arrived(found.at)
            else shelf.position.stale(retryMs = dev.nimbus.weather.data.repo.Freshness.locationPauseMs(_state.value.locationMisses))
            // Only the name changed (it was missing before): show it right away, no reload needed.
            if (!moved && old != null && place.name != old.name) {
                val renamed = _state.value.states[place.id]?.data?.copy(place = place)
                if (renamed != null) {
                    updatePlace(place.id) { it.copy(data = renamed) }
                    shelf.take(renamed)
                    runCatching { store.cacheWeather(renamed) }
                }
            }
            if (BuildConfig.DEBUG) android.util.Log.d("NimbusLocation", "place ${place.name} (was ${old?.name}, moved=$moved)")
            load(place, force = moved || forced)
            // the look-back: the one asked for meanwhile, or the place's anew where it moved
            val hadHistory = _state.value.states[place.id]?.history != null
            if (historyWaiting.remove(place.id) || moved && hadHistory) loadHistory(place.id, force = moved)
        }
    }

    /**
     * Loads [place]'s weather: the parts past their shelf life – or everything, fresh from the
     * sources ([force]: reload, a new spot). [showYellowMs]: the cards stay yellow at least this long
     * (a forced reload: "all yellow" is seen, however fast the answers are).
     */
    fun load(place: Place, force: Boolean, showYellowMs: Long = 0L) {
        // "my location" while its position is looked for: nothing yet, the search loads it after
        if (waitsForLocation(place, _state.value)) return
        val current = _state.value.states[place.id]
        val data = current?.data
        // What to load: everything (asked for anew, a new spot, nothing there yet) – else the parts
        // whose records are out of date; none: the data are current. The forecast comes along
        // whenever anything is loaded (its life is the shortest).
        val all = dev.nimbus.weather.data.model.DataPart.entries.toSet()
        val due = if (force || data == null || !sameSpot(data.place, place)) all else shelf.due(place.id)
        if (due.isEmpty()) return
        // a load already running for the same spot is enough (what went out of date meanwhile is
        // loaded after it); one for the spot left behind ("my location" moved meanwhile) gives way –
        // and so does any one to a reload asked for: everything anew, not the rest of the old one
        if (jobs[place.id]?.isActive == true) {
            if (!force && sameSpot(jobSpots[place.id], place)) { dueAfter += place.id; return }
            jobs[place.id]?.cancel()
        }
        jobSpots[place.id] = place
        jobs[place.id] = viewModelScope.launch {
            // what is shown now is the last data: the records being loaded out of date (their cards
            // yellow) until their part is new
            (due + dev.nimbus.weather.data.model.DataPart.FORECAST).forEach { shelf.part(place.id, it).stale() }
            updatePlace(place.id) { it.copy(loading = true, error = false) }
            val settings = store.settings.first()
            // The forecast shows as soon as it is there, each extra's card as soon as its source has
            // answered – but only while this load is still the one for the place: "my location" moved
            // meanwhile, a load for the spot left behind writes none of its steps over the new one's
            val me = coroutineContext.job
            if (showYellowMs > 0) kotlinx.coroutines.delay(showYellowMs)
            val result = runCatching {
                repo.load(place, settings, german, previous = data, refresh = due, fresh = force) { step ->
                    // each step to its records on the main thread: each card as its part arrives –
                    // the page's spinner ends with the first (the forecast); the extras still on
                    // the way show it by their cards' dots, however long their sources take
                    viewModelScope.launch {
                        if (me.isActive) {
                            updatePlace(place.id) { it.copy(data = step, loading = false) }
                            shelf.take(step)
                        }
                    }
                }
            }
            // given way to a load for another spot: neither its data nor an error over the new one's
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return@launch
            result.onSuccess { d ->
                updatePlace(place.id) { it.copy(data = d, loading = false, error = false, models = null) }
                shelf.take(d)
                // no new answer (failed, too late, a stored one standing in): asked again after a while
                d.stale.forEach { shelf.part(place.id, it).stale(retryMs = dev.nimbus.weather.data.repo.Freshness.STALE_RETRY_MS) }
                runCatching { store.cacheWeather(d) }
                maybePrefetchRadar(place, settings)
                // Radar preview of this place, so switching to it shows a picture at once
                // (own job: must not keep the load job of the place active)
                viewModelScope.launch {
                    val app = getApplication<Application>()
                    runCatching {
                        dev.nimbus.weather.ui.radar.RadarPreview.prefetch(
                            app, container.http, container.mapHttp, place.latitude, place.longitude,
                            app.resources.displayMetrics.density, app.resources.configuration.locales[0].language,
                        )
                    }
                }
            }.onFailure {
                updatePlace(place.id) { it.copy(loading = false, error = true) }
                // the forecast failed: everything stays out of date, asked again after a while
                shelf.part(place.id, dev.nimbus.weather.data.model.DataPart.FORECAST).stale(retryMs = dev.nimbus.weather.data.repo.Freshness.STALE_RETRY_MS)
            }
            // went out of date while this load ran: now
            if (dueAfter.remove(place.id)) load(place, force = false)
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
        // "my location": first the position (everything for it waits), then its data
        if (place.isCurrentLocation) refreshLocation(force = true) else load(place, force = true, showYellowMs = FORCED_YELLOW_MS)
    }

    /** Past 48 h for the place, fetched on demand when the user swipes back; kept for 30 min. */
    fun loadHistory(placeId: String, force: Boolean = false) {
        val st = _state.value.states[placeId] ?: return
        // the place as it is now ("my location" may have moved, its data are still the old spot's)
        val place = _state.value.pages.firstOrNull { it.id == placeId } ?: st.data?.place ?: return
        // "my location" while its position is looked for: after the answer
        if (waitsForLocation(place, _state.value)) { historyWaiting += placeId; return }
        val h = st.history
        val record = shelf.lookBack(placeId)
        if (!force && (st.historyLoading || (h != null && record.state == dev.nimbus.weather.data.repo.RecordState.CURRENT))) return
        viewModelScope.launch {
            record.stale()
            updatePlace(placeId) { it.copy(historyLoading = true, historyError = false) }
            val model = _state.value.settings.modelFor(place).openMeteoId
            // Station data is always loaded for the look back, independent of the "use station
            // measurements" setting (that only decides what the current conditions show): the
            // comparison measurement vs. forecast is the point of the history.
            val inGermany = dev.nimbus.weather.data.repo.WeatherRepository.isInDwdArea(place.latitude, place.longitude)
            val result = runCatching { container.history.load(place.latitude, place.longitude, model, inGermany) }
            updatePlace(placeId) {
                it.copy(history = result.getOrNull() ?: it.history, historyLoading = false, historyError = result.isFailure)
            }
            // the look-back's record: current – or, failed, out of date and asked again after a while
            result.getOrNull()?.let { record.arrived(it.fetchedAt) } ?: record.stale(retryMs = dev.nimbus.weather.data.repo.Freshness.STALE_RETRY_MS)
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

    /** New order of the saved places (ids in the wanted order; places not listed keep their place at the end). */
    fun reorderPlaces(ids: List<String>) {
        viewModelScope.launch {
            store.updatePlaces { list -> ids.mapNotNull { id -> list.firstOrNull { it.id == id } } + list.filter { it.id !in ids } }
        }
    }

    /**
     * The forecast model of the saved place [placeId]: [model], or null for the one of the
     * settings. Its weather and its look-back are loaded anew with it.
     */
    fun setPlaceModel(placeId: String, model: ForecastModel?) {
        viewModelScope.launch {
            store.updatePlaces { list -> list.map { if (it.id == placeId) it.copy(model = model) else it } }
            _state.update { st -> st.copy(savedPlaces = st.savedPlaces.map { if (it.id == placeId) it.copy(model = model) else it }) }
            val place = _state.value.pages.firstOrNull { it.id == placeId } ?: return@launch
            load(place, force = true)
            if (_state.value.states[placeId]?.history != null) loadHistory(placeId, force = true)
        }
    }

    fun removePlace(place: Place) {
        viewModelScope.launch {
            store.updatePlaces { list -> list.filterNot { it.id == place.id } }
            store.deleteCache(place.id)
            shelf.forget(place.id)
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
        locateIfDue()
        val st = _state.value
        val place = st.pages.firstOrNull { it.id == placeId } ?: return
        if (st.states[placeId]?.data != null) maybePrefetchRadar(place, st.settings)
    }

    fun updateSettings(f: (Settings) -> Settings) {
        viewModelScope.launch { store.updateSettings(f) }
    }

    // ---- navigation ---------------------------------------------------------

    fun navigate(screen: Screen) {
        _state.update { it.copy(backStack = it.backStack + screen) }
        locateIfDue()
    }

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

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/WeatherGrid.kt
 * Grid of hourly temperature and wind around a place for the radar overlays.
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

package dev.nimbus.weather.ui.radar

import dev.nimbus.weather.data.remote.OpenMeteoSource
import dev.nimbus.weather.data.remote.arr
import dev.nimbus.weather.data.remote.dbl
import dev.nimbus.weather.data.remote.getJson
import dev.nimbus.weather.data.remote.lng
import dev.nimbus.weather.data.remote.o
import dev.nimbus.weather.data.remote.obj
import dev.nimbus.weather.data.remote.JsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Regular lat/lon grid of hourly 2 m temperature and 10 m wind around a place (Open-Meteo,
 * multi-location request). Used for the temperature and wind overlays and to decide per radar
 * pixel whether precipitation falls as rain, sleet or snow.
 */
class WeatherGrid(
    val lat0: Double,          // southern row
    val lon0: Double,          // western column
    val step: Double,
    val rows: Int,
    val cols: Int,
    val times: LongArray,      // epoch ms, hourly
    /** [timeIndex][row * cols + col] */
    val temp: Array<FloatArray>,
    val windSpeed: Array<FloatArray>,   // km/h
    val windDir: Array<FloatArray>,     // degrees, direction wind blows from
) {
    val lat1: Double get() = lat0 + step * (rows - 1)
    val lon1: Double get() = lon0 + step * (cols - 1)

    fun contains(lat: Double, lon: Double, margin: Double = 0.0) =
        lat in (lat0 + margin)..(lat1 - margin) && lon in (lon0 + margin)..(lon1 - margin)

    fun hourIndex(timeMs: Long): Int {
        var best = 0
        var bestD = Long.MAX_VALUE
        for (i in times.indices) {
            val d = abs(times[i] - timeMs)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** Bilinear interpolation of [field] at the given position, null outside the grid. */
    fun sample(field: FloatArray, lat: Double, lon: Double): Float? {
        val fy = (lat - lat0) / step
        val fx = (lon - lon0) / step
        if (fy < 0 || fx < 0 || fy > rows - 1 || fx > cols - 1) return null
        val r = floor(fy).toInt().coerceAtMost(rows - 2)
        val c = floor(fx).toInt().coerceAtMost(cols - 2)
        val ty = (fy - r).toFloat()
        val tx = (fx - c).toFloat()
        val a = field[r * cols + c]
        val b = field[r * cols + c + 1]
        val d = field[(r + 1) * cols + c]
        val e = field[(r + 1) * cols + c + 1]
        if (a.isNaN() || b.isNaN() || d.isNaN() || e.isNaN()) return null
        return (a * (1 - tx) + b * tx) * (1 - ty) + (d * (1 - tx) + e * tx) * ty
    }

    fun temperatureAt(lat: Double, lon: Double, timeMs: Long): Float? = sample(temp[hourIndex(timeMs)], lat, lon)

    /** Like [sample], but continues the edge values up to [margin] degrees outside the grid. */
    fun sampleNear(field: FloatArray, lat: Double, lon: Double, margin: Double = step): Float? {
        if (lat < lat0 - margin || lat > lat1 + margin || lon < lon0 - margin || lon > lon1 + margin) return null
        return sample(field, lat.coerceIn(lat0, lat1), lon.coerceIn(lon0, lon1))
    }

    fun overlaps(south: Double, north: Double, west: Double, east: Double) =
        north >= lat0 && south <= lat1 && east >= lon0 && west <= lon1

    companion object {
        // Open-Meteo counts every location as one API call (free tier: 5,000/hour, 10,000/day),
        // so every grid has 11 x 9 = 99 points (portrait, like the screen). The spacing follows
        // the map zoom: 1° covers ~10° x 8° (whole Germany), 0.125° covers ~1.3° x 1° (a region).
        const val STEP = 1.0
        val STEPS = listOf(1.0, 0.5, 0.25, 0.125)
        const val HALF_ROWS = 5     // 11 rows
        const val HALF_COLS = 4     // 9 columns

        /** Spacing that puts grid points roughly 100–200 dp apart at the given MapLibre zoom. */
        fun stepForZoom(zoom: Double): Double = when {
            zoom < 7.1 -> 1.0
            zoom < 8.1 -> 0.5
            zoom < 9.1 -> 0.25
            else -> 0.125
        }

        /** Grid centre snapped to the grid spacing so nearby places share one grid. */
        fun origin(lat: Double, lon: Double, step: Double = STEP): Pair<Double, Double> {
            val cLat = (lat / step).roundToInt() * step
            val cLon = (lon / step).roundToInt() * step
            return (cLat - HALF_ROWS * step) to (cLon - HALF_COLS * step)
        }

        fun parse(root: JsonElement, lat0: Double, lon0: Double, rows: Int, cols: Int, step: Double): WeatherGrid {
            val list = root.arr()?.mapNotNull { it as? JsonObject }
                ?: listOfNotNull(root.obj())    // a single location returns an object
            require(list.size == rows * cols) { "expected ${rows * cols} locations, got ${list.size}" }
            val times = list.first().o("hourly")?.arr("time")?.mapNotNull { it.lng()?.times(1000) }?.toLongArray() ?: LongArray(0)
            fun field(name: String) = Array(times.size) { t ->
                FloatArray(rows * cols) { i -> list[i].o("hourly")?.arr(name)?.getOrNull(t).dbl()?.toFloat() ?: Float.NaN }
            }
            return WeatherGrid(lat0, lon0, step, rows, cols, times, field("temperature_2m"), field("wind_speed_10m"), field("wind_direction_10m"))
        }

        private fun JsonObject.arr(key: String) = this[key].arr()
    }
}

/** Keeps the last few grids in memory; the tile recolouring reads from here synchronously. */
object WeatherGridStore {
    private val mutex = Mutex()
    @Volatile private var grids: List<Pair<Long, WeatherGrid>> = emptyList()
    private const val MAX_AGE_MS = 60 * 60_000L
    /** Grids are also kept on disk for an hour, so app restarts cost no API calls. */
    @Volatile var cacheDir: java.io.File? = null

    /** Finest grid that contains the position. */
    fun gridFor(lat: Double, lon: Double): WeatherGrid? =
        grids.map { it.second }.filter { it.contains(lat, lon) }.minByOrNull { it.step }

    fun temperatureAt(lat: Double, lon: Double, timeMs: Long): Float? =
        gridFor(lat, lon)?.temperatureAt(lat, lon, timeMs)

    /**
     * Grid for colouring a radar tile: the finest one that covers the whole tile, else the
     * coarsest overlapping one (largest area; edges are continued by [WeatherGrid.sampleNear]).
     */
    fun gridOverlapping(tile: TileGeo): WeatherGrid? {
        val all = grids.map { it.second }
        return all.filter { it.contains(tile.south, tile.west) && it.contains(tile.north, tile.east) }.minByOrNull { it.step }
            ?: all.filter { it.overlaps(tile.south, tile.north, tile.west, tile.east) }.maxByOrNull { it.step }
    }

    /** Makes sure a fresh grid covers [lat]/[lon] with some margin; returns it (or null on error). */
    suspend fun ensure(http: OkHttpClient, lat: Double, lon: Double, step: Double = WeatherGrid.STEP): WeatherGrid? = mutex.withLock {
        val now = System.currentTimeMillis()
        grids.firstOrNull { now - it.first < MAX_AGE_MS && it.second.step == step && it.second.contains(lat, lon, margin = 2 * step) }
            ?.let { return it.second }
        val (lat0, lon0) = WeatherGrid.origin(lat, lon, step)
        val rows = 2 * WeatherGrid.HALF_ROWS + 1
        val cols = 2 * WeatherGrid.HALF_COLS + 1
        val lats = StringBuilder()
        val lons = StringBuilder()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (lats.isNotEmpty()) { lats.append(','); lons.append(',') }
            lats.append(OpenMeteoSource.fmt(lat0 + r * step))
            lons.append(OpenMeteoSource.fmt(lon0 + c * step))
        }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
            "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m&past_hours=25&forecast_hours=4" +
            "&timeformat=unixtime&wind_speed_unit=kmh&cell_selection=nearest"
        val file = cacheDir?.let { java.io.File(it, "grid_${step}_${OpenMeteoSource.fmt(lat0)}_${OpenMeteoSource.fmt(lon0)}.json") }
        val grid = runCatching {
            val cached = file?.takeIf { it.exists() && now - it.lastModified() < MAX_AGE_MS }?.let {
                withContext(Dispatchers.IO) { JsonCodec.parseToJsonElement(it.readText()) }
            }
            val json = cached ?: http.getJson(url).also { j ->
                file?.let { f -> withContext(Dispatchers.IO) { f.parentFile?.mkdirs(); f.writeText(j.toString()) } }
            }
            WeatherGrid.parse(json, lat0, lon0, rows, cols, step)
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it   // not an error: caller left
            android.util.Log.w("Nimbus", "weather grid unavailable: ${it.message}")
        }.getOrNull() ?: return null
        // The coarse grid (radar colouring of the whole map) is always kept.
        val fresh = grids.filter { now - it.first < MAX_AGE_MS }
        val coarse = fresh.filter { it.second.step == WeatherGrid.STEP }.take(2)
        grids = (listOf(now to grid) + coarse + fresh.filter { it.second.step != WeatherGrid.STEP }).distinctBy { it.second }.take(6)
        grid
    }
}

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarData.kt
 * Radar frames, time line and tile sources (DWD, RainViewer).
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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dev.nimbus.weather.data.remote.USER_AGENT
import dev.nimbus.weather.data.remote.await
import dev.nimbus.weather.data.remote.getJson
import dev.nimbus.weather.data.remote.getText
import dev.nimbus.weather.data.remote.l
import dev.nimbus.weather.data.remote.o
import dev.nimbus.weather.data.remote.obj
import dev.nimbus.weather.data.remote.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/** One animation frame of the radar loop. */
data class RadarFrame(
    val time: Long,
    val isForecast: Boolean,
    /** DWD WMS time parameter (Germany, incl. 2 h nowcast). */
    val dwdTime: String?,
    /** RainViewer tile path (Europe, past only), null if no matching frame. */
    val rainViewerPath: String?,
)

data class RadarTimeline(val frames: List<RadarFrame>, val nowIndex: Int, val rainViewerHost: String, val range: HistoryRange = HistoryRange.H2)

/**
 * How far the radar loop looks back. DWD keeps three days of radar, RainViewer (Europe) only two
 * hours, so the longer ranges show Germany only. Every range has ~25 frames incl. the 2 h nowcast.
 */
enum class HistoryRange(val hours: Int, val stepMinutes: Int) {
    H2(2, 10), H6(6, 20), H24(24, 60);

    val stepMs: Long get() = stepMinutes * 60_000L
}

/** Tile URL templates and frame discovery for DWD and RainViewer radar. */
object RadarSources {
    const val DWD_WMS = "https://maps.dwd.de/geoserver/dwd/wms"
    const val DWD_LAYER = "dwd:Radar_wn-product_1x1km_ger"
    const val DWD_ANALYSIS_LAYER = "Radar_wn-analysis_1x1km_ger"
    const val SAT_LAYER = "dwd:Satellite_meteosat_1km_euat_rgb_day_hrv_and_night_ir108_3h"
    const val WARN_LAYER = "dwd:Warnungen_Gemeinden_vereinigt"

    /**
     * Bumped whenever the tile colouring changes: radar tiles were once cached already recoloured,
     * a new URL makes sure old colours never come back from MapLibre's or OkHttp's cache.
     */
    const val TILE_VERSION = 4

    fun dwdTileUrl(layer: String, time: String?): String =
        "$DWD_WMS?service=WMS&version=1.1.1&request=GetMap&layers=$layer&styles=&format=image/png&transparent=true" +
            "&srs=EPSG:3857&bbox={bbox-epsg-3857}&width=512&height=512" + (time?.let { "&time=$it" } ?: "") +
            (if (layer.contains("Radar")) "&v=$TILE_VERSION" else "")

    fun rainViewerTileUrl(host: String, path: String): String = "$host$path/512/{z}/{x}/{y}/2/1_1.png?v=$TILE_VERSION"

    fun isoTime(ms: Long): String = Instant.ofEpochMilli(ms).toString().replace("Z", "").let {
        // 2026-09-28T20:40:00 -> 2026-09-28T20:40:00.000Z
        (if (it.length == 16) "$it:00" else it.take(19)) + ".000Z"
    }

    private val mutex = Mutex()
    private val cached = HashMap<HistoryRange, Pair<Long, RadarTimeline>>()
    /** Last RainViewer frame list, used when the API does not answer (its paths stay valid ~2 h). */
    @Volatile private var lastRainViewer: Pair<Long, Pair<String, List<Pair<Long, String>>>>? = null
    /** Time of the newest DWD radar analysis; frames up to here are final and cached long. */
    @Volatile var latestAnalysis: Long? = null
        private set
    /**
     * The last analysis time is also kept on disk: without network the loop is then built from
     * exactly the frames that are in the cache (an estimated time would miss them).
     */
    @Volatile var stateDir: java.io.File? = null
    private fun storedAnalysis(): Long? = runCatching { java.io.File(stateDir, "radar_latest").readText().trim().toLong() }.getOrNull()
    private fun storeAnalysis(t: Long) { runCatching { stateDir?.let { it.mkdirs(); java.io.File(it, "radar_latest").writeText(t.toString()) } } }
    /** Each discovery request may take this long; after that the fallback applies. */
    private const val DISCOVERY_TIMEOUT_MS = 6_000L
    private val rainViewerTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Time of a RainViewer frame by its path (e.g. "/v2/radar/abc123"), for the tile recolouring. */
    fun rainViewerTime(path: String): Long? = rainViewerTimes[path]

    /**
     * Frames from -[HistoryRange.hours] to +2 h around the latest DWD analysis.
     * RainViewer frames (past only, last 2 h) are matched by time.
     */
    suspend fun timeline(
        http: OkHttpClient, range: HistoryRange = HistoryRange.H2, now: Long = System.currentTimeMillis(), force: Boolean = false,
    ): RadarTimeline = mutex.withLock {
        if (!force) cached[range]?.let { (at, tl) -> if (now - at < 2 * 60_000L) return tl }
        coroutineScope {
            // Neither discovery request may hold up the radar: after a few seconds the DWD time is
            // estimated (analyses appear every 5 minutes, ~10 minutes late) and RainViewer's last
            // known frame list is used.
            val dwdJob = async { withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { runCatching { latestDwdAnalysis(http) }.getOrNull() } }
            val rvJob = async { withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { runCatching { rainViewerFrames(http) }.getOrNull() } }
            val dwd = dwdJob.await()
            if (dwd != null) { latestAnalysis = dwd; withContext(Dispatchers.IO) { storeAnalysis(dwd) } }
            val known = dwd ?: latestAnalysis ?: withContext(Dispatchers.IO) { storedAnalysis() }?.also { latestAnalysis = it }
            val latest = known?.takeIf { now - it < 3 * 3600_000L } ?: ((now - 10 * 60_000L) / (5 * 60_000L) * (5 * 60_000L))
            val rv = rvJob.await()?.also { lastRainViewer = now to it }
                ?: lastRainViewer?.takeIf { now - it.first < 2 * 3600_000L }?.second
            val host = rv?.first ?: "https://tilecache.rainviewer.com"
            val rvFrames = rv?.second.orEmpty()
            rvFrames.forEach { (t, p) -> rainViewerTimes[p] = t }
            val step = range.stepMs
            val anchor = latest / step * step
            val past = range.hours * 60 / range.stepMinutes
            val future = 120 / range.stepMinutes
            val frames = (-past..future).map { k ->
                val t = anchor + k * step
                val match = rvFrames.minByOrNull { kotlin.math.abs(it.first - t) }?.takeIf { kotlin.math.abs(it.first - t) <= 5 * 60_000L }
                RadarFrame(t, t > latest, isoTime(t), match?.second)
            }
            val tl = RadarTimeline(frames, frames.indexOfLast { !it.isForecast }.coerceAtLeast(0), host, range)
            cached[range] = System.currentTimeMillis() to tl
            tl
        }
    }

    private suspend fun latestDwdAnalysis(http: OkHttpClient): Long? {
        val url = "https://maps.dwd.de/geoserver/dwd/$DWD_ANALYSIS_LAYER/ows?service=WMS&version=1.3.0&request=GetCapabilities"
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val body = http.getText(req)
        val m = Regex("""<Dimension name="time"[^>]*default="([^"]+)"""").find(body) ?: return null
        return runCatching { Instant.parse(m.groupValues[1]).toEpochMilli() }.getOrNull()
    }

    /** Returns host and (time, path) of the past RainViewer frames. */
    private suspend fun rainViewerFrames(http: OkHttpClient): Pair<String, List<Pair<Long, String>>> {
        val root = http.getJson("https://api.rainviewer.com/public/weather-maps.json").obj() ?: error("rainviewer")
        val host = root.s("host") ?: "https://tilecache.rainviewer.com"
        val past = root.o("radar")?.get("past") as? kotlinx.serialization.json.JsonArray ?: return host to emptyList()
        return host to past.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = o.l("time") ?: return@mapNotNull null
            val p = o.s("path") ?: return@mapNotNull null
            t * 1000 to p
        }
    }
}

/** Web-Mercator helpers for the static preview map. */
object Mercator {
    const val TILE = 256.0
    fun worldX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * TILE * (1 shl z)
    fun worldY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * TILE * (1 shl z)
    }
    fun lon(worldX: Double, z: Int) = worldX / (TILE * (1 shl z)) * 360.0 - 180.0
    fun lat(worldY: Double, z: Int): Double {
        val n = PI - 2 * PI * worldY / (TILE * (1 shl z))
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }

    /** EPSG:3857 bounding box string of tile x/y/z. */
    fun bbox3857(x: Int, y: Int, z: Int): String {
        val o = 20037508.342789244
        val size = 2 * o / (1 shl z)
        val minX = -o + x * size
        val maxY = o - y * size
        return String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f", minX, maxY - size, minX + size, maxY)
    }
}

/** Small in-memory bitmap cache for the preview tiles. */
object TileBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(http: OkHttpClient, url: String): Bitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                http.newCall(req).await().use { r ->
                    if (!r.isSuccessful || r.code == 204) return@use null
                    val bytes = r.body.bytes()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()?.also { cache.put(url, it) }
        }
    }
}

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarSources.kt
 * The radar loop's time line: anchored on the newest step of the composite a place lies in,
 * RainViewer's frames matched to it; the URL templates of the map layers.
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

import dev.nimbus.weather.data.remote.getJson
import dev.nimbus.weather.data.remote.l
import dev.nimbus.weather.data.remote.o
import dev.nimbus.weather.data.remote.obj
import dev.nimbus.weather.data.remote.s
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.time.Instant

object RadarSources {
    const val WARN_LAYER = "dwd:Warnungen_Gemeinden_vereinigt"

    /**
     * Bumped whenever the tile colouring changes: radar tiles were once cached already recoloured,
     * a new URL makes sure old colours never come back from MapLibre's or OkHttp's cache.
     */
    const val TILE_VERSION = 8

    /** Resolution of the day archive: every step (5 minutes). */
    const val ARCHIVE_STEP_MS = HistoryRange.STEP_MS
    /** RainViewer's frame list may take this long; after that its last known one applies. */
    private const val DISCOVERY_TIMEOUT_MS = 6_000L

    fun dwdTileUrl(layer: String, time: String?): String =
        "${DwdRadar.WMS}?service=WMS&version=1.1.1&request=GetMap&layers=$layer&styles=&format=image/png&transparent=true" +
            "&srs=EPSG:3857&bbox={bbox-epsg-3857}&width=512&height=512" + (time?.let { "&time=$it" } ?: "") +
            (if (layer.contains("Radar")) "&v=$TILE_VERSION&c=${RadarPalette.scheme.ordinal}" else "")

    /** 2026-09-28T20:40:00.000Z (the WMS time parameter). */
    fun isoTime(ms: Long): String = Instant.ofEpochMilli(ms).toString().replace("Z", "").let {
        (if (it.length == 16) "$it:00" else it.take(19)) + ".000Z"
    }

    private val mutex = Mutex()
    private val cached = HashMap<Pair<HistoryRange, String>, Pair<Long, RadarTimeline>>()
    /** Last RainViewer frame list, used when the API does not answer (its paths stay valid ~2 h). */
    @Volatile private var lastRainViewer: Pair<Long, Pair<String, List<Pair<Long, String>>>>? = null
    private val rainViewerTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Time of a RainViewer frame by its path (e.g. "/v2/radar/abc123"), for the tile recolouring. */
    fun rainViewerTime(path: String): Long? = rainViewerTimes[path]

    /** The newest step of [anchor] now – the known one when it does not answer, else an estimate. */
    private suspend fun latest(http: OkHttpClient, anchor: RadarComposite, now: Long): Long {
        val known = RadarLatest.check(http, anchor) ?: RadarLatest.known(anchor)
        // steps appear every 5 minutes, about 10 minutes late
        return known?.takeIf { now - it < 3 * 3600_000L } ?: ((now - 10 * 60_000L) / HistoryRange.STEP_MS * HistoryRange.STEP_MS)
    }

    /**
     * Frames from -[HistoryRange.hours] up to the newest step of [anchor] (the composite the
     * place lies in, [RadarComposites.anchorFor]) – and 2 h on where it has a nowcast. RainViewer
     * frames (past only, last 2 h) are matched by time.
     */
    suspend fun timeline(
        http: OkHttpClient, range: HistoryRange = HistoryRange.H2, now: Long = System.currentTimeMillis(), force: Boolean = false,
        anchor: RadarComposite = RadarComposites.all.first(),
    ): RadarTimeline = mutex.withLock {
        val key = range to anchor.id
        if (!force) cached[key]?.let { (at, tl) -> if (now - at < 2 * 60_000L) return tl }
        coroutineScope {
            // Neither request may hold up the radar (see RadarLatest and DISCOVERY_TIMEOUT_MS)
            val latestJob = async { latest(http, anchor, now) }
            val rvJob = async { withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { runCatching { rainViewerFrames(http) }.getOrNull() } }
            val latest = latestJob.await()
            val rv = rvJob.await()?.also { lastRainViewer = now to it }
                ?: lastRainViewer?.takeIf { now - it.first < 2 * 3600_000L }?.second
            val host = rv?.first ?: "https://tilecache.rainviewer.com"
            val rvFrames = rv?.second.orEmpty()
            rvFrames.forEach { (t, p) -> rainViewerTimes[p] = t }
            val step = HistoryRange.STEP_MS
            val last = latest / step * step
            val past = range.hours * 60 / HistoryRange.STEP_MINUTES
            val future = if (anchor.hasNowcast) 120 / HistoryRange.STEP_MINUTES else 0
            val frames = (-past..future).map { k ->
                val t = last + k * step
                val match = rvFrames.minByOrNull { kotlin.math.abs(it.first - t) }?.takeIf { kotlin.math.abs(it.first - t) <= 5 * 60_000L }
                RadarFrame(t, t > latest, if (t > latest) latest else null, match?.second, match?.first)
            }
            val tl = RadarTimeline(frames, frames.indexOfLast { !it.isForecast }.coerceAtLeast(0), host, range)
            cached[key] = System.currentTimeMillis() to tl
            tl
        }
    }

    /**
     * A whole day of radar steps for the look-back (the DWD keeps about 3½ days, KNMI longer):
     * from [dayStart] to the end of the day, today up to the newest step of [anchor].
     */
    suspend fun dayTimeline(
        http: OkHttpClient, dayStart: Long, now: Long = System.currentTimeMillis(), anchor: RadarComposite = RadarComposites.all.first(),
    ): RadarTimeline {
        val latest = latest(http, anchor, now)
        val end = minOf(dayStart + 24 * 3_600_000L - ARCHIVE_STEP_MS, latest)      // 00:00 … 23:55
        val frames = (0..((end - dayStart) / ARCHIVE_STEP_MS).toInt()).map { k -> RadarFrame(dayStart + k * ARCHIVE_STEP_MS, false, null, null) }
        return RadarTimeline(frames, 0, "", day = dayStart)
    }

    /** Returns host and (time, path) of the past RainViewer frames. */
    private suspend fun rainViewerFrames(http: OkHttpClient): Pair<String, List<Pair<Long, String>>> {
        val root = http.getJson("https://api.rainviewer.com/public/weather-maps.json").obj() ?: error("rainviewer")
        val host = root.s("host") ?: "https://tilecache.rainviewer.com"
        val past = root.o("radar")?.get("past") as? JsonArray ?: return host to emptyList()
        return host to past.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = o.l("time") ?: return@mapNotNull null
            val p = o.s("path") ?: return@mapNotNull null
            t * 1000 to p
        }
    }
}

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/SatelliteLayer.kt
 * The satellite picture under the radar: Meteosat (EUMETSAT), at the time the radar shows – live
 * and on a day of the look-back.
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.ImageSource
import java.util.Locale
import kotlin.math.max

/**
 * One picture of the view from EUMETSAT's archive of Meteosat imagery (Meteosat Third Generation,
 * "GeoColour": true colour by day, clouds over the city lights at night; every 10 minutes since
 * September 2024), like the radar a single image the app loads itself – the next one replaces the
 * shown one only when it is complete: no blank tiles while the time moves on. The DWD's satellite
 * layer it replaces had a picture every 3 hours and about a day and a half back – nothing for the
 * look-back's earlier days.
 *
 * Licence: CC BY 4.0, "Contains modified EUMETSAT Meteosat data <year>" ([ATTRIBUTION]).
 */
class SatelliteLayer(private val scope: CoroutineScope, private val http: OkHttpClient) {
    private var style: Style? = null
    @Volatile private var geo: FieldGeo? = null
    private var shownGeo: FieldGeo? = null
    private var shownKey: String? = null
    private var wanted: String? = null
    private var job: Job? = null
    /** Pictures by view and time: a loop played again shows them at once. */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The satellite layer's source: an image of the view (an empty one until the first arrives). */
    fun source(): ImageSource = ImageSource(
        SOURCE, LatLngQuad(LatLng(1.0, 0.0), LatLng(1.0, 1.0), LatLng(0.0, 1.0), LatLng(0.0, 0.0)),
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    )

    fun install(style: Style) { this.style = style }

    /** The visible area changed: the picture covers it with a margin (like the radar picture). */
    fun setView(south: Double, north: Double, west: Double, east: Double) {
        val g = geo
        val inside = g != null && FieldGeo.R * Math.toRadians(west) >= g.minX && FieldGeo.R * Math.toRadians(east) <= g.maxX &&
            FieldGeo.mercY(south) >= g.minY && FieldGeo.mercY(north) <= g.maxY
        if (inside) return
        geo = FieldGeo.forView(south, north, west, east, maxSide = MAX_SIDE, minPxKm = 0.0)
    }

    /** Shows the picture of [time] (already one of [timeFor]'s steps); [next]: loaded ahead, for playback. */
    fun show(time: Long, next: Long? = null) {
        val g = geo ?: return
        val key = key(g, time)
        wanted = key
        cache.get(key)?.let { setImage(g, key, it); prefetch(next); return }
        job?.cancel()
        job = scope.launch {
            val bmp = load(g, time) ?: return@launch
            cache.put(key, bmp)
            if (wanted == key) withContext(Dispatchers.Main) { setImage(g, key, bmp) }
            prefetch(next)
        }
    }

    private fun prefetch(next: Long?) {
        val g = geo ?: return
        if (next == null) return
        val key = key(g, next)
        if (cache.get(key) != null) return
        scope.launch { load(g, next)?.let { cache.put(key, it) } }
    }

    private fun setImage(g: FieldGeo, key: String, bmp: Bitmap) {
        if (shownKey == key) return
        val src = style?.getSource(SOURCE) as? ImageSource ?: return
        if (shownGeo !== g) {
            src.setCoordinates(LatLngQuad(LatLng(g.north, g.west), LatLng(g.north, g.east), LatLng(g.south, g.east), LatLng(g.south, g.west)))
            shownGeo = g
        }
        src.setImage(bmp)
        shownKey = key
    }

    private suspend fun load(g: FieldGeo, time: Long): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url(g, time)).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val bytes = r.body.bytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun key(g: FieldGeo, time: Long) = "${g.minX.toLong()},${g.minY.toLong()},${g.maxX.toLong()},${g.maxY.toLong()}@$time"

    companion object {
        const val SOURCE = "sat"
        const val WMS = "https://view.eumetsat.int/geoserver/wms"
        const val LAYER = "mtg_fd:rgb_geocolour"
        /** The licence's attribution, with the year of the picture (CC BY 4.0). */
        const val ATTRIBUTION = "Contains modified EUMETSAT Meteosat data %d"
        /** A picture every 10 minutes … */
        const val STEP_MS = 10 * 60_000L
        /** … while playing one per hour (a day in a minute would load 144 pictures). */
        const val PLAY_STEP_MS = 60 * 60_000L
        /** The newest picture is about this old. */
        const val LATENCY_MS = 20 * 60_000L
        /** Longest side of the picture, in pixels (the map scales it). */
        private const val MAX_SIDE = 1024

        /**
         * The picture for the radar's time [frame] at [now]: one of the 10-minute steps (while
         * [playing], one of the hours), never newer than the newest there is (the radar's
         * forecast steps lie ahead of the satellite).
         */
        fun timeFor(frame: Long, now: Long, playing: Boolean): Long {
            val t = minOf(frame, now - LATENCY_MS)
            val step = if (playing) PLAY_STEP_MS else STEP_MS
            return Math.floorDiv(t, step) * step
        }

        /** The WMS request for the picture of [g] at [time] (in the map's projection). */
        fun url(g: FieldGeo, time: Long): String {
            val ratio = (g.maxX - g.minX) / (g.maxY - g.minY)
            val w = if (ratio >= 1) MAX_SIDE else max(1, (MAX_SIDE * ratio).toInt())
            val h = if (ratio >= 1) max(1, (MAX_SIDE / ratio).toInt()) else MAX_SIDE
            return String.format(
                Locale.US, "%s?service=WMS&version=1.3.0&request=GetMap&layers=%s&styles=&format=image/jpeg&crs=EPSG:3857" +
                    "&bbox=%.0f,%.0f,%.0f,%.0f&width=%d&height=%d&time=%s",
                WMS, LAYER, g.minX, g.minY, g.maxX, g.maxY, w, h, RadarSources.isoTime(time),
            )
        }
    }
}

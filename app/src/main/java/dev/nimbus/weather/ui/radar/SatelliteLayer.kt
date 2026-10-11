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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.ImageSource
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
class SatelliteLayer(
    /** Runs on the main thread (the map's). */
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val wms: String = WMS,
) {
    private var style: Style? = null
    @Volatile private var geo: FieldGeo? = null
    /** The two pictures on the map: [0] the earlier (or the one standing), [1] the later one over it. */
    private val shownKey = arrayOfNulls<String>(2)
    private val shownGeo = arrayOfNulls<FieldGeo>(2)
    /** The pictures as EUMETSAT sent them (JPEG, a few hundred kB): a day of them fits. */
    private val jpegs = object : LruCache<String, ByteArray>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    /** The pictures unpacked (4 MB each): the two shown and the next. */
    private val bitmaps = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val loading = ConcurrentHashMap<String, Deferred<ByteArray?>>()
    private val unpacking = ConcurrentHashMap.newKeySet<String>()
    /** The last [at]: shown again when a picture it waited for has come. */
    private var last: (() -> Unit)? = null

    /** What is shown: the earlier picture's time, the later one's (null: none over it), how far it shows. */
    internal var shown: Blend? = null
        private set

    /** The earlier picture's source (shown alone while standing). */
    fun source(): ImageSource = empty(SOURCE)

    /** The later picture's source, over the earlier one while playing. */
    fun nextSource(): ImageSource = empty(SOURCE_NEXT)

    private fun empty(id: String) = ImageSource(
        id, LatLngQuad(LatLng(1.0, 0.0), LatLng(1.0, 1.0), LatLng(0.0, 1.0), LatLng(0.0, 0.0)), Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    )

    /** A (new) style: its sources start empty. */
    fun install(style: Style) {
        this.style = style
        shownKey.fill(null); shownGeo.fill(null)
    }

    /** The visible area changed: the picture covers it with a margin (like the radar picture). */
    fun setView(south: Double, north: Double, west: Double, east: Double) {
        val g = geo
        val inside = g != null && FieldGeo.R * Math.toRadians(west) >= g.minX && FieldGeo.R * Math.toRadians(east) <= g.maxX &&
            FieldGeo.mercY(south) >= g.minY && FieldGeo.mercY(north) <= g.maxY
        if (inside) return
        geo = FieldGeo.forView(south, north, west, east, maxSide = MAX_SIDE, minPxKm = 0.0)
    }

    /**
     * Shows the radar's time [time]: standing, its 10-minute picture; [playing], the pictures
     * [stepMs] apart around it blended by where it lies between them – the clouds pass over
     * instead of jumping – and the next ones loaded ahead. A picture not there yet keeps the one
     * before on the map.
     */
    fun at(time: Long, playing: Boolean, stepMs: Long, now: Long = System.currentTimeMillis()) {
        val g = geo ?: return
        last = { at(time, playing, stepMs, now) }
        val b = blend(time, now, playing, stepMs)
        listOfNotNull(b.before, b.after).forEach { fetch(g, it) }
        if (playing) (2..AHEAD).map { b.before + it * stepMs }.filter { it <= newest(now) }.forEach { fetch(g, it) }
        val earlier = picture(g, b.before) ?: return
        put(0, g, key(g, b.before), earlier)
        val later = b.after?.let { picture(g, it) }
        if (later != null) put(1, g, key(g, b.after), later)
        val f = if (later != null) b.f else 0f
        (style?.getLayer(LAYER_NEXT) as? RasterLayer)?.setProperties(PropertyFactory.rasterOpacity(OPACITY * f))
        shown = Blend(b.before, b.after.takeIf { later != null }, f)
    }

    /** The picture unpacked, else on its way: [at] again when it is there. */
    private fun picture(g: FieldGeo, time: Long): Bitmap? {
        val k = key(g, time)
        bitmaps.get(k)?.let { return it }
        val jpeg = jpegs.get(k) ?: return null
        if (unpacking.add(k)) scope.launch {
            val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }
            if (bmp != null) bitmaps.put(k, bmp)
            unpacking.remove(k)
            last?.invoke()
        }
        return null
    }

    /** The JPEG from the cache, else loading – on in the layer's scope, whoever waited for it gone. */
    private fun fetch(g: FieldGeo, time: Long) {
        val k = key(g, time)
        if (jpegs.get(k) != null) return
        loading.getOrPut(k) {
            scope.async {
                load(g, time).also {
                    if (it != null) jpegs.put(k, it)
                    loading.remove(k)
                    if (it != null) last?.invoke()
                }
            }
        }
    }

    private fun put(i: Int, g: FieldGeo, key: String, bmp: Bitmap) {
        if (shownKey[i] == key) return
        val src = style?.getSource(if (i == 0) SOURCE else SOURCE_NEXT) as? ImageSource ?: return
        if (shownGeo[i] !== g) {
            src.setCoordinates(LatLngQuad(LatLng(g.north, g.west), LatLng(g.north, g.east), LatLng(g.south, g.east), LatLng(g.south, g.west)))
            shownGeo[i] = g
        }
        src.setImage(bmp)
        shownKey[i] = key
    }

    private suspend fun load(g: FieldGeo, time: Long): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url(g, time, wms)).build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
        }.getOrNull()
    }

    private fun key(g: FieldGeo, time: Long) = "${g.minX.toLong()},${g.minY.toLong()},${g.maxX.toLong()},${g.maxY.toLong()}@$time"

    /** The earlier picture's time, the later one's (null: none – standing, or the newest), how far the later one shows (0–1). */
    data class Blend(val before: Long, val after: Long?, val f: Float)

    companion object {
        const val SOURCE = "sat"
        const val SOURCE_NEXT = "sat-next"
        /** The later picture's layer (over the earlier one, "sat"). */
        const val LAYER_NEXT = "sat-next"
        /** The satellite's opacity over the map. */
        const val OPACITY = 0.75f
        /** Pictures loaded ahead of playback. */
        private const val AHEAD = 4
        const val WMS = "https://view.eumetsat.int/geoserver/wms"
        const val LAYER = "mtg_fd:rgb_geocolour"
        /** The licence's attribution, with the year of the picture (CC BY 4.0). */
        const val ATTRIBUTION = "Contains modified EUMETSAT Meteosat data %d"
        /** A picture every 10 minutes – standing, and playing the live loop … */
        const val STEP_MS = 10 * 60_000L
        /** … playing a day of the look-back every half hour (48 pictures rather than 144). */
        const val DAY_STEP_MS = 30 * 60_000L
        /** The newest picture is about this old. */
        const val LATENCY_MS = 20 * 60_000L
        /** Longest side of the picture, in pixels (the map scales it). */
        private const val MAX_SIDE = 1024

        /** The newest picture there is at [now]. */
        fun newest(now: Long) = Math.floorDiv(now - LATENCY_MS, STEP_MS) * STEP_MS

        /**
         * The picture for the radar's time [frame] at [now]: one of the steps [stepMs] apart, never
         * newer than the newest there is (the radar's forecast steps lie ahead of the satellite).
         */
        fun timeFor(frame: Long, now: Long, stepMs: Long = STEP_MS): Long = Math.floorDiv(minOf(frame, newest(now)), stepMs) * stepMs

        /**
         * The pictures for the radar's time [time]: standing its own; [playing], the one before and
         * the one after it ([stepMs] apart), the later one showing by where [time] lies between them.
         */
        fun blend(time: Long, now: Long, playing: Boolean, stepMs: Long): Blend {
            if (!playing) return Blend(timeFor(time, now), null, 0f)
            val t = minOf(time, newest(now))
            val before = Math.floorDiv(t, stepMs) * stepMs
            val after = (before + stepMs).takeIf { it <= newest(now) } ?: return Blend(before, null, 0f)
            return Blend(before, after, ((t - before).toFloat() / stepMs).coerceIn(0f, 1f))
        }

        /** The WMS request for the picture of [g] at [time] (in the map's projection). */
        fun url(g: FieldGeo, time: Long, wms: String = WMS): String {
            val ratio = (g.maxX - g.minX) / (g.maxY - g.minY)
            val w = if (ratio >= 1) MAX_SIDE else max(1, (MAX_SIDE * ratio).toInt())
            val h = if (ratio >= 1) max(1, (MAX_SIDE / ratio).toInt()) else MAX_SIDE
            return String.format(
                Locale.US, "%s?service=WMS&version=1.3.0&request=GetMap&layers=%s&styles=&format=image/jpeg&crs=EPSG:3857" +
                    "&bbox=%.0f,%.0f,%.0f,%.0f&width=%d&height=%d&time=%s",
                wms, LAYER, g.minX, g.minY, g.maxX, g.maxY, w, h, RadarSources.isoTime(time),
            )
        }
    }
}

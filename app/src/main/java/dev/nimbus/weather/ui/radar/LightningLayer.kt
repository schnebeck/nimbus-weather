/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/LightningLayer.kt
 * Lightning over the radar: the flashes of the last 15 minutes, seen from the satellite, the newest brightest.
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.ImageSource
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Where lightning struck in the 15 minutes up to the radar's time: the flashes the Lightning
 * Imager of Meteosat Third Generation saw, as the DWD serves them (all of Europe, a minute or two
 * late). The DWD's own colours tell nothing here – each 5-minute step is drawn in one bright
 * colour instead, the older ones fainter, as one picture of the view (like the satellite's): no
 * blank tiles while the time moves on. A picture stands only for its own time: on a new time the
 * old flashes go at once, the new ones come when loaded – once loaded, at once.
 */
class LightningLayer(
    /** Runs on the main thread (the map's). */
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val wms: String = WMS,
) {
    private var style: Style? = null
    @Volatile private var geo: FieldGeo? = null
    private var appliedGeo: FieldGeo? = null
    private var shownKey: String? = null
    private var shownGeo: FieldGeo? = null
    private var shownPicture: Bitmap = EMPTY
    private var wanted: String? = null
    private var job: Job? = null
    /** The DWD's pictures as it sent them (PNG, mostly empty: a few kB each) – a whole day of them fits. */
    private val images = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    /** The pictures as shown, by the radar's step: the last ones at once again. */
    private val pictures = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** Pictures on their way: one passed by while playing still loads, for the next round. */
    private val loading = ConcurrentHashMap<String, Deferred<ByteArray?>>()

    /** The step whose flashes are on the map; null: none. */
    internal var shownStep: Long? = null
        private set

    /** The layer's source: an image of the view (an empty one until the first arrives). */
    fun source(): ImageSource = ImageSource(SOURCE, LatLngQuad(LatLng(1.0, 0.0), LatLng(1.0, 1.0), LatLng(0.0, 1.0), LatLng(0.0, 0.0)), EMPTY)

    /** A (new) style: its source starts empty – the picture shown goes on it again. */
    fun install(style: Style) {
        this.style = style
        appliedGeo = null
        shownGeo?.let { apply(it, shownPicture) }
    }

    /** The visible area changed: the picture covers it with a margin. */
    fun setView(south: Double, north: Double, west: Double, east: Double) {
        val g = geo
        val inside = g != null && FieldGeo.R * Math.toRadians(west) >= g.minX && FieldGeo.R * Math.toRadians(east) <= g.maxX &&
            FieldGeo.mercY(south) >= g.minY && FieldGeo.mercY(north) <= g.maxY
        if (inside) return
        geo = FieldGeo.forView(south, north, west, east, maxSide = MAX_SIDE, minPxKm = 0.0)
    }

    /**
     * Shows the flashes up to [step] (one of [stepFor]'s); null: none (a time ahead of now).
     * Standing, its three 5-minute pictures, the newest brightest; [playing], the 15 minutes as one
     * picture – a third of the requests, as a step shows half a second – and the steps [ahead]
     * loaded before playback reaches them.
     */
    fun show(step: Long?, playing: Boolean = false, ahead: List<Long> = emptyList()) {
        val g = geo ?: return
        job?.cancel()
        ahead.forEach { a -> spans(a, playing).forEach { (end, span) -> fetch(g, end, span) } }
        if (step == null) { wanted = null; setImage(g, NONE, null, EMPTY); return }
        val key = key(g, step) + if (playing) "p" else ""
        wanted = key
        pictures.get(key)?.let { setImage(g, key, step, it); return }
        // the flashes of another time do not stand for this one
        setImage(g, NONE, null, EMPTY)
        job = scope.launch {
            val parts = spans(step, playing).map { (end, span) -> fetch(g, end, span) }.awaitAll()
            val picture = withContext(Dispatchers.Default) { compose(parts.map { it?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) } }) }
            // a picture that failed is asked again next time
            if (parts.all { it != null }) pictures.put(key, picture)
            if (wanted == key) setImage(g, key, step, picture)
        }
    }

    /** The DWD's pictures making up the flashes to [step], each by its end and span – the newest first. */
    private fun spans(step: Long, playing: Boolean): List<Pair<Long, Long>> =
        if (playing) listOf(step to AGES * STEP_MS) else (0 until AGES).map { (step - it * STEP_MS) to STEP_MS }

    /** A picture from the cache, else loading – on in the layer's scope, whoever waited for it gone. */
    private fun fetch(g: FieldGeo, end: Long, span: Long): Deferred<ByteArray?> {
        val key = key(g, end) + "/" + span
        images.get(key)?.let { return CompletableDeferred(it) }
        return loading.getOrPut(key) {
            scope.async { load(g, end, span).also { if (it != null) images.put(key, it); loading.remove(key) } }
        }
    }

    private fun setImage(g: FieldGeo, key: String, step: Long?, bmp: Bitmap) {
        if (shownKey == key) return
        shownKey = key
        shownStep = step
        shownGeo = g
        shownPicture = bmp
        apply(g, bmp)
    }

    private fun apply(g: FieldGeo, bmp: Bitmap) {
        val src = style?.getSource(SOURCE) as? ImageSource ?: return
        if (appliedGeo !== g) {
            src.setCoordinates(LatLngQuad(LatLng(g.north, g.west), LatLng(g.north, g.east), LatLng(g.south, g.east), LatLng(g.south, g.west)))
            appliedGeo = g
        }
        src.setImage(bmp)
    }

    private suspend fun load(g: FieldGeo, end: Long, span: Long): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url(g, end, wms, span)).build()).execute().use { r ->
                if (!r.isSuccessful || r.body.contentType()?.subtype != "png") return@use null
                r.body.bytes()
            }
        }.getOrNull()
    }

    private fun key(g: FieldGeo, time: Long) = "${g.minX.toLong()},${g.minY.toLong()},${g.maxX.toLong()},${g.maxY.toLong()}@$time"

    companion object {
        const val SOURCE = "lightning"
        private const val NONE = "none"
        const val WMS = "https://maps.dwd.de/geoserver/dwd/wms"
        const val LAYER = "dwd:Accumulated_Flash_Geometry"
        /** One picture covers 5 minutes (standing) … */
        const val STEP_MS = 5 * 60_000L
        /** … three of them the 15 minutes shown, the newest brightest. */
        private const val AGES = 3
        private val ALPHA = intArrayOf(255, 150, 70)
        /** A flash reaches the DWD a minute or two after it struck. */
        const val LATENCY_MS = 2 * 60_000L
        /** Bright yellow: apart from the radar's colours, the warnings and the map. */
        private const val COLOUR = 0xFFFFF176.toInt()
        /** Longest side of the picture, in pixels (thin flash outlines need no more). */
        private const val MAX_SIDE = 768
        private val EMPTY: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        /** The steps one over the other, the oldest first and faintest; null parts (failed) left out. */
        internal fun compose(parts: List<Bitmap?>): Bitmap {
            val first = parts.firstOrNull { it != null } ?: return EMPTY
            val out = Bitmap.createBitmap(first.width, first.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(COLOUR, PorterDuff.Mode.SRC_IN) }
            parts.withIndex().reversed().forEach { (age, bmp) ->
                if (bmp == null) return@forEach
                paint.alpha = ALPHA[age]
                canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, out.width, out.height), paint)
            }
            return out
        }

        /**
         * The newest step ending at or before the radar's time [frame] at [now]; null for a time
         * ahead of now (the radar's forecast: no lightning is forecast).
         */
        fun stepFor(frame: Long, now: Long): Long? {
            if (frame > now + STEP_MS / 2) return null
            return Math.floorDiv(minOf(frame, now - LATENCY_MS), STEP_MS) * STEP_MS
        }

        /** While playing: the step of the radar's time [frame], on the quarter hour (one picture of 15 minutes). */
        fun playStep(frame: Long, now: Long): Long? = stepFor(Math.floorDiv(frame, AGES * STEP_MS) * AGES * STEP_MS, now)

        /** The WMS request for the flashes of the [span] (5 minutes) ending at [end], over [g] (the map's projection). */
        fun url(g: FieldGeo, end: Long, wms: String = WMS, span: Long = STEP_MS): String {
            val ratio = (g.maxX - g.minX) / (g.maxY - g.minY)
            val w = if (ratio >= 1) MAX_SIDE else max(1, (MAX_SIDE * ratio).toInt())
            val h = if (ratio >= 1) max(1, (MAX_SIDE / ratio).toInt()) else MAX_SIDE
            return String.format(
                Locale.US, "%s?service=WMS&version=1.3.0&request=GetMap&layers=%s&styles=&format=image/png&transparent=true&crs=EPSG:3857" +
                    "&bbox=%.0f,%.0f,%.0f,%.0f&width=%d&height=%d&time=%s/%s",
                wms, LAYER, g.minX, g.minY, g.maxX, g.maxY, w, h, RadarSources.isoTime(end - span + 1000), RadarSources.isoTime(end),
            )
        }
    }
}

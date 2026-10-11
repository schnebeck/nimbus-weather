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
import kotlinx.coroutines.CoroutineScope
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
import kotlin.math.max

/**
 * Where lightning struck in the 15 minutes up to the radar's time: the flashes the Lightning
 * Imager of Meteosat Third Generation saw, as the DWD serves them (all of Europe, a minute or two
 * late). The DWD's own colours tell nothing here – each 5-minute step is drawn in one bright
 * colour instead, the older ones fainter, as one picture of the view (like the satellite's): no
 * blank tiles while the time moves on.
 */
class LightningLayer(private val scope: CoroutineScope, private val http: OkHttpClient) {
    private var style: Style? = null
    @Volatile private var geo: FieldGeo? = null
    private var shownGeo: FieldGeo? = null
    private var shownKey: String? = null
    private var wanted: String? = null
    private var job: Job? = null
    /** The DWD's pictures of single steps: a step serves three radar times. */
    private val steps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The layer's source: an image of the view (an empty one until the first arrives). */
    fun source(): ImageSource = ImageSource(SOURCE, LatLngQuad(LatLng(1.0, 0.0), LatLng(1.0, 1.0), LatLng(0.0, 1.0), LatLng(0.0, 0.0)), EMPTY)

    fun install(style: Style) { this.style = style }

    /** The visible area changed: the picture covers it with a margin. */
    fun setView(south: Double, north: Double, west: Double, east: Double) {
        val g = geo
        val inside = g != null && FieldGeo.R * Math.toRadians(west) >= g.minX && FieldGeo.R * Math.toRadians(east) <= g.maxX &&
            FieldGeo.mercY(south) >= g.minY && FieldGeo.mercY(north) <= g.maxY
        if (inside) return
        geo = FieldGeo.forView(south, north, west, east, maxSide = MAX_SIDE, minPxKm = 0.0)
    }

    /** Shows the flashes up to [step] (one of [stepFor]'s); null: none (a time ahead of now). */
    fun show(step: Long?) {
        val g = geo ?: return
        if (step == null) { wanted = null; setImage(g, "none", EMPTY); return }
        val key = key(g, step)
        wanted = key
        job?.cancel()
        job = scope.launch {
            val parts = (0 until AGES).map { age -> async { step(g, step - age * STEP_MS) } }.awaitAll()
            val picture = withContext(Dispatchers.Default) { compose(parts) }
            if (wanted == key) withContext(Dispatchers.Main) { setImage(g, key, picture) }
        }
    }

    private suspend fun step(g: FieldGeo, end: Long): Bitmap? {
        val key = key(g, end)
        steps.get(key)?.let { return it }
        return load(g, end)?.also { steps.put(key, it) }
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

    private suspend fun load(g: FieldGeo, end: Long): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url(g, end)).build()).execute().use { r ->
                if (!r.isSuccessful || r.body.contentType()?.subtype != "png") return@use null
                val bytes = r.body.bytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun key(g: FieldGeo, time: Long) = "${g.minX.toLong()},${g.minY.toLong()},${g.maxX.toLong()},${g.maxY.toLong()}@$time"

    companion object {
        const val SOURCE = "lightning"
        const val WMS = "https://maps.dwd.de/geoserver/dwd/wms"
        const val LAYER = "dwd:Accumulated_Flash_Geometry"
        /** One picture covers 5 minutes … */
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

        /** The WMS request for the flashes of the 5 minutes ending at [end], over [g] (the map's projection). */
        fun url(g: FieldGeo, end: Long): String {
            val ratio = (g.maxX - g.minX) / (g.maxY - g.minY)
            val w = if (ratio >= 1) MAX_SIDE else max(1, (MAX_SIDE * ratio).toInt())
            val h = if (ratio >= 1) max(1, (MAX_SIDE / ratio).toInt()) else MAX_SIDE
            return String.format(
                Locale.US, "%s?service=WMS&version=1.3.0&request=GetMap&layers=%s&styles=&format=image/png&transparent=true&crs=EPSG:3857" +
                    "&bbox=%.0f,%.0f,%.0f,%.0f&width=%d&height=%d&time=%s/%s",
                WMS, LAYER, g.minX, g.minY, g.maxX, g.maxY, w, h, RadarSources.isoTime(end - STEP_MS + 1000), RadarSources.isoTime(end),
            )
        }
    }
}

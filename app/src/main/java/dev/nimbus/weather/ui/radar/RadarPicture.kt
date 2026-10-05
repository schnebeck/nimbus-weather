/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarPicture.kt
 * The radar picture of an area: which composites reach into it and where, RainViewer beyond them,
 * rain or snow from the temperature – for the radar loop and the still preview alike.
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

object RadarPicture {
    private const val O = 20037508.342789244

    /** The composites with a step [f] in the area [g]: reaching into it, the future only with a nowcast. */
    fun composites(f: RadarFrame, g: FieldGeo): List<RadarComposite> =
        RadarComposites.all.filter { it.overlaps(g.west, g.east, g.south, g.north) && (!f.isForecast || it.hasNowcast) }

    /** Per composite reaching into the area: the pixels it covers. */
    fun coverage(g: FieldGeo): Map<RadarComposite, BooleanArray> {
        val lat = DoubleArray(g.h) { g.lat(g.my(it.toDouble())) }
        val lon = DoubleArray(g.w) { g.lon(g.mx(it.toDouble())) }
        return RadarComposites.all.filter { it.overlaps(g.west, g.east, g.south, g.north) }.associateWith { c ->
            BooleanArray(g.w * g.h) { i -> c.covers(lat[i / g.w], lon[i % g.w]) }
        }
    }

    /**
     * Does the area reach beyond the composites with an exact coverage (where RainViewer fills in –
     * also where another composite only has its rectangle)?
     */
    fun needsRainViewer(covered: Map<RadarComposite, BooleanArray>, n: Int): Boolean {
        val exact = covered.filterKeys { it.exactCoverage }.values
        return (0 until n).any { i -> exact.none { it[i] } }
    }

    /**
     * The composites the picture shows somewhere: covering a pixel no composite before them with an
     * exact coverage covers ([RadarComposites.all]). Over Hannover the KNMI's rectangle reaches
     * into the area, but the DWD's radars cover it all.
     */
    fun shown(covered: Map<RadarComposite, BooleanArray>, n: Int): List<RadarComposite> {
        val taken = BooleanArray(n)
        return covered.keys.sortedBy { RadarComposites.all.indexOf(it) }.filter { c ->
            val inside = covered.getValue(c)
            val shows = (0 until n).any { inside[it] && !taken[it] }
            if (c.exactCoverage) for (i in 0 until n) if (inside[i]) taken[i] = true
            shows
        }
    }

    /** RainViewer's zoom whose pixels match the picture's, at most [maxZoom] (7: their finest). */
    fun rvZoom(g: FieldGeo, maxZoom: Int = 7): Int =
        (ln(2 * Math.PI * FieldGeo.R / (512 * g.pxM)) / ln(2.0)).roundToInt().coerceIn(3, maxZoom)

    /** RainViewer's tiles of the area at [rvZoom]; [preview]: in the small requests' lane ([RadarStore.rvTile]). */
    suspend fun mosaic(http: OkHttpClient, host: String, path: String, g: FieldGeo, maxZoom: Int = 7, preview: Boolean = false): RvMosaic {
        val z = rvZoom(g, maxZoom)
        val size = 2 * O / (1 shl z)
        val x0 = floor((g.minX + O) / size).toInt(); val x1 = floor((g.maxX + O) / size).toInt()
        val y0 = floor((O - g.maxY) / size).toInt(); val y1 = floor((O - g.minY) / size).toInt()
        val tiles = HashMap<Long, ByteArray>()
        for (y in y0..y1) for (x in x0..x1) {
            if (x !in 0 until (1 shl z) || y !in 0 until (1 shl z)) continue
            RadarStore.rvTile(http, host, path, z, x, y, preview)?.let { tiles[RvMosaic.key(x, y)] = it }
        }
        return RvMosaic(z, tiles)
    }

    /** Snow share from the 2 m temperature for every pixel, per hour of the grid. */
    private val snowCache = HashMap<String, FloatArray?>()

    fun snowAt(g: FieldGeo, time: Long): FloatArray? {
        val grid = WeatherGridStore.gridOverlapping(TileGeo(g.minX, g.minY, g.maxX, g.maxY), time) ?: return null
        val hour = grid.hourIndex(time)
        val key = "${System.identityHashCode(grid)}_${hour}_${g.minX}_${g.maxY}_${g.w}"
        return synchronized(snowCache) {
            snowCache.getOrPut(key) {
                if (snowCache.size > 24) snowCache.clear()
                val field = grid.temp[hour]
                val lat = DoubleArray(g.h) { g.lat(g.my(it.toDouble())) }
                val lon = DoubleArray(g.w) { g.lon(g.mx(it.toDouble())) }
                // the grid is coarse: sample every 4th pixel and spread, fine enough for rain/snow
                val out = FloatArray(g.w * g.h)
                for (y in 0 until g.h step 4) for (x in 0 until g.w step 4) {
                    val s = grid.sampleNear(field, lat[y], lon[x])?.let { RadarPalette.snowFraction(it) } ?: 0f
                    for (yy in y until minOf(y + 4, g.h)) for (xx in x until minOf(x + 4, g.w)) out[yy * g.w + xx] = s
                }
                out.takeIf { a -> a.any { it > 0f } }
            }
        }
    }

    /**
     * The still's RainViewer zoom: 5 (pixels of 1.5 km here, about its radars' own) – one to four
     * tiles for the preview instead of up to nine at 7.
     */
    const val STILL_RV_ZOOM = 5

    /**
     * The still picture of step [f] for the area [g] (the preview): of each composite it shows only
     * the cells of the area ([RadarStore.window]), RainViewer only where they do not reach – all at
     * once – drawn as in the radar loop. Null if it cannot be had; a transparent picture where
     * nothing falls.
     */
    suspend fun still(
        http: OkHttpClient, tl: RadarTimeline, f: RadarFrame, g: FieldGeo,
        /** A composite's cells of the area. */
        load: suspend (RadarComposite, GridWindow) -> ByteArray? = { c, w -> RadarStore.window(http, c, f, w) },
    ): Bitmap? = coroutineScope {
        val covered = coverage(g)
        val layers = shown(covered, g.w * g.h).filter { !f.isForecast || it.hasNowcast }.mapNotNull { c ->
            c.window(g.west, g.east, g.south, g.north)?.let { w -> async { load(c, w)?.let { RadarLayer(c, it, covered[c], w) } } }
        }
        val rv = f.rainViewerPath?.takeIf { needsRainViewer(covered, g.w * g.h) }
            ?.let { async { mosaic(http, tl.rainViewerHost, it, g, STILL_RV_ZOOM, preview = true) } }
        val shown = layers.mapNotNull { it.await() }
        val mosaic = rv?.await()
        if (shown.isEmpty() && mosaic == null) return@coroutineScope null
        val out = IntArray(g.w * g.h)
        RadarField.render(RadarField.extract(g, shown, mosaic), null, null, 0f, g.w, g.h, snowAt(g, f.time), out)
        Bitmap.createBitmap(g.w, g.h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, g.w, 0, 0, g.w, g.h) }
    }
}

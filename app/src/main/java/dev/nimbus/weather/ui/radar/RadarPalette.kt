/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarPalette.kt
 * Unified radar colours for rain and snow, tile recolouring, caching and retries.
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
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

/**
 * Unified radar colour scale. Both DWD (WN composite, dBZ intervals) and RainViewer
 * ("Universal Blue" scheme) tiles are decoded back to reflectivity (dBZ) and recoloured, so the
 * map shows one consistent palette across the German border. Rain goes from white via grey to
 * blue, snow from pale pink to dark violet; the phase comes from the 2 m temperature at each pixel.
 */
object RadarPalette {
    /**
     * dBZ → ARGB for rain (above 0 °C): white → light grey → dark grey → light blue → dark blue.
     * The dark grey stays lighter than the dark base map so weak rain remains visible.
     */
    private val rainStops = listOf(
        8 to 0x80FFFFFF.toInt(), 14 to 0xB0E8EAEE.toInt(), 20 to 0xD0BEC3CB.toInt(), 26 to 0xE07E858F.toInt(),
        32 to 0xF08CC8FF.toInt(), 40 to 0xF53D8BFF.toInt(), 48 to 0xFA1440C8.toInt(), 56 to 0xFF0A1F7A.toInt(),
    )

    /** dBZ → ARGB for snow (0 °C and below): pale pink (white with a hint of pink) → dark violet. */
    private val snowStops = listOf(
        8 to 0x80FFF2F8.toInt(), 16 to 0xB0F7D2E6.toInt(), 24 to 0xD0E7A3CF.toInt(), 32 to 0xE8C86FBF.toInt(),
        40 to 0xF29A45AE.toInt(), 50 to 0xFA6A2390.toInt(), 60 to 0xFF45106E.toInt(),
    )

    private val rainLut = buildLut(rainStops)
    private val snowLut = buildLut(snowStops)

    /** Colours for the legends (light → heavy). */
    val legendRain: List<Int> = listOf(10, 16, 22, 28, 34, 42, 50, 56).map { rainLut[it] }
    val legendSnow: List<Int> = listOf(10, 18, 26, 34, 44, 56).map { snowLut[it] }

    /** [snow]: 0 = rain, 1 = snow, in between sleet (colours are blended). */
    fun colorFor(dbz: Int, snow: Float): Int {
        if (dbz < 8) return 0
        val i = dbz.coerceAtMost(95)
        return when {
            snow <= 0f -> rainLut[i]
            snow >= 1f -> snowLut[i]
            else -> lerpArgb(rainLut[i], snowLut[i], snow)
        }
    }

    fun colorFor(dbz: Int, snow: Boolean): Int = colorFor(dbz, if (snow) 1f else 0f)

    /** Test hook (demo mode): shifts the temperatures used for the rain/snow decision. */
    @Volatile var demoTempOffset = 0f

    /** Fraction of snow from the 2 m temperature: snow ≤ 0 °C, rain ≥ 1 °C, sleet in between. */
    fun snowFraction(tempC: Float): Float = (1f - (tempC + demoTempOffset)).coerceIn(0f, 1f)

    /** The snow scale is only worth showing where it can get this cold (°C, with a margin). */
    const val SNOW_LEGEND_MAX_C = 3f

    /** Snow legend for the lowest temperature in view; unknown (no grid) keeps it. */
    fun showSnowLegend(minTempC: Float?): Boolean = minTempC == null || minTempC + demoTempOffset <= SNOW_LEGEND_MAX_C

    private fun buildLut(stops: List<Pair<Int, Int>>): IntArray = IntArray(96) { dbz ->
        when {
            dbz < stops.first().first -> 0
            dbz >= stops.last().first -> stops.last().second
            else -> {
                val hi = stops.indexOfFirst { it.first > dbz }
                val (d0, c0) = stops[hi - 1]
                val (d1, c1) = stops[hi]
                lerpArgb(c0, c1, (dbz - d0).toFloat() / (d1 - d0))
            }
        }
    }

    private fun lerpArgb(a: Int, b: Int, f: Float): Int {
        fun ch(x: Int, s: Int) = (x ushr s) and 0xFF
        fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * f).toInt().coerceIn(0, 255)
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    // ---- source palettes ---------------------------------------------------------------

    /** DWD WN product legend (GetLegendGraphic): colour → representative dBZ. */
    private val dwdColors: Map<Int, Int> = mapOf(
        0x99FFFF to 8, 0x33FFFF to 11, 0x00CACA to 13, 0x009934 to 17, 0x4DBF1A to 21,
        0x99CC00 to 26, 0xCCE600 to 30, 0xFFFF00 to 35, 0xFFC400 to 39, 0xFF8900 to 44,
        0xFF0000 to 48, 0xB40000 to 53, 0x4848FF to 57, 0x0000CA to 62, 0x990099 to 70,
        0xFF33FF to 80, 0x000000 to 90,
    )

    /** RainViewer "Universal Blue" (rain block) from rainviewer_api_colors_table.csv, ARGB → dBZ. */
    private val rainViewerRain: Map<Int, Int> = buildMap {
        val hex = listOf(
            "63615914", "66635a19", "69665c1e", "6c685d24", "6f6b5f29", "726e612e", "75706234", "78736439", "7c75653e", "7f786744",
            "827b6949", "857d6a4e", "88806c54", "8b826d59", "8e856f5e", "92887164", "9e93756e", "aa9e7978", "b6a97e82", "c2b4828c",
            "cec08796", "d2c48ba0", "d6c88faa", "dacc93b4", "ded097be", "88ddeeff", "6cd1ebff", "51c5e8ff", "36bae5ff", "1baee2ff",
            "00a3e0ff", "009ad5ff", "0091caff", "0088bfff", "007fb4ff", "0077aaff", "0070a3ff", "00699cff", "006295ff", "005b8eff",
            "005588ff", "005180ff", "004e78ff", "004a70ff", "004768ff", "ffee00ff", "ffe000ff", "ffd200ff", "ffc500ff", "ffb700ff",
            "ffaa00ff", "ff9f00ff", "ff9500ff", "ff8b00ff", "ff8100ff", "ff4400ff", "f23600ff", "e62800ff", "d91b00ff", "cd0d00ff",
            "c10000ff", "a80000ff", "8f0000ff", "760000ff", "5d0000ff", "ffaaffff", "ff9fffff", "ff95ffff", "ff8bffff", "ff81ffff",
            "ff77ffff", "ff6cffff", "ff62ffff", "ff58ffff", "ff4effff", "ffffffff",
        )
        hex.forEachIndexed { i, h -> put(rgbaToArgb(h), i - 10) }
    }

    /** RainViewer "Universal Blue" snow block: ARGB → dBZ. */
    private val rainViewerSnow: Map<Int, Int> = buildMap {
        val hex = listOf(
            "cfffff00", "ceffff0c", "cdffff19", "ccffff26", "cbffff33", "cbffff3f", "caffff4c", "c9ffff59", "c8ffff66", "c7ffff72",
            "c7ffff7f", "c6ffff8c", "c5ffff99", "c4ffffa5", "c3ffffb2", "c3ffffbf", "c2ffffcc", "c1ffffd8", "c0ffffe5", "bffffff2",
            "bfffffff", "b8f8ffff", "b2f2ffff", "abebffff", "a5e5ffff", "9fdfffff", "98d8ffff", "92d2ffff", "8bcbffff", "85c5ffff",
            "7fbfffff", "78b8ffff", "72b2ffff", "6babffff", "65a5ffff", "5f9fffff", "5b9bffff", "5898ffff", "5595ffff", "5292ffff",
            "4f8fffff", "4b8bffff", "4888ffff", "4585ffff", "4282ffff", "3f7fffff", "3b7bffff", "3878ffff", "3575ffff", "3272ffff",
            "2f6fffff", "2b6bffff", "2868ffff", "2565ffff", "2262ffff", "1f5fffff", "1b5bffff", "1858ffff", "1555ffff", "1252ffff",
            "0f4fffff", "0c4bffff", "0948ffff", "0645ffff", "0242ffff", "003fffff",
        )
        hex.forEachIndexed { i, h -> put(rgbaToArgb(h), i - 10) }
    }

    private fun rgbaToArgb(rgba: String): Int {
        val v = rgba.toLong(16)
        val r = (v shr 24) and 0xFF
        val g = (v shr 16) and 0xFF
        val b = (v shr 8) and 0xFF
        val a = v and 0xFF
        return ((a shl 24) or (r shl 16) or (g shl 8) or b).toInt()
    }

    enum class Source { DWD, RAINVIEWER }

    // Lock-free: up to 12 tile threads decode at the same time. A synchronized map made them wait
    // for each other on every single pixel.
    private val dwdCache = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private val rvCache = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private const val NONE = -1
    private const val SNOW_FLAG = 0x100

    /**
     * Decodes a source pixel to reflectivity: dBZ (8..95), plus [SNOW_FLAG] if the source itself
     * marks it as snow (RainViewer), or [NONE] for "no precipitation / not radar data".
     */
    fun decode(argb: Int, source: Source): Int {
        if ((argb ushr 24) == 0) return NONE
        return when (source) {
            Source.DWD -> dwdCache[argb] ?: decodeDwd(argb).also { dwdCache[argb] = it }
            Source.RAINVIEWER -> rvCache[argb] ?: decodeRainViewer(argb).also { rvCache[argb] = it }
        }
    }

    /** Maps one source pixel to the display palette without temperature information. */
    fun mapPixel(argb: Int, source: Source): Int {
        val code = decode(argb, source)
        if (code == NONE) return 0
        return colorFor(code and 0xFF, (code and SNOW_FLAG) != 0)
    }

    private fun decodeDwd(argb: Int): Int {
        if ((argb ushr 24) < 200) return NONE          // "no data" grey is semi transparent
        val rgb = argb and 0xFFFFFF
        dwdColors[rgb]?.let { return it }
        var best = -1
        var bestD = Int.MAX_VALUE
        for ((c, dbz) in dwdColors) {
            val d = dist(rgb, c)
            if (d < bestD) { bestD = d; best = dbz }
        }
        // Radar site circles / borders (magenta, grey) are not in the legend.
        return if (bestD <= 900) best else NONE
    }

    private fun decodeRainViewer(argb: Int): Int {
        rainViewerRain[argb]?.let { return if (it < 8) NONE else it }
        rainViewerSnow[argb]?.let { return if (it < 8) NONE else it or SNOW_FLAG }
        var best = 0
        var bestSnow = false
        var bestD = Int.MAX_VALUE
        for ((c, dbz) in rainViewerRain) {
            val d = dist4(argb, c)
            if (d < bestD) { bestD = d; best = dbz; bestSnow = false }
        }
        for ((c, dbz) in rainViewerSnow) {
            val d = dist4(argb, c)
            if (d < bestD) { bestD = d; best = dbz; bestSnow = true }
        }
        return if (best < 8) NONE else best or (if (bestSnow) SNOW_FLAG else 0)
    }

    private fun dist(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    private fun dist4(a: Int, b: Int): Int {
        val da = (a ushr 24) - (b ushr 24)
        return dist(a, b) + da * da
    }

    /**
     * Recoloured tile, or null if the tile contains no precipitation at all.
     * With [geo] and [timeMs] the rain/snow decision uses the 2 m temperature at every pixel.
     */
    fun recolor(bitmap: Bitmap, source: Source, geo: TileGeo? = null, timeMs: Long? = null): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        // Tiles at low zoom are wider than the grid: look it up by overlap, not by the tile centre.
        val grid = if (geo != null && timeMs != null) WeatherGridStore.gridOverlapping(geo) else null
        val field = if (grid != null && timeMs != null) grid.temp[grid.hourIndex(timeMs)] else null
        // Latitude depends only on the row and longitude only on the column (Mercator tiles).
        val rowLat = if (field != null && geo != null) DoubleArray(h) { geo.latAt((it + 0.5) / h) } else null
        val colLon = if (field != null && geo != null) DoubleArray(w) { geo.lonAt((it + 0.5) / w) } else null
        var any = false
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val code = decode(px[i], source)
                if (code == NONE) {
                    px[i] = 0
                    continue
                }
                val sourceSnow = if ((code and SNOW_FLAG) != 0) 1f else 0f
                val snow = if (field != null && rowLat != null && colLon != null) {
                    grid?.sampleNear(field, rowLat[y], colLon[x])?.let { snowFraction(it) } ?: sourceSnow
                } else sourceSnow
                val c = colorFor(code and 0xFF, snow)
                px[i] = c
                if (c != 0) any = true
            }
        }
        if (!any) return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}

/** Geographic extent of a Web-Mercator tile (EPSG:3857 metres). */
class TileGeo(private val minX: Double, private val minY: Double, private val maxX: Double, private val maxY: Double) {
    fun lonAt(fx: Double) = Math.toDegrees((minX + fx * (maxX - minX)) / R)
    fun latAt(fy: Double) = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh((maxY - fy * (maxY - minY)) / R)))
    val centerLat: Double get() = latAt(0.5)
    val centerLon: Double get() = lonAt(0.5)
    val north: Double get() = latAt(0.0)
    val south: Double get() = latAt(1.0)
    val west: Double get() = lonAt(0.0)
    val east: Double get() = lonAt(1.0)

    companion object {
        private const val R = 6378137.0
        private const val O = 20037508.342789244

        fun fromBbox(bbox: String?): TileGeo? {
            val v = bbox?.split(',')?.mapNotNull { it.toDoubleOrNull() } ?: return null
            return if (v.size == 4) TileGeo(v[0], v[1], v[2], v[3]) else null
        }

        fun fromXyz(z: Int, x: Int, y: Int): TileGeo {
            val size = 2 * O / (1 shl z)
            val minX = -O + x * size
            val maxY = O - y * size
            return TileGeo(minX, maxY - size, minX + size, maxY)
        }
    }
}

/**
 * Network interceptor: raw radar tiles get an explicit cache lifetime. Past frames never change
 * (the time is part of the URL), so they are kept for DWD's full 3-day archive and the history can
 * be replayed offline. Nowcast frames are recomputed every 5 minutes; RainViewer paths are unique.
 */
class RadarCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response
        val url = request.url
        val maxAge = when (url.host) {
            "maps.dwd.de" -> {
                // Analysed frames never change once published: keep them three days (the DWD archive).
                // Nowcast frames are recomputed every 5 minutes; 10 minutes keeps the loop current
                // without reloading all of it on every opening. Tiles without time (satellite,
                // warnings) change every few minutes.
                val time = url.queryParameter("time")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                val latest = RadarSources.latestAnalysis
                when {
                    time == null -> 300
                    latest != null && time <= latest -> 3 * 24 * 3600
                    time < System.currentTimeMillis() - 15 * 60_000L -> 3 * 24 * 3600
                    else -> 600
                }
            }
            "tilecache.rainviewer.com" -> 24 * 3600
            else -> return response
        }
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$maxAge")
            .removeHeader("Pragma").removeHeader("Expires")
            .build()
    }
}

/**
 * Application interceptor that recolours radar tiles for MapLibre. It runs after the HTTP cache,
 * so the raw tiles are cached long-term while the colours (which depend on the current
 * temperature grid) are recomputed; MapLibre may keep the recoloured variant for 10 minutes.
 */
/** Radar tile requests since start (debug statistics). */
object RadarTileStats {
    val requests = java.util.concurrent.atomic.AtomicInteger()
    val frames = java.util.concurrent.atomic.AtomicInteger()
}

class RadarTileInterceptor : Interceptor {
    private fun empty(response: Response): Response =
        response.newBuilder().code(204).message("No Content").body(ByteArray(0).toResponseBody(null))
            .removeHeader("Content-Type").removeHeader("Content-Length")
            .header("Cache-Control", "max-age=600").build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val source = when {
            url.host == "maps.dwd.de" && url.queryParameter("layers")?.contains("Radar") == true -> RadarPalette.Source.DWD
            url.host == "tilecache.rainviewer.com" -> RadarPalette.Source.RAINVIEWER
            else -> null
        }
        val response = chain.proceed(request)
        if (source != null) RadarTileStats.requests.incrementAndGet()
        if (source == null || !response.isSuccessful) return response
        val contentType = response.header("Content-Type") ?: ""
        if (!contentType.startsWith("image/png")) {
            // e.g. a WMS ServiceException – hand MapLibre an empty tile instead of an error.
            response.close()
            return empty(response)
        }
        val geo: TileGeo?
        val time: Long?
        if (source == RadarPalette.Source.DWD) {
            geo = TileGeo.fromBbox(url.queryParameter("bbox"))
            time = url.queryParameter("time")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        } else {
            // /v2/radar/<id>/512/{z}/{x}/{y}/2/1_1.png
            val seg = url.pathSegments
            val z = seg.getOrNull(4)?.toIntOrNull()
            val x = seg.getOrNull(5)?.toIntOrNull()
            val y = seg.getOrNull(6)?.toIntOrNull()
            geo = if (z != null && x != null && y != null) TileGeo.fromXyz(z, x, y) else null
            time = RadarSources.rainViewerTime("/" + seg.take(3).joinToString("/"))
        }
        val bytes = response.body.bytes()
        val opts = BitmapFactory.Options().apply { inPremultiplied = false }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return response.newBuilder().body(bytes.toResponseBody("image/png".toMediaType())).build()
        val recolored = RadarPalette.recolor(decoded, source, geo, time)
        decoded.recycle()
        // No precipitation in this tile: skip the PNG encoding entirely.
        if (recolored == null) return empty(response)
        val out = ByteArrayOutputStream(bytes.size)
        recolored.compress(Bitmap.CompressFormat.PNG, 100, out)
        recolored.recycle()
        return response.newBuilder()
            .body(out.toByteArray().toResponseBody("image/png".toMediaType()))
            .removeHeader("Content-Length")
            // Short lifetime: reopening the radar is instant, while temperature-dependent
            // colours (rain / snow) are recomputed after the grid refreshes.
            .header("Cache-Control", "max-age=600")
            .build()
    }
}

/**
 * Application interceptor: DWD's WMS occasionally fails under load and MapLibre does not retry
 * failed tiles, which would leave holes in single animation frames. One retry fixes that.
 */
class RetryInterceptor(private val hosts: Set<String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host !in hosts) return chain.proceed(request)
        val first = try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            if (chain.call().isCanceled()) throw e
            return chain.proceed(request)
        }
        if (first.code < 500) return first
        first.close()
        return chain.proceed(request)
    }
}

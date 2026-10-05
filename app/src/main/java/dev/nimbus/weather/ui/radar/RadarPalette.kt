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


/**
 * Unified radar colour scale. Both DWD (WN composite, dBZ intervals) and RainViewer
 * ("Universal Blue" scheme) tiles are decoded back to reflectivity (dBZ) and recoloured, so the
 * map shows one consistent palette across the German border. The phase comes from the 2 m
 * temperature at each pixel (rain above 1 °C, snow below 0 °C, blended in between).
 *
 * Each scale runs through two dark points with a different hue on either side, so neighbouring
 * steps stay apart where most echoes are (17–35 dBZ) and the rare extremes stand out:
 * - rain: light green → dark green (17) → yellow (33) → dark red (49) → magenta (56)
 * - snow: turquoise → dark blue (17) → white (33) → dark violet (49) → pink (56)
 * No colour appears in both scales. The colours are opaque (only the weakest echoes fade in), so
 * the map shows them exactly as in the legend; roads, borders and names are drawn on top.
 */
object RadarPalette {
    private val rainStops = listOf(
        8 to 0x59A8F0A0.toInt(), 10 to 0x8CA8F0A0.toInt(), 12 to 0xFF96E592.toInt(), 17 to 0xFF145F2A.toInt(),
        33 to 0xFFF5E43A.toInt(), 49 to 0xFF6E1212.toInt(), 56 to 0xFFFF2BD6.toInt(),
    )

    private val snowStops = listOf(
        8 to 0x597EEBF0.toInt(), 10 to 0x8C7EEBF0.toInt(), 12 to 0xFF6FD9E8.toInt(), 17 to 0xFF1A3C8C.toInt(),
        33 to 0xFFFFFFFF.toInt(), 49 to 0xFF4A1C80.toInt(), 56 to 0xFFFF9EE8.toInt(),
    )

    /**
     * The calm alternative (setting "Radar colours: blue"): rain from the lightest light blue to
     * the darkest dark blue, snow from the lightest pink to the darkest violet – opaque as well.
     */
    private val blueRainStops = listOf(
        8 to 0x59D6ECFF.toInt(), 10 to 0x8CD6ECFF.toInt(), 12 to 0xFFCDE6FF.toInt(), 24 to 0xFF7FB6F5.toInt(),
        36 to 0xFF2F6FE0.toInt(), 46 to 0xFF153C9E.toInt(), 56 to 0xFF07144D.toInt(),
    )
    private val blueSnowStops = listOf(
        8 to 0x59FFE4F2.toInt(), 10 to 0x8CFFE4F2.toInt(), 12 to 0xFFFFDDEF.toInt(), 24 to 0xFFF0A6D8.toInt(),
        36 to 0xFFC45CC0.toInt(), 46 to 0xFF7E2899.toInt(), 56 to 0xFF3A0B5C.toInt(),
    )

    private val contrastLuts = buildLut(rainStops) to buildLut(snowStops)
    private val blueLuts = buildLut(blueRainStops) to buildLut(blueSnowStops)
    /** The same in steps of 1/[FINE] dBZ, for smoothed reflectivities (one lookup per pixel). */
    private const val FINE = 8
    private val contrastFine = fine(contrastLuts.first) to fine(contrastLuts.second)
    private val blueFine = fine(blueLuts.first) to fine(blueLuts.second)

    /** The scale in use (from the settings); the tile URLs carry it, so cached tiles never mix scales. */
    @Volatile var scheme: dev.nimbus.weather.data.model.RadarColors = dev.nimbus.weather.data.model.RadarColors.CONTRAST
    private val luts get() = if (scheme == dev.nimbus.weather.data.model.RadarColors.BLUE) blueLuts else contrastLuts
    private val rainLut get() = luts.first
    private val snowLut get() = luts.second

    /** Range of the legends; colours are sampled evenly, so a position on the bar is linear in dBZ. */
    const val LEGEND_MIN_DBZ = 10
    const val LEGEND_MAX_DBZ = 56

    /** Colours for the legends (light → heavy). */
    val legendRain: List<Int> get() = (LEGEND_MIN_DBZ..LEGEND_MAX_DBZ step 2).map { rainLut[it] }
    val legendSnow: List<Int> get() = (LEGEND_MIN_DBZ..LEGEND_MAX_DBZ step 2).map { snowLut[it] }

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

    /** As [colorFor], for a smoothed (fractional) reflectivity: no steps between whole dBZ. */
    fun colorFor(dbz: Float, snow: Float): Int {
        if (dbz < 8f) return 0
        val (rain, snowLut) = if (scheme == dev.nimbus.weather.data.model.RadarColors.BLUE) blueFine else contrastFine
        val i = (dbz * FINE).toInt().coerceAtMost(rain.lastIndex)
        return when {
            snow <= 0f -> rain[i]
            snow >= 1f -> snowLut[i]
            else -> lerpArgb(rain[i], snowLut[i], snow)
        }
    }

    private fun fine(lut: IntArray) = IntArray((lut.size - 1) * FINE + 1) { k ->
        val i = k / FINE
        val f = (k % FINE).toFloat() / FINE
        if (f == 0f) lut[i] else lerpArgb(lut[i], lut[i + 1], f)
    }

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

    internal const val MIN_DBZ = 8f

    /**
     * Box filter of width 2[r]+1, horizontal then vertical, both running along the rows (the
     * vertical pass keeps one running sum per column in [col]) – fast on large tiles.
     */
    internal fun boxBlur(a: FloatArray, tmp: FloatArray, col: FloatArray, w: Int, h: Int, r: Int) {
        val k = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val o = y * w
            var sum = 0f
            for (x in 0..minOf(r, w - 1)) sum += a[o + x]
            for (x in 0 until w) {
                tmp[o + x] = sum * k
                val add = x + r + 1
                val sub = x - r
                if (add < w) sum += a[o + add]
                if (sub >= 0) sum -= a[o + sub]
            }
        }
        if (h == 1) { System.arraycopy(tmp, 0, a, 0, w); return }
        java.util.Arrays.fill(col, 0f)
        for (y in 0..minOf(r, h - 1)) { val o = y * w; for (x in 0 until w) col[x] += tmp[o + x] }
        for (y in 0 until h) {
            val o = y * w
            for (x in 0 until w) a[o + x] = col[x] * k
            val add = y + r + 1
            val sub = y - r
            if (add < h) { val oa = add * w; for (x in 0 until w) col[x] += tmp[oa + x] }
            if (sub >= 0) { val os = sub * w; for (x in 0 until w) col[x] -= tmp[os + x] }
        }
    }

    /** Wet share at which a smoothed pixel is drawn half transparent (the old cell edge). */
    internal fun edgeAlpha(wet: Float): Float = ((wet - 0.35f) / 0.3f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
}

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/KnmiRadar.kt
 * The KNMI radar composite (the Netherlands, the North Sea, north-western Germany): reflectivity
 * every 5 minutes as raw values, on the cells of the DWD grid – it fills in where the DWD's radars
 * do not reach.
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

import java.time.Instant
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The KNMI composite as the app keeps it: one byte per cell of 0.01° – dBZ (8–95), 0 where it is
 * dry, [NO_DATA] where no radar reaches. Its cells are the DWD grid's ([DwdGrid]: the same west
 * edge and step), so both are drawn alike – the same smoothing, colours and motion.
 */
object KnmiRadar {
    private const val WCS = "https://geoservices.knmi.nl/adagucserver?dataset=RADAR&service=WCS&version=1.0.0&request=GetCoverage"
    const val LON0 = 1.4
    const val LAT1 = 55.97
    const val W = 944
    const val H = 707
    const val STEP = 0.01
    val LON1 = LON0 + W * STEP
    val LAT0 = LAT1 - H * STEP

    /** A cell no radar reaches – not dry: there RainViewer shows. */
    const val NO_DATA = 0xFF
    /** Below this reflectivity a cell counts as dry (as the DWD's). */
    private const val WET_DBZ = 8

    /**
     * The composite at [time] as a grid of numbers (a few hundred kB, about 20 kB compressed –
     * mostly "-32", no echo). Width and height given: so the cells are exactly 0.01°.
     */
    fun url(time: Long): String =
        "$WCS&coverage=Reflectivity&crs=EPSG:4326&format=aaigrid&bbox=$LON0,${"%.2f".format(java.util.Locale.ROOT, LAT0)}," +
            "${"%.2f".format(java.util.Locale.ROOT, LON1)},$LAT1&width=$W&height=$H&time=${Instant.ofEpochMilli(time)}"

    fun overlaps(west: Double, east: Double, south: Double, north: Double) = east > LON0 && west < LON1 && north > LAT0 && south < LAT1

    /** The code at a position: dBZ, 0 dry, [NO_DATA] outside the radars' reach or the grid. */
    fun at(codes: ByteArray?, lat: Double, lon: Double): Int {
        if (codes == null) return NO_DATA
        val r = floor((LAT1 - lat) / STEP).toInt()
        val c = floor((lon - LON0) / STEP).toInt()
        if (r !in 0 until H || c !in 0 until W) return NO_DATA
        return codes[r * W + c].toInt() and 0xFF
    }

    /**
     * The answer (ESRI ASCII grid: a header, then the rows from the north) to codes; null if it is
     * not this grid. The header's "NODATA_value" marks the cells beyond the radars.
     */
    fun parse(text: String): ByteArray? {
        val header = HashMap<String, String>()
        var pos = 0
        while (header.size < 6) {
            val end = text.indexOf('\n', pos).takeIf { it >= 0 } ?: return null
            val parts = text.substring(pos, end).trim().split(Regex("\\s+"))
            if (parts.size != 2 || parts[0].first().isDigit() || parts[0].first() == '-') break
            header[parts[0].lowercase()] = parts[1]
            pos = end + 1
        }
        if (header["ncols"]?.toIntOrNull() != W || header["nrows"]?.toIntOrNull() != H) return null
        val noData = header["nodata_value"]?.toFloatOrNull()
        val out = ByteArray(W * H)
        val numbers = Numbers(text, pos)
        for (i in out.indices) {
            val v = numbers.next() ?: return null
            out[i] = when {
                noData != null && v == noData -> NO_DATA
                v < WET_DBZ -> 0
                else -> v.roundToInt().coerceIn(WET_DBZ, 95)
            }.toByte()
        }
        return out
    }

    /** Numbers of a text one after the other – the grid's 667,000 without a string each. */
    private class Numbers(private val s: String, private var i: Int) {
        fun next(): Float? {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) return null
            val neg = s[i] == '-'
            if (neg) i++
            var v = 0f
            var digits = 0
            while (i < s.length && s[i].isDigit()) { v = v * 10 + (s[i] - '0'); i++; digits++ }
            if (i < s.length && s[i] == '.') {
                i++
                var f = 0.1f
                while (i < s.length && s[i].isDigit()) { v += (s[i] - '0') * f; f /= 10; i++; digits++ }
            }
            return if (digits == 0) null else if (neg) -v else v
        }
    }
}

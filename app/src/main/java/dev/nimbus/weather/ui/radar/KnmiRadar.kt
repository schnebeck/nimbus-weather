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

import dev.nimbus.weather.data.remote.USER_AGENT
import dev.nimbus.weather.data.remote.getText
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The KNMI composite: its raw reflectivity (no colours to read back) on cells of 0.01° – the DWD
 * grid's ([DwdRadar]: the same west edge and step). Past steps only; the cells beyond its radars
 * say so ([RadarComposite.NO_DATA]), so it covers its rectangle.
 */
object KnmiRadar : RadarComposite {
    private const val WCS = "https://geoservices.knmi.nl/adagucserver?dataset=RADAR&service=WCS&version=1.0.0&request=GetCoverage"
    private const val WMS_CAPABILITIES = "https://geoservices.knmi.nl/adagucserver?dataset=RADAR&service=WMS&request=GetCapabilities"
    const val W = 944
    const val H = 707
    override val id = "knmi"
    override val lon0 = 1.4
    override val lat1 = 55.97
    override val w = W
    override val h = H
    override val hasNowcast = false
    override val exactCoverage = false

    private const val KEEP_MS = 4L * 24 * 3_600_000L
    /** Below this reflectivity a cell counts as dry (as the DWD's). */
    private const val WET_DBZ = 8

    override fun covers(lat: Double, lon: Double) = lat in lat0..lat1 && lon in lon0..lon1

    override fun key(frame: RadarFrame) = "knmi_${frame.time / 60_000L}"

    /** The newest composite: the default time of its map service (about 3 kB compressed). */
    override suspend fun latest(http: OkHttpClient): Long? {
        val body = http.getText(Request.Builder().url(WMS_CAPABILITIES).header("User-Agent", USER_AGENT).build())
        val m = Regex("""<Dimension name="time"[^>]*default="([^"]+)"""").find(body) ?: return null
        return runCatching { Instant.parse(m.groupValues[1]).toEpochMilli() }.getOrNull()
    }

    /**
     * The composite at [time] as a grid of numbers (a few hundred kB, about 20 kB compressed –
     * mostly "-32", no echo). Width and height given: so the cells are exactly 0.01°.
     */
    fun url(time: Long): String =
        "$WCS&coverage=Reflectivity&crs=EPSG:4326&format=aaigrid&bbox=$lon0,${"%.2f".format(Locale.ROOT, lat0)}," +
            "${"%.2f".format(Locale.ROOT, lon1)},$lat1&width=$W&height=$H&time=${Instant.ofEpochMilli(time)}"

    override suspend fun fetch(http: OkHttpClient, frame: RadarFrame): ByteArray? {
        if (frame.isForecast) return null
        val text = RadarDecode.text(http, url(frame.time)) ?: return null
        return withContext(RadarDecode.decoding) { parse(text) }
    }

    override fun expired(base: String, modified: Long, now: Long, latestIssue: Long?): Boolean? {
        if (!base.startsWith("knmi_")) return null
        return base.removePrefix("knmi_").toLongOrNull()?.let { now - it * 60_000L > KEEP_MS } ?: true
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
                noData != null && v == noData -> RadarComposite.NO_DATA
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

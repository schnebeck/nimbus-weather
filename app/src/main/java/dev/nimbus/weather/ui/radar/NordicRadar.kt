/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/NordicRadar.kt
 * MET Norway's Nordic radar composite (Norway, Sweden, Finland, Denmark; 1 km, every 5 minutes):
 * its daily files, the newest step, a window of it as dBZ read from a grey picture.
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The composite as MET Norway serves it (THREDDS, CC BY 4.0): one file per day, updated every 5
 * minutes about 10 minutes late, with a map service that answers any window of it in any size –
 * here in our grid of [RadarComposite.STEP] cells (or coarser), the reflectivity as greys that
 * read back to dBZ by its colour bar ([NordicLegend]). Dry cells come transparent; where the radars
 * do not reach says a mask of its own ([NordicCoverage]).
 */
object NordicRadar {
    private const val WMS = "https://thredds.met.no/thredds/wms/remotesensing/reflectivity-nordic"
    private const val FILE = "yrwms-nordic.mos.pcappi-0-dbz.noclass-clfilter-novpr-clcorr-block.nordiclcc-1000"
    /** The composite's area: west, north edge and its size in degrees. */
    const val LON0 = -8.0
    const val LAT1 = 73.1
    const val WIDTH_DEG = 48.8
    const val HEIGHT_DEG = 20.0
    /** The reflectivity range of the greys, and how many of them. */
    const val LO = 0.0
    const val HI = 80.0
    const val BANDS = 250
    /** Below this a cell counts as dry (as the DWD's). */
    private const val WET_DBZ = 8

    private val day = DateTimeFormatter.ofPattern("yyyy/MM/'$FILE'.yyyyMMdd'.nc'").withZone(ZoneOffset.UTC)

    /** The map service of the day of [time] (UTC). */
    fun service(time: Long): String = "$WMS/${day.format(Instant.ofEpochMilli(time))}"

    /** A map of [layer] over [bbox] (west,south,east,north) at [time], [w] × [h] pixels, in greys over [range]. */
    fun mapUrl(layer: String, time: Long, bbox: String, w: Int, h: Int, range: String = "$LO,$HI"): String =
        "${service(time)}?service=WMS&version=1.3.0&request=GetMap&layers=$layer&styles=default-scalar/seq-Greys" +
            "&crs=CRS:84&bbox=$bbox&width=$w&height=$h&format=image/png&transparent=true" +
            "&colorscalerange=$range&numcolorbands=$BANDS&belowmincolor=transparent&time=${Instant.ofEpochMilli(time)}"

    /** The newest step (the day's file says it: a small request), null if it cannot be had. */
    suspend fun latest(http: OkHttpClient, now: Long = System.currentTimeMillis()): Long? =
        listOf(now, now - 24 * 3_600_000L).firstNotNullOfOrNull { t ->
            runCatching {
                val root = http.getJson("${service(t)}?service=WMS&version=1.3.0&request=GetMetadata&item=layerDetails&layerName=equivalent_reflectivity_factor") as? JsonObject
                (root?.get("nearestTimeIso") as? JsonPrimitive)?.content?.let { Instant.parse(it).toEpochMilli() }
            }.getOrNull()
        }

    /** The cells [window] of [grid] at [time] as codes: dBZ (8–95), 0 where dry; null if they cannot be had. */
    suspend fun fetch(http: OkHttpClient, grid: RadarComposite, time: Long, window: GridWindow): ByteArray? {
        val legend = NordicLegend.values ?: return null
        val px = RadarDecode.pixels(http, mapUrl("equivalent_reflectivity_factor", time, grid.bbox(window), window.w, window.h), window.w, window.h)
            ?: return null
        return codes(px, legend)
    }

    /** Greys to codes: transparent dry, every grey the dBZ of its band in the colour bar ([legend]: grey → dBZ). */
    fun codes(px: IntArray, legend: FloatArray): ByteArray = ByteArray(px.size) { i ->
        val argb = px[i]
        if ((argb ushr 24) < 128) 0
        else legend[argb and 0xFF].let { v -> if (v < WET_DBZ) 0 else v.roundToInt().coerceIn(WET_DBZ, 95) }.toByte()
    }

    /**
     * The colour bar ([BANDS] greys, the highest value at the top) as a table grey → dBZ: each grey
     * the value of the band whose grey is nearest (the greys are not linear).
     */
    fun legendTable(bar: IntArray): FloatArray {
        val band = (HI - LO) / bar.size
        val greys = bar.map { it and 0xFF }
        return FloatArray(256) { g ->
            val i = greys.indices.minBy { abs(greys[it] - g) }
            (HI - (i + 0.5) * band).toFloat()
        }
    }
}

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/DwdRadar.kt
 * The DWD radar composite (Germany and around it): reflectivity every 5 minutes plus a 2-hour
 * nowcast, read back from the colours of its map service.
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/**
 * The DWD composite as the app keeps it: one byte per cell of 0.01° over the DWD area (about
 * 0.7 km east–west and 1.1 km north–south, close to the radar's own 1 km) – read back from the
 * colours of its WMS picture. Where its radars reach: [DwdCoverage] (the "no data" grey around it).
 */
object DwdRadar : RadarComposite {
    const val WMS = "https://maps.dwd.de/geoserver/dwd/wms"
    internal const val LAYER = "dwd:Radar_wn-product_1x1km_ger"
    private const val ANALYSIS_LAYER = "Radar_wn-analysis_1x1km_ger"
    const val W = 1740
    const val H = 1070
    const val LON0 = 1.4
    const val LAT1 = 56.3
    override val id = "dwd"
    override val lon0 = LON0
    override val lat1 = LAT1
    override val w = W
    override val h = H
    override val hasNowcast = true
    override val exactCoverage = true

    private const val ANALYSIS_KEEP_MS = 4L * 24 * 3_600_000L
    private const val NOWCAST_KEEP_MS = 30 * 60_000L

    /** Inside the area its radars reach – the grid's rectangle until the mask is loaded. */
    override fun covers(lat: Double, lon: Double): Boolean =
        if (DwdCoverage.ready) DwdCoverage.covers(lat, lon) else lat in lat0..lat1 && lon in lon0..lon1

    /** The newest analysis: the default time of its analysis layer (a small capabilities request). */
    override suspend fun latest(http: OkHttpClient): Long? {
        val url = "https://maps.dwd.de/geoserver/dwd/$ANALYSIS_LAYER/ows?service=WMS&version=1.3.0&request=GetCapabilities"
        val body = http.getText(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
        val m = Regex("""<Dimension name="time"[^>]*default="([^"]+)"""").find(body) ?: return null
        return runCatching { Instant.parse(m.groupValues[1]).toEpochMilli() }.getOrNull()
    }

    /** A nowcast step carries the analysis it was computed from ([RadarFrame.issue]). */
    fun key(frame: RadarFrame, issue: Long?): String =
        if (frame.isForecast) "dwd_${frame.time / 60_000L}_n${(issue ?: 0L) / 60_000L}" else "dwd_${frame.time / 60_000L}"

    override fun key(frame: RadarFrame): String = key(frame, frame.issue)

    /** The cells [window] of the step at [time] as a WMS picture, one pixel per cell. */
    fun url(time: Long, window: GridWindow = whole): String =
        "$WMS?service=WMS&version=1.1.1&request=GetMap&layers=$LAYER&styles=&format=image/png&transparent=true" +
            "&srs=EPSG:4326&bbox=${bbox(window)}&width=${window.w}&height=${window.h}&time=${RadarSources.isoTime(time)}"

    override suspend fun fetch(http: OkHttpClient, frame: RadarFrame, window: GridWindow): ByteArray? =
        RadarDecode.png(http, url(frame.time, window), window.w, window.h, RadarPalette.Source.DWD)

    override suspend fun prepare(http: OkHttpClient) = DwdCoverage.ensure(http)

    /** Offline: a nowcast step from an earlier analysis is better than none. */
    override fun stalePrefix(frame: RadarFrame): String? = if (frame.isForecast) "dwd_${frame.time / 60_000L}_n" else null

    /** Analyses are kept 4 days; a nowcast until its time has passed or a newer analysis is half an hour old. */
    override fun expired(base: String, modified: Long, now: Long, latestIssue: Long?): Boolean? {
        if (!base.startsWith("dwd_")) return null
        if ("_n" in base) {
            val issue = base.substringAfter("_n").toLongOrNull()?.times(60_000L) ?: return true
            val time = base.removePrefix("dwd_").substringBefore("_n").toLongOrNull()?.times(60_000L) ?: return true
            return time < now - NOWCAST_KEEP_MS || latestIssue != null && issue < latestIssue && now - modified > NOWCAST_KEEP_MS
        }
        val time = base.removePrefix("dwd_").toLongOrNull()?.times(60_000L) ?: return true
        return now - time > ANALYSIS_KEEP_MS
    }
}

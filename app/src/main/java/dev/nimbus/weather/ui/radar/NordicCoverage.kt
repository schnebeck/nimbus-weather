/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/NordicCoverage.kt
 * Where the Nordic composite's radars reach, and its colour bar: both read once from MET Norway
 * and kept on the device.
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

import okhttp3.OkHttpClient

/** The radars' reach: MET Norway's "no data" layer on the overview grid – white where they measure. */
internal object NordicCoverage {
    const val W = 1220
    const val H = 500

    private val mask = CoverageMask(
        "nordic_coverage.png", NordicRadar.LON0, NordicRadar.LAT1 - NordicRadar.HEIGHT_DEG,
        NordicRadar.LON0 + NordicRadar.WIDTH_DEG, NordicRadar.LAT1, W, H,
        url = { http ->
            NordicRadar.latest(http)?.let { t ->
                NordicRadar.mapUrl("is_nodata", t, NordicOverview.bbox(NordicOverview.whole), W, H, "0,1")
            }
        },
        classify = ::compute,
    )

    fun covers(lat: Double, lon: Double): Boolean = mask.covers(lat, lon)
    val ready: Boolean get() = mask.ready
    suspend fun ensure(http: OkHttpClient) { mask.ensure(http); NordicLegend.ensure(http) }

    /** Covered: an opaque light grey ("no data" 0 – the greys run white to black); outside the area: transparent. */
    fun compute(argb: IntArray, w: Int, h: Int): BooleanArray = BooleanArray(w * h) { i -> (argb[i] ushr 24) > 128 && (argb[i] and 0xFF) > 127 }

    internal fun setForTest(m: BooleanArray) = mask.set(m)
}

/** The composite's colour bar as a table grey → dBZ ([NordicRadar.legendTable]). */
internal object NordicLegend {
    @Volatile var values: FloatArray? = null
        internal set

    suspend fun ensure(http: OkHttpClient) {
        if (values != null) return
        val bar = keptPicture(http, "nordic_legend.png", 1, NordicRadar.BANDS, 30L * 24 * 3_600_000L) {
            NordicRadar.latest(http)?.let { t ->
                "${NordicRadar.service(t)}?service=WMS&version=1.3.0&request=GetLegendGraphic&layer=equivalent_reflectivity_factor" +
                    "&styles=default-scalar/seq-Greys&palette=seq-Greys&colorbaronly=true&width=1&height=${NordicRadar.BANDS}" +
                    "&numcolorbands=${NordicRadar.BANDS}&colorscalerange=${NordicRadar.LO},${NordicRadar.HI}"
            }
        } ?: return
        values = NordicRadar.legendTable(bar)
    }
}

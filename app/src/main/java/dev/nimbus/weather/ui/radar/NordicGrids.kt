/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/NordicGrids.kt
 * The Nordic composite as two grids: an overview of 0.04° cells (the whole area per step) for a
 * zoomed-out view, 1 km cells in windows of the picture area for a closer one.
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
import kotlin.math.roundToInt

/**
 * One grid of the Nordic composite ([NordicRadar]): cells of [step] degrees over the whole area.
 * Past steps only (no nowcast); where its radars reach the mask says ([NordicCoverage]) – before
 * it is loaded, nowhere (RainViewer stays).
 */
abstract class NordicGrid(final override val id: String, final override val step: Double) : RadarComposite {
    final override val lon0 = NordicRadar.LON0
    final override val lat1 = NordicRadar.LAT1
    final override val w = (NordicRadar.WIDTH_DEG / step).roundToInt()
    final override val h = (NordicRadar.HEIGHT_DEG / step).roundToInt()
    final override val hasNowcast = false
    final override val exactCoverage = true

    override fun covers(lat: Double, lon: Double) = NordicCoverage.covers(lat, lon)
    override suspend fun latest(http: OkHttpClient) = NordicRadar.latest(http)
    override fun key(frame: RadarFrame) = "${id}_${frame.time / 60_000L}"

    override suspend fun fetch(http: OkHttpClient, frame: RadarFrame, window: GridWindow): ByteArray? =
        if (frame.isForecast) null else NordicRadar.fetch(http, this, frame.time, window)

    override fun expired(base: String, modified: Long, now: Long, latestIssue: Long?): Boolean? {
        if (!base.startsWith("${id}_")) return null
        val minute = base.removePrefix("${id}_").substringBefore('_').toLongOrNull() ?: return true
        return now - minute * 60_000L > KEEP_MS
    }

    override suspend fun prepare(http: OkHttpClient) = NordicCoverage.ensure(http)

    companion object {
        /** Kept as long as the DWD's analyses. */
        const val KEEP_MS = 4L * 24 * 3_600_000L
        /**
         * The 1 km cells up to a window of this many (1000 × 1000: about 1.3 s and 100 kB a step;
         * the whole grid the service refuses) – a larger picture area takes the overview.
         */
        const val MAX_FINE_CELLS = 1_000_000
    }
}

/** The whole area in 0.04° cells (about 2–4 km): 1220 × 500 per step, about 85 kB from the service. */
object NordicOverview : NordicGrid("nordic4", 0.04) {
    override fun serves(g: FieldGeo) = !NordicFine.serves(g)
}

/** 1 km cells in windows of the picture area: the whole grid (4880 × 2000) the service refuses. */
object NordicFine : NordicGrid("nordic1", RadarComposite.STEP) {
    override val windowed = true
    override fun serves(g: FieldGeo) = window(g.west, g.east, g.south, g.north, margin = 0).let { it != null && it.size <= MAX_FINE_CELLS }
}

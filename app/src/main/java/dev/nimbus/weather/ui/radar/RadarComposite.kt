/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarComposite.kt
 * A national radar composite as the radar view uses it: a grid of 0.01° cells, where its radars
 * reach, its steps – the processing knows none of them by name.
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
import kotlin.math.floor

/**
 * A radar composite of a weather service: one byte per cell of [STEP] degrees – the reflectivity
 * in dBZ (8–95), 0 where it is dry, [NO_DATA] where no radar reaches. All lie on the same cells
 * (their west edges and steps alike), so they are drawn alike: the same smoothing, colours and
 * motion ([RadarField]). In the picture the first composite covering a spot shows it
 * ([RadarComposites.all]); beyond them all, RainViewer.
 */
interface RadarComposite {
    /** Prefix of its steps in the store ("dwd", "knmi"). */
    val id: String
    val lon0: Double
    val lat1: Double
    val w: Int
    val h: Int
    val lon1: Double get() = lon0 + w * STEP
    val lat0: Double get() = lat1 - h * STEP

    /** Whether it has steps ahead (a nowcast); else past steps only. */
    val hasNowcast: Boolean

    /**
     * Whether [covers] marks exactly where its radars reach (a mask) – else it is its rectangle,
     * and the cells beyond the radars say so themselves ([NO_DATA]).
     */
    val exactCoverage: Boolean

    fun covers(lat: Double, lon: Double): Boolean

    /** Its newest step now (a small request), null if it cannot be had. */
    suspend fun latest(http: OkHttpClient): Long?

    /** Its store name of the step [frame]. */
    fun key(frame: RadarFrame): String

    /**
     * The cells [window] of the step [frame] as codes (row 0 at the window's north edge), null if
     * they cannot be had.
     */
    suspend fun fetch(http: OkHttpClient, frame: RadarFrame, window: GridWindow = whole): ByteArray?

    /** Offline: the store name prefix of an older version of [frame] to show instead (null: none). */
    fun stalePrefix(frame: RadarFrame): String? = null

    /** Whether its stored file [base] (written at [modified]) has expired; null: not its file. */
    fun expired(base: String, modified: Long, now: Long, latestIssue: Long?): Boolean?

    /** What it needs before its first picture (the DWD: the mask of where its radars reach). */
    suspend fun prepare(http: OkHttpClient) {}

    companion object {
        const val STEP = 0.01
        const val NO_DATA = 0xFF
    }
}

/** Whether the composite reaches into the rectangle (degrees). */
fun RadarComposite.overlaps(west: Double, east: Double, south: Double, north: Double): Boolean =
    east > lon0 && west < lon1 && north > lat0 && south < lat1

/** The code at a position: dBZ, 0 dry, [RadarComposite.NO_DATA] outside its grid. */
fun RadarComposite.codeAt(codes: ByteArray, lat: Double, lon: Double): Int {
    val r = floor((lat1 - lat) / RadarComposite.STEP).toInt()
    val c = floor((lon - lon0) / RadarComposite.STEP).toInt()
    if (r !in 0 until h || c !in 0 until w) return RadarComposite.NO_DATA
    return codes[r * w + c].toInt() and 0xFF
}

/**
 * A composite's step for the picture: its codes in [window] (all its cells, or those of the
 * area), and per picture pixel whether it covers it (null: all).
 */
class RadarLayer(val composite: RadarComposite, val codes: ByteArray, val inside: BooleanArray?, val window: GridWindow = composite.whole)

object RadarComposites {
    /** In this order: where the first covers a spot, it shows it. */
    val all: List<RadarComposite> = listOf(DwdRadar, KnmiRadar)

    /** Where the composites keep what they prepare (the DWD's coverage mask). */
    @Volatile var dir: java.io.File? = null

    /**
     * The composite a place's time line follows: the first covering it – a place beyond them all
     * follows the first (the DWD's, the one with a nowcast).
     */
    fun anchorFor(lat: Double, lon: Double, among: List<RadarComposite> = all): RadarComposite =
        among.firstOrNull { it.covers(lat, lon) } ?: among.first()

    /** Prepares all of them – none failing the others ([RadarComposite.prepare]). */
    suspend fun prepare(http: OkHttpClient) = all.forEach { runCatching { it.prepare(http) } }
}

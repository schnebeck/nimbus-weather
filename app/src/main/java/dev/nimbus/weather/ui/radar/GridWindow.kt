/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/GridWindow.kt
 * A rectangle of a composite's cells: what a small picture needs of it, fetched alone or cut
 * from the whole step.
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

import java.util.Locale
import kotlin.math.floor

/**
 * The cells of a composite in columns [col0] ..< col0 + [w] and rows [row0] ..< row0 + [h] (row 0
 * at its north edge) – the preview's area is a few hundred cells across, the whole DWD grid
 * 1.9 million.
 */
data class GridWindow(val col0: Int, val row0: Int, val w: Int, val h: Int) {
    val size: Int get() = w * h
}

/** All its cells. */
val RadarComposite.whole: GridWindow get() = GridWindow(0, 0, w, h)

/**
 * Its cells under the rectangle (degrees) and [margin] cells around it, as far as its grid
 * reaches; null if it does not reach into it.
 */
fun RadarComposite.window(west: Double, east: Double, south: Double, north: Double, margin: Int = 2): GridWindow? {
    val c0 = (floor((west - lon0) / RadarComposite.STEP).toInt() - margin).coerceAtLeast(0)
    val c1 = (floor((east - lon0) / RadarComposite.STEP).toInt() + margin).coerceAtMost(w - 1)
    val r0 = (floor((lat1 - north) / RadarComposite.STEP).toInt() - margin).coerceAtLeast(0)
    val r1 = (floor((lat1 - south) / RadarComposite.STEP).toInt() + margin).coerceAtMost(h - 1)
    return if (c1 < c0 || r1 < r0) null else GridWindow(c0, r0, c1 - c0 + 1, r1 - r0 + 1)
}

/** The cells of [window] out of a whole step. */
fun RadarComposite.cut(codes: ByteArray, window: GridWindow): ByteArray {
    val out = ByteArray(window.size)
    for (r in 0 until window.h) System.arraycopy(codes, (window.row0 + r) * w + window.col0, out, r * window.w, window.w)
    return out
}

/**
 * The window as a request's "bbox" (west, south, east, north): its cells' edges – with its width
 * and height in pixels the service answers exactly these cells.
 */
fun RadarComposite.bbox(window: GridWindow): String {
    fun f(v: Double) = "%.2f".format(Locale.ROOT, v)
    val step = RadarComposite.STEP
    return "${f(lon0 + window.col0 * step)},${f(lat1 - (window.row0 + window.h) * step)}," +
        "${f(lon0 + (window.col0 + window.w) * step)},${f(lat1 - window.row0 * step)}"
}

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RvMosaic.kt
 * RainViewer's cells of a picture area: one byte per pixel – dBZ, snow, dry.
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

import kotlin.math.floor

/**
 * RainViewer cells (Europe): one 512 × 512 tile per entry, keyed by [key], one byte per pixel –
 * dBZ, plus [SNOW] where the source marks snow, 0 where dry. Missing tiles count as dry.
 */
class RvMosaic(val z: Int, val tiles: Map<Long, ByteArray>) {
    private val size = 2 * O / (1 shl z)

    /** Code at a Mercator position, -1 outside the tiles held. */
    fun at(mx: Double, my: Double): Int {
        val fx = (mx + O) / size
        val fy = (O - my) / size
        val tx = floor(fx).toInt()
        val ty = floor(fy).toInt()
        val t = tiles[key(tx, ty)] ?: return -1
        val px = ((fx - tx) * 512).toInt().coerceIn(0, 511)
        val py = ((fy - ty) * 512).toInt().coerceIn(0, 511)
        return t[py * 512 + px].toInt() and 0xFF
    }

    companion object {
        const val SNOW = 0x80
        private const val O = 20037508.342789244
        fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    }
}

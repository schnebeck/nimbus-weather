/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/TileGeo.kt
 * The geographic extent of a Web-Mercator rectangle (a map tile, a picture area).
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

/** Geographic extent of a Web-Mercator tile (EPSG:3857 metres). */
class TileGeo(private val minX: Double, private val minY: Double, private val maxX: Double, private val maxY: Double) {
    fun lonAt(fx: Double) = Math.toDegrees((minX + fx * (maxX - minX)) / R)
    fun latAt(fy: Double) = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh((maxY - fy * (maxY - minY)) / R)))
    val centerLat: Double get() = latAt(0.5)

    /** The same tile grown by [px] pixels of a [w]×[h] image on every side. */
    fun grown(px: Int, w: Int, h: Int): TileGeo {
        val dx = (maxX - minX) / w * px
        val dy = (maxY - minY) / h * px
        return TileGeo(minX - dx, minY - dy, maxX + dx, maxY + dy)
    }

    fun bbox(): String = String.format(java.util.Locale.US, "%.3f,%.3f,%.3f,%.3f", minX, minY, maxX, maxY)
    val centerLon: Double get() = lonAt(0.5)
    val north: Double get() = latAt(0.0)
    val south: Double get() = latAt(1.0)
    val west: Double get() = lonAt(0.0)
    val east: Double get() = lonAt(1.0)

    companion object {
        private const val R = 6378137.0
        private const val O = 20037508.342789244

        fun fromBbox(bbox: String?): TileGeo? {
            val v = bbox?.split(',')?.mapNotNull { it.toDoubleOrNull() } ?: return null
            return if (v.size == 4) TileGeo(v[0], v[1], v[2], v[3]) else null
        }

        fun fromXyz(z: Int, x: Int, y: Int): TileGeo {
            val size = 2 * O / (1 shl z)
            val minX = -O + x * size
            val maxY = O - y * size
            return TileGeo(minX, maxY - size, minX + size, maxY)
        }
    }
}


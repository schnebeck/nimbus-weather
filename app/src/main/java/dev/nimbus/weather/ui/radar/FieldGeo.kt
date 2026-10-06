/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/FieldGeo.kt
 * The Web-Mercator rectangle of the radar picture and its pixels.
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

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The Web-Mercator rectangle (metres) shown as [w] × [h] square pixels: the view with a margin,
 * so panning a little needs no new picture.
 */
class FieldGeo(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double, val w: Int, val h: Int) {
    /** Mercator metres per pixel. */
    val pxM: Double get() = (maxX - minX) / w
    fun mx(x: Double) = minX + (x + 0.5) * pxM
    fun my(y: Double) = maxY - (y + 0.5) * pxM
    fun lon(mx: Double) = Math.toDegrees(mx / R)
    fun lat(my: Double) = Math.toDegrees(atan(sinh(my / R)))
    val north get() = lat(maxY)
    val south get() = lat(minY)
    val west get() = lon(minX)
    val east get() = lon(maxX)
    val centerLat get() = lat((minY + maxY) / 2)
    /** Real kilometres per pixel at the centre. */
    val pxKm: Double get() = pxM * cos(Math.toRadians(centerLat)) / 1000.0

    /** Does the view [s]..[n], [wst]..[e] lie inside, at a resolution this field still serves? */
    fun serves(s: Double, n: Double, wst: Double, e: Double, viewPxM: Double): Boolean {
        val x0 = R * Math.toRadians(wst); val x1 = R * Math.toRadians(e)
        val y0 = mercY(s); val y1 = mercY(n)
        return x0 >= minX && x1 <= maxX && y0 >= minY && y1 <= maxY && viewPxM in pxM / 2.5..pxM * 1.6
    }

    companion object {
        const val R = 6378137.0
        fun mercY(lat: Double) = R * ln(tan(Math.PI / 4 + Math.toRadians(lat.coerceIn(-85.0, 85.0)) / 2))

        /**
         * The field for a view: its bounds grown by [margin] on every side, at most [maxSide]
         * pixels on the long side, never finer than [minPxKm] (the radar has 1 km cells).
         */
        fun forView(s: Double, n: Double, wst: Double, e: Double, maxSide: Int = 1024, margin: Double = 0.2, minPxKm: Double = 0.12): FieldGeo {
            val x0 = R * Math.toRadians(wst); val x1 = R * Math.toRadians(e)
            val y0 = mercY(s); val y1 = mercY(n)
            val gx = (x1 - x0) * margin; val gy = (y1 - y0) * margin
            val minX = x0 - gx; val maxX = x1 + gx; val minY = y0 - gy; val maxY = y1 + gy
            val latC = Math.toDegrees(atan(sinh((minY + maxY) / 2 / R)))
            val minPxM = minPxKm * 1000.0 / cos(Math.toRadians(latC))
            val pxM = max(max(maxX - minX, maxY - minY) / maxSide, minPxM)
            val w = ceil((maxX - minX) / pxM).toInt().coerceAtLeast(1)
            val h = ceil((maxY - minY) / pxM).toInt().coerceAtLeast(1)
            return FieldGeo(minX, maxY - h * pxM, minX + w * pxM, maxY, w, h)
        }
    }
}

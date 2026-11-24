/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/DwdProjection.kt
 * Where a position lies in the DWD's composite grid, and that grid laid under a grid of degrees.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Fable 5.2 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Fable 5.2 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.nimbus.weather.ui.radar

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The grid of the DWD's composites ("DE1200"): [COLS] by [ROWS] cells of 1 km in a polar
 * stereographic projection of the WGS 84 ellipsoid, true to scale at 60 degrees north, the
 * meridian of 10 degrees east running straight down. Its first row is the northern one.
 */
internal object DwdProjection {
    const val COLS = 1100
    const val ROWS = 1200
    private const val CELL_M = 1000.0
    private const val A = 6378137.0
    private const val B = 6356752.3142451802
    private const val LON_0 = 10.0
    private const val LAT_TRUE = 60.0
    /** The projection's coordinates (m) of the pole, counted from the middle of the grid's first cell. */
    private const val X_0 = 543196.83521776402
    private const val Y_0 = 3622588.8619310022
    private val e = sqrt(1 - (B / A) * (B / A))
    private val scale = A * m(Math.toRadians(LAT_TRUE)) / t(Math.toRadians(LAT_TRUE))

    private fun m(phi: Double) = cos(phi) / sqrt(1 - (e * sin(phi)).pow(2))

    private fun t(phi: Double): Double {
        val s = e * sin(phi)
        return tan(PI / 4 - phi / 2) / ((1 - s) / (1 + s)).pow(e / 2)
    }

    /**
     * The grid's column and row at a position, with their fractions: (0, 0) is the upper left
     * corner of its first cell, ([COLS], [ROWS]) the lower right corner of its last.
     */
    fun cell(lat: Double, lon: Double): Pair<Double, Double> {
        val rho = scale * t(Math.toRadians(lat))
        val lambda = Math.toRadians(lon - LON_0)
        val x = X_0 + rho * sin(lambda)
        val y = Y_0 - rho * cos(lambda)
        return (x / CELL_M + 0.5) to (0.5 - y / CELL_M)
    }
}

/**
 * The composite's grid under a grid of degrees: [w] by [h] cells of [step] degrees from [lon0]
 * east and from [lat1] south. Which of its cells lies under the middle of each of these, the
 * projection tells exactly only at nodes [NODE] cells apart; between four nodes the place is
 * weighed from theirs - off by less than a hundredth of a cell, over a distance on which the
 * projection hardly bends.
 */
internal class DwdResampler(private val lon0: Double, private val lat1: Double, private val step: Double, val w: Int, val h: Int) {
    private val nx = (w + NODE - 1) / NODE + 1
    private val ny = (h + NODE - 1) / NODE + 1

    /** Column and row of the composite at every node, row by row. */
    private val nodes: DoubleArray by lazy {
        DoubleArray(nx * ny * 2).also { a ->
            for (j in 0 until ny) for (i in 0 until nx) {
                val (col, row) = DwdProjection.cell(lat1 - j * NODE * step, lon0 + i * NODE * step)
                a[(j * nx + i) * 2] = col
                a[(j * nx + i) * 2 + 1] = row
            }
        }
    }

    /**
     * For every cell, row by row, the composite's cell under its middle
     * (row * [DwdProjection.COLS] + column), -1 where the composite does not reach.
     */
    fun sources(): IntArray {
        val n = nodes
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val upper = y / NODE * nx * 2
            val lower = upper + nx * 2
            val down = (y % NODE + 0.5) / NODE
            for (i in 0 until nx - 1) {
                // column and row at both ends of this stretch between two nodes, at the height of the cells' middles
                val a = upper + i * 2
                val b = lower + i * 2
                val col0 = n[a] + (n[b] - n[a]) * down
                val row0 = n[a + 1] + (n[b + 1] - n[a + 1]) * down
                val dCol = (n[a + 2] + (n[b + 2] - n[a + 2]) * down - col0) / NODE
                val dRow = (n[a + 3] + (n[b + 3] - n[a + 3]) * down - row0) / NODE
                var col = col0 + dCol / 2
                var row = row0 + dRow / 2
                for (x in i * NODE until minOf(w, (i + 1) * NODE)) {
                    val inside = col >= 0.0 && row >= 0.0 && col < DwdProjection.COLS && row < DwdProjection.ROWS
                    out[y * w + x] = if (inside) row.toInt() * DwdProjection.COLS + col.toInt() else -1
                    col += dCol
                    row += dRow
                }
            }
        }
        return out
    }

    companion object {
        const val NODE = 20
    }
}

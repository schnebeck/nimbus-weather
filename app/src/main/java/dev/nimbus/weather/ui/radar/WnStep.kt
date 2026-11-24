/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/WnStep.kt
 * One step of the DWD's composite as its file gives it: the reflectivity and where no radar reaches.
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

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A step of the WN composite (the analysis, or a step of its nowcast) out of its HDF5 file: the
 * numbers of the grid ([DwdProjection]) and how they read - a number times [gain] plus [offset]
 * is the reflectivity in dBZ, [nodata] marks a cell no radar reaches, [undetect] one without an echo.
 */
internal class WnStep(private val grid: Grid16, private val gain: Double, private val offset: Double, private val nodata: Int, private val undetect: Int) {

    /** The code of each number: its dBZ rounded (8-95) from [MIN_DBZ] on, 0 below, without an echo and without data. */
    private fun codeTable(): ByteArray = ByteArray(NUMBERS) { v ->
        val dbz = v * gain + offset
        if (v == nodata || v == undetect || dbz < MIN_DBZ) 0 else dbz.roundToInt().coerceIn(MIN_CODE, MAX_CODE).toByte()
    }

    /** The step on the grid of [DwdRadar]: one code per cell. */
    fun codes(): ByteArray {
        val table = codeTable()
        val out = ByteArray(DwdRadar.W * DwdRadar.H)
        val values = grid.values
        val sources = radarSources()
        for (cell in out.indices) {
            val source = sources[cell]
            if (source >= 0) out[cell] = table[(values[2 * source].toInt() and 0xFF) or ((values[2 * source + 1].toInt() and 0xFF) shl 8)]
        }
        return out
    }

    /** Where the radars reach, on the grid of [DwdCoverage]: every cell with data. */
    fun reach(): BooleanArray {
        val sources = coverageCells.sources()
        return BooleanArray(DwdCoverage.W * DwdCoverage.H) { cell -> sources[cell] >= 0 && grid.at(sources[cell]) != nodata }
    }

    companion object {
        /** The weakest echo shown: below it the DWD's own picture stays empty too. */
        const val MIN_DBZ = 7.0
        private const val MIN_CODE = 8
        private const val MAX_CODE = 95
        private const val NUMBERS = 65536
        private const val DATA = "dataset1/data1/data"
        private const val SCALING = "dataset1/data1/what"
        private const val PLACE = "where"
        /** How far a corner named in the file may lie from the grid's own (cells). */
        private const val CORNER_SLACK = 0.05

        private val radarCells by lazy { DwdResampler(DwdRadar.LON0, DwdRadar.LAT1, RadarComposite.STEP, DwdRadar.W, DwdRadar.H) }
        /** The composite's cell under each of the radar grid's, kept while memory allows: every step of a loop reads it. */
        private var radarSourcesKept: java.lang.ref.SoftReference<IntArray>? = null

        @Synchronized private fun radarSources(): IntArray =
            radarSourcesKept?.get() ?: radarCells.sources().also { radarSourcesKept = java.lang.ref.SoftReference(it) }
        private val coverageCells by lazy {
            DwdResampler(DwdCoverage.LON0, DwdCoverage.LAT1, (DwdCoverage.LON1 - DwdCoverage.LON0) / DwdCoverage.W, DwdCoverage.W, DwdCoverage.H)
        }

        /**
         * The step in the HDF5 file [file]; null if it is not one of this grid: other dimensions,
         * corners elsewhere, the scaling missing, or a file of another make.
         */
        fun read(file: ByteArray): WnStep? = runCatching {
            val h5 = Hdf5(file)
            val grid = h5.grid16(h5.path(DATA))
            val scaling = h5.doubles(h5.path(SCALING))
            val place = h5.doubles(h5.path(PLACE))
            val fits = grid.rows == DwdProjection.ROWS && grid.cols == DwdProjection.COLS &&
                corner(place, "UL", 0, 0) && corner(place, "LR", DwdProjection.COLS, DwdProjection.ROWS)
            if (!fits) return null
            WnStep(grid, scaling.getValue("gain"), scaling.getValue("offset"), scaling.getValue("nodata").toInt(), scaling.getValue("undetect").toInt())
        }.getOrNull()

        /** Whether the corner [name] the file gives in degrees lies at [col], [row] of the grid. */
        private fun corner(place: Map<String, Double>, name: String, col: Int, row: Int): Boolean {
            val (c, r) = DwdProjection.cell(place.getValue(name + "_lat"), place.getValue(name + "_lon"))
            return abs(c - col) < CORNER_SLACK && abs(r - row) < CORNER_SLACK
        }
    }
}

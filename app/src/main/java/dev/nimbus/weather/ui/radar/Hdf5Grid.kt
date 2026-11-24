/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/Hdf5Grid.kt
 * A data set of 16-bit numbers in an HDF5 file: its rows and columns, unpacked chunk by chunk.
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

import java.util.zip.Inflater

/** [rows] by [cols] numbers of 16 bits, row by row from the first, each little endian in [values]. */
internal class Grid16(val rows: Int, val cols: Int, val values: ByteArray) {
    /** The number in the cell [index] (row * cols + column). */
    fun at(index: Int): Int = (values[2 * index].toInt() and 0xFF) or ((values[2 * index + 1].toInt() and 0xFF) shl 8)
}

private const val CELL_BYTES = 2
private const val DEFLATE = 1
private const val CONTIGUOUS = 1
private const val CHUNKED = 2
private const val CHUNK_NODE = 1
/** More cells than any composite has: a file claiming more is not read. */
private const val MAX_CELLS = 16_000_000L

/**
 * The data set at [header] as a grid: two dimensions of unsigned 16-bit numbers, stored in one
 * piece or in chunks, the chunks plain or packed with deflate (any other packing: an exception).
 */
internal fun Hdf5.grid16(header: Long): Grid16 {
    val messages = messages(header)
    val space = messages.first { it.type == Hdf5.DATASPACE }
    require(u8(space.at + 1) == 2)
    val dims = space.at + if (u8(space.at) == 1) 8 else 4
    val rows = u64(dims)
    val cols = u64(dims + 8)
    require(rows > 0 && cols > 0 && rows * cols <= MAX_CELLS)
    val type = messages.first { it.type == Hdf5.DATATYPE }
    // whole numbers of two bytes, the low one first
    require(u8(type.at) and 0x0F == 0 && u32(type.at + 4) == CELL_BYTES && u8(type.at + 1) and 1 == 0)
    val packed = messages.firstOrNull { it.type == Hdf5.FILTERS }?.let { deflateOnly(it) } ?: false
    val layout = messages.first { it.type == Hdf5.LAYOUT }
    require(u8(layout.at) == 3)
    val grid = Grid16(rows.toInt(), cols.toInt(), ByteArray((rows * cols).toInt() * CELL_BYTES))
    when (u8(layout.at + 1)) {
        CONTIGUOUS -> System.arraycopy(bytes, at(u64(layout.at + 2), grid.values.size), grid.values, 0, grid.values.size)
        CHUNKED -> {
            // the chunk's dimensions: rows, columns and the size of a number
            require(u8(layout.at + 2) == 3)
            val chunkRows = u32(layout.at + 11)
            val chunkCols = u32(layout.at + 15)
            require(chunkRows > 0 && chunkCols > 0 && chunkRows.toLong() * chunkCols <= MAX_CELLS)
            chunks(u64(layout.at + 3), chunkRows, chunkCols, packed, grid, 0)
        }
        else -> throw IllegalArgumentException("layout")
    }
    return grid
}

/** Whether the filter message [m] names deflate, and nothing else. */
private fun Hdf5.deflateOnly(m: Hdf5.Message): Boolean {
    require(u8(m.at) == 1 && u8(m.at + 1) == 1 && u16(m.at + 8) == DEFLATE)
    return true
}

/** The chunks under the tree node at [address] into [grid], each at its row and column. */
private fun Hdf5.chunks(address: Long, chunkRows: Int, chunkCols: Int, packed: Boolean, grid: Grid16, depth: Int) {
    val n = at(address, Hdf5.NODE_HEAD)
    require(tag(n, "TREE") && u8(n + 4) == CHUNK_NODE && depth < Hdf5.MAX_DEPTH)
    val level = u8(n + 5)
    // a key: the chunk's size and filter mask, then its place in each of the three dimensions
    val key = 8 + 8 * 3
    for (i in 0 until u16(n + 6)) {
        val entry = n + Hdf5.NODE_HEAD + i * (key + 8)
        val child = u64(entry + key)
        if (level > 0) {
            chunks(child, chunkRows, chunkCols, packed, grid, depth + 1)
            continue
        }
        val size = u32(entry)
        val plain = !packed || u32(entry + 4) and 1 != 0
        val start = at(child, size)
        val chunk = if (plain) bytes.copyOfRange(start, start + size) else inflate(start, size, chunkRows * chunkCols * CELL_BYTES)
        place(chunk, chunkRows, chunkCols, u64(entry + 8), u64(entry + 16), grid)
    }
}

/** The [size] packed bytes at [start] unpacked to exactly [length] bytes. */
private fun Hdf5.inflate(start: Int, size: Int, length: Int): ByteArray {
    val inflater = Inflater()
    inflater.setInput(bytes, start, size)
    val out = ByteArray(length)
    var n = 0
    while (n < length && !inflater.finished()) {
        val k = inflater.inflate(out, n, length - n)
        if (k == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
        n += k
    }
    inflater.end()
    require(n == length)
    return out
}

/** The rows of [chunk] (its upper left cell at [row0], [col0] of the grid) into [grid], as far as it reaches. */
private fun place(chunk: ByteArray, chunkRows: Int, chunkCols: Int, row0: Long, col0: Long, grid: Grid16) {
    require(row0 in 0 until grid.rows && col0 in 0 until grid.cols && chunk.size >= chunkRows * chunkCols * CELL_BYTES)
    val width = minOf(chunkCols, grid.cols - col0.toInt()) * CELL_BYTES
    for (r in 0 until minOf(chunkRows, grid.rows - row0.toInt())) {
        val to = ((row0.toInt() + r) * grid.cols + col0.toInt()) * CELL_BYTES
        System.arraycopy(chunk, r * chunkCols * CELL_BYTES, grid.values, to, width)
    }
}

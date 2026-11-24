/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/Hdf5.kt
 * The structure of an HDF5 file as the DWD writes its radar composites: groups and attributes.
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

/**
 * Reads the one kind of HDF5 file the DWD's radar composites come in: the first superblock
 * format with addresses of 8 bytes, groups kept as symbol tables, object headers of the first
 * version. Numbers are little endian. A file of another kind, or one cut short, ends the reading
 * with an exception: every position is checked against the file's length, every walk through a
 * tree against [MAX_DEPTH].
 */
internal class Hdf5(val bytes: ByteArray) {
    /** One message of an object header: its [type], where its data begin and their [size]. */
    class Message(val type: Int, val at: Int, val size: Int)

    /** The object header of the root group. */
    val root: Long

    init {
        require(bytes.size > SUPERBLOCK_SIZE && tag(0, "\u0089HDF"))
        require(bytes[8].toInt() == 0 && bytes[13].toInt() == 8 && bytes[14].toInt() == 8 && u64(24) == 0L)
        root = u64(ROOT_HEADER)
    }

    /** A position in the file as an index: inside it, [room] bytes before its end. */
    fun at(position: Long, room: Int = 0): Int {
        require(position >= 0 && position <= bytes.size - room)
        return position.toInt()
    }

    fun u8(p: Int): Int = bytes[p].toInt() and 0xFF
    fun u16(p: Int): Int = u8(p) or (u8(p + 1) shl 8)
    fun u32(p: Int): Int = u16(p) or (u16(p + 2) shl 16)
    fun u64(p: Int): Long = (u32(p).toLong() and 0xFFFFFFFFL) or (u32(p + 4).toLong() shl 32)

    /** Whether the four characters [name] stand at [p]. */
    fun tag(p: Int, name: String): Boolean = name.indices.all { bytes[p + it] == name[it].code.toByte() }

    /** The text at [p] up to its zero byte. */
    fun text(p: Int): String {
        var end = p
        while (bytes[end].toInt() != 0) end++
        return String(bytes, p, end - p, Charsets.US_ASCII)
    }

    /** The messages of the object header at [header], those of its continuation blocks included. */
    fun messages(header: Long): List<Message> {
        val h = at(header, HEADER_SIZE)
        require(u8(h) == 1)
        val count = u16(h + 2)
        val blocks = ArrayDeque<Pair<Int, Int>>()
        blocks += (h + HEADER_SIZE) to u32(h + 8)
        val out = ArrayList<Message>(count)
        while (blocks.isNotEmpty() && out.size < count) {
            val (start, size) = blocks.removeFirst()
            var p = start
            while (p + MESSAGE_HEAD <= start + size && out.size < count) {
                val m = Message(u16(p), p + MESSAGE_HEAD, u16(p + 2))
                out += m
                if (m.type == CONTINUATION) blocks += at(u64(m.at)) to at(u64(m.at + 8))
                p += MESSAGE_HEAD + m.size
            }
        }
        return out
    }

    /** The members of the group at [header]: each name with its object header. */
    fun children(header: Long): Map<String, Long> {
        val table = messages(header).firstOrNull { it.type == SYMBOL_TABLE } ?: return emptyMap()
        val heap = at(u64(table.at + 8), HEAP_SIZE)
        require(tag(heap, "HEAP"))
        val out = LinkedHashMap<String, Long>()
        collect(u64(table.at), u64(heap + 24), out, 0)
        return out
    }

    /** The names under the tree node at [address], read from the heap's data at [names]. */
    private fun collect(address: Long, names: Long, out: MutableMap<String, Long>, depth: Int) {
        val n = at(address, NODE_HEAD)
        require(tag(n, "TREE") && u8(n + 4) == GROUP_NODE && depth < MAX_DEPTH)
        val level = u8(n + 5)
        for (i in 0 until u16(n + 6)) {
            val child = u64(n + NODE_HEAD + 8 + i * 16)
            if (level > 0) {
                collect(child, names, out, depth + 1)
                continue
            }
            val table = at(child, 8)
            require(tag(table, "SNOD"))
            for (k in 0 until u16(table + 6)) {
                val entry = table + 8 + k * SYMBOL_SIZE
                out[text(at(names + u64(entry)))] = u64(entry + 8)
            }
        }
    }

    /** The object header of the member at [path] ("dataset1/data1/data"), from the root. */
    fun path(path: String): Long = path.split('/').fold(root) { header, name -> children(header).getValue(name) }

    /** The attributes of the object at [header] that are one number of 64 bits with a fraction. */
    fun doubles(header: Long): Map<String, Double> = messages(header).filter { it.type == ATTRIBUTE && u8(it.at) == 1 }.mapNotNull { m ->
        // the name, the type and the dimensions, each filled up to 8 bytes; then the value
        val name = m.at + 8
        val type = name + padded(u16(m.at + 2))
        val value = type + padded(u16(m.at + 4)) + padded(u16(m.at + 6))
        if (u8(type) and 0x0F != FLOAT || u32(type + 4) != 8) null else text(name) to Double.fromBits(u64(value))
    }.toMap()

    private fun padded(size: Int) = (size + 7) / 8 * 8

    companion object {
        const val MAX_DEPTH = 8
        const val DATASPACE = 0x01
        const val DATATYPE = 0x03
        const val LAYOUT = 0x08
        const val FILTERS = 0x0B
        const val ATTRIBUTE = 0x0C
        const val CONTINUATION = 0x10
        const val SYMBOL_TABLE = 0x11
        /** The head of a tree node: its tag, kind, level, number of entries and both neighbours. */
        const val NODE_HEAD = 24
        const val FLOAT = 1
        private const val SUPERBLOCK_SIZE = 96
        private const val ROOT_HEADER = 64
        private const val HEADER_SIZE = 16
        private const val MESSAGE_HEAD = 8
        private const val HEAP_SIZE = 32
        private const val SYMBOL_SIZE = 40
        private const val GROUP_NODE = 0
    }
}

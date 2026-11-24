/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/Tar.kt
 * The files in a tar archive: their names and where their bytes lie.
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
 * A tar archive is a row of files, each behind a head of [BLOCK] bytes that names it and gives
 * its size (octal digits); a file fills its last block up with zeros. The head of the first file
 * alone tells where that file ends - enough to fetch just the beginning of an archive.
 */
internal object Tar {
    const val BLOCK = 512
    private const val NAME_LENGTH = 100
    private const val SIZE_AT = 124
    private const val SIZE_LENGTH = 12
    /** No archive read here holds more files. */
    private const val MAX_MEMBERS = 256

    /** A file of the archive: its [name], where its bytes begin ([offset]) and how many they are. */
    class Member(val name: String, val offset: Int, val size: Int) {
        /** The archive's bytes up to the end of this file. */
        val end: Int get() = offset + size
    }

    /** The file whose head stands at [at] in [archive]; null at the archive's end or without a whole head. */
    fun member(archive: ByteArray, at: Int = 0): Member? {
        if (at < 0 || at + BLOCK > archive.size || archive[at].toInt() == 0) return null
        val name = text(archive, at, NAME_LENGTH)
        val size = text(archive, at + SIZE_AT, SIZE_LENGTH).trim().toIntOrNull(8) ?: return null
        return Member(name, at + BLOCK, size).takeIf { size >= 0 }
    }

    /** Every file of [archive] that lies in it whole, in its order. */
    fun members(archive: ByteArray): List<Member> {
        val out = ArrayList<Member>()
        var at = 0
        while (out.size < MAX_MEMBERS) {
            val m = member(archive, at)?.takeIf { it.end <= archive.size } ?: break
            out += m
            at = m.offset + (m.size + BLOCK - 1) / BLOCK * BLOCK
        }
        return out
    }

    /** The bytes of [m] out of [archive]. */
    fun bytes(archive: ByteArray, m: Member): ByteArray = archive.copyOfRange(m.offset, m.end)

    private fun text(b: ByteArray, at: Int, length: Int): String {
        var end = at
        while (end < at + length && b[end].toInt() != 0) end++
        return String(b, at, end - at, Charsets.US_ASCII)
    }
}

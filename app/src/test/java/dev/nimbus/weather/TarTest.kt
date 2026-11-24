/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/TarTest.kt
 * A tar archive read: its files by name and place, an archive cut short.
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


package dev.nimbus.weather

import dev.nimbus.weather.ui.radar.Tar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/** An archive of [files] (name, bytes) as tar writes it: a head of 512 bytes each, the bytes filled up to whole blocks. */
internal fun tarOf(files: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    for ((name, bytes) in files) {
        val head = ByteArray(Tar.BLOCK)
        name.toByteArray(Charsets.US_ASCII).copyInto(head)
        "%011o".format(bytes.size).toByteArray(Charsets.US_ASCII).copyInto(head, 124)
        out.write(head)
        out.write(bytes)
        out.write(ByteArray((Tar.BLOCK - bytes.size % Tar.BLOCK) % Tar.BLOCK))
    }
    out.write(ByteArray(2 * Tar.BLOCK))
    return out.toByteArray()
}

class TarTest {
    private val first = ByteArray(700) { (it % 251).toByte() }
    private val second = ByteArray(512) { 7 }
    private val archive = tarOf(listOf("first.bin" to first, "second.bin" to second))

    @Test fun everyFileByNameAndPlace() {
        val members = Tar.members(archive)
        assertEquals(listOf("first.bin", "second.bin"), members.map { it.name })
        assertArrayEquals(first, Tar.bytes(archive, members[0]))
        assertArrayEquals(second, Tar.bytes(archive, members[1]))
        // the second head stands behind the first file, filled up to whole blocks
        assertEquals(Tar.BLOCK + 1024 + Tar.BLOCK, members[1].offset)
    }

    @Test fun theHeadAloneTellsWhereTheFirstFileEnds() {
        val m = Tar.member(archive.copyOf(Tar.BLOCK))!!
        assertEquals("first.bin", m.name)
        assertEquals(Tar.BLOCK + 700, m.end)
    }

    @Test fun anArchiveCutShortHoldsOnlyItsWholeFiles() {
        assertEquals(listOf("first.bin"), Tar.members(archive.copyOf(Tar.BLOCK + 1024 + Tar.BLOCK + 100)).map { it.name })
        assertEquals(0, Tar.members(archive.copyOf(Tar.BLOCK + 300)).size)
        assertNull(Tar.member(archive.copyOf(100)))
        assertNull(Tar.member(ByteArray(Tar.BLOCK)))
    }
}

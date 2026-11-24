/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/MaskFile.kt
 * A mask of yes and no per cell as bytes: one bit each.
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

import java.io.File

/** A mask packed eight cells to a byte, the first cell in the lowest bit of the first byte. */
internal object MaskFile {
    fun pack(mask: BooleanArray): ByteArray {
        val bytes = ByteArray((mask.size + 7) / 8)
        for (i in mask.indices) if (mask[i]) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
        return bytes
    }

    /** The mask of [cells] cells in [bytes]; null if they are not as many. */
    fun unpack(bytes: ByteArray, cells: Int): BooleanArray? {
        if (bytes.size != (cells + 7) / 8) return null
        return BooleanArray(cells) { (bytes[it / 8].toInt() shr (it % 8)) and 1 == 1 }
    }

    /** The mask of [cells] cells kept in [file]; null without one. */
    fun read(file: File?, cells: Int): BooleanArray? =
        runCatching { file?.takeIf { it.exists() }?.readBytes()?.let { unpack(it, cells) } }.getOrNull()

    /** [mask] into [file], stamped with [at] where given. */
    fun write(file: File?, mask: BooleanArray, at: Long? = null) {
        file ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(pack(mask))
            if (at != null) file.setLastModified(at)
        }
    }
}

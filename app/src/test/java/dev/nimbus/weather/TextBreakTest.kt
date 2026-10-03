/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/TextBreakTest.kt
 * Numbers stay with their units: no line break between "35" and "%" ("(35" | "%)") or inside
 * "km/h" ("km/" | "h") – seen on narrow phones and with a large system font.
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

package dev.nimbus.weather

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextBreakTest {
    private val files = listOf("values", "values-de").map { File("src/main/res/$it/strings.xml") }

    private fun strings(f: File): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(f.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** A number (or a number placeholder) and its "%" with a plain, breakable space between them. */
    @Test fun percentStaysWithItsNumber() {
        val bad = Regex("""(\d|%\d\${'$'}d|%d) %(?!\w)""")
        val failures = files.flatMap { f ->
            strings(f).filter { (_, v) -> bad.containsMatchIn(v.replace("%%", "%")) }
                // the explanations are running text; a break there is fine
                .filterKeys { !it.startsWith("term_") }
                .map { (k, v) -> "${f.parentFile.name}/$k: \"$v\"" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}

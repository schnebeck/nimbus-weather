/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CodeCommentsTest.kt
 * Comments in the code say why a solution is built so – never how it came about.
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

/**
 * Comments explain the code as it stands: why a solution is built so. How it came about belongs to
 * commits, changelogs and the requirements – quoted requests, version numbers and accounts of
 * earlier behaviour in a comment age with the next change and mislead.
 */
class CodeCommentsTest {
    private val sources = listOf(File("src/main/java"), File("src/test/java"), File("../tools"))

    /** Comment lines of Kotlin (//, /* */, KDoc) and Python (#) files. */
    private fun comments(): Sequence<Pair<String, String>> = sources.asSequence().flatMap { root ->
        root.walk().filter { it.isFile && (it.extension == "kt" || it.extension == "py") }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val s = line.trim()
                val comment = s.startsWith("//") || s.startsWith("*") || s.startsWith("/*") || (f.extension == "py" && s.startsWith("#"))
                if (comment) "${f.path}:${i + 1}" to s else null
            }
        }
    }

    @Test fun noHistoryInComments() {
        val quote = "„"                                     // the German opening quotation mark
        val version = Regex("""\b1\.\d{2}\.\d{1,2}\b""")         // the app's versions
        val story = Regex("""\b(used to|earlier versions?|an earlier version|found on the emulator)\b""", RegexOption.IGNORE_CASE)
        // this file names the patterns it looks for
        val found = comments().filter { (where, text) ->
            "CodeCommentsTest.kt:" !in where && (quote in text || version.containsMatchIn(text) || story.containsMatchIn(text))
        }.map { (where, text) -> "$where: $text" }.toList()
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RequirementsTest.kt
 * docs/REQUIREMENTS.md (kept locally) holds every requirement in its words with the tests that
 * cover it: each one names tests that exist – or stands there as open, with its reason.
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

class RequirementsTest {
    private val tests = File("src/test/java/dev/nimbus/weather")

    /** The requirements of docs/REQUIREMENTS.md: heading to its text. */
    private fun requirements(): Map<String, String> {
        val file = File("../docs/REQUIREMENTS.md")
        // kept locally (not in the repository): a checkout without it has nothing to check
        org.junit.Assume.assumeTrue("docs/REQUIREMENTS.md not here", file.exists())
        val text = file.readText()
        return Regex("""(?m)^### (.+)$""").findAll(text).associate { m ->
            val end = Regex("""(?m)^#{2,3} """).find(text, m.range.last + 1)?.range?.first ?: text.length
            m.groupValues[1].trim() to text.substring(m.range.last + 1, end)
        }
    }

    @Test fun everyRequirementInItsWordsWithItsTests() {
        val reqs = requirements()
        assertTrue("no requirements found", reqs.size >= 10)
        val problems = mutableListOf<String>()
        for ((name, body) in reqs) {
            if (!Regex("""(?m)^> „""").containsMatchIn(body)) problems += "$name: not in its words (no quote)"
            val named = Regex("""(?s)Tests:(.*)""").find(body)?.groupValues?.get(1)
            when {
                named == null -> problems += "$name: no tests named"
                named.trim().startsWith("offen") -> if (!named.contains("–")) problems += "$name: open without a reason"
                else -> {
                    val refs = Regex("""`([A-Za-z]+)#([^`]+)`""").findAll(named).map { it.groupValues[1] to it.groupValues[2] }.toList()
                    if (refs.isEmpty()) problems += "$name: no tests named"
                    for ((cls, method) in refs) {
                        val file = File(tests, "$cls.kt")
                        val src = if (file.exists()) file.readText() else ""
                        // a test: "@Test" before "fun" (on its line or the one above)
                        val found = Regex("""@Test[^\n]*\n?[^\n]*fun\s+(`${Regex.escape(method)}`|${Regex.escape(method)})\s*\(""").containsMatchIn(src)
                        if (!found) problems += "$name: test $cls#$method does not exist"
                    }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

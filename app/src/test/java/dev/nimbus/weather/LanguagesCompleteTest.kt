/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/LanguagesCompleteTest.kt
 * Every language the app offers has every text, with the same placeholders as the English one.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanguagesCompleteTest {
    private val res = File("src/main/res")

    /** The languages with texts of their own: the values-xx folders holding a strings.xml. */
    private val languages = res.listFiles()!!.map { it.name }
        .filter { Regex("""values-[a-z]{2}""").matches(it) && File(res, "$it/strings.xml").exists() }.sorted()

    /** Each text by its name – the items of plurals and arrays joined – without the untranslatable ones. */
    private fun texts(folder: String): Map<String, String> {
        val xml = File(res, "$folder/strings.xml").readText()
        return Regex("""<(string|plurals|string-array) name="([^"]+)"([^>]*)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .filter { !it.groupValues[3].contains("translatable=\"false\"") }
            .associate { it.groupValues[2] to it.groupValues[4] }
    }

    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+\$)?[sdf]""").findAll(text).map { it.value }.distinct().sorted().toList()

    @Test fun italianIsOneOfTheLanguages() {
        assertTrue(languages.toString(), "values-de" in languages && "values-it" in languages)
    }

    @Test fun everyLanguageHasEveryText() {
        val english = texts("values")
        for (lang in languages) {
            // the app's name stays the same in every language
            val missing = english.keys - texts(lang).keys - "app_name"
            assertTrue("$lang lacks: $missing", missing.isEmpty())
            val extra = texts(lang).keys - english.keys
            assertTrue("$lang has no English text for: $extra", extra.isEmpty())
        }
    }

    @Test fun everyTextHasTheEnglishPlaceholders() {
        val english = texts("values")
        val failures = languages.flatMap { lang ->
            texts(lang).filter { (k, v) -> placeholders(v) != placeholders(english.getValue(k)) }.map { (k, v) -> "$lang/$k: $v" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The system's per-app language choice (Android 13 and up) lists each of them. */
    @Test fun theLocaleConfigListsEveryLanguage() {
        val config = File(res, "xml/locales_config.xml").readText()
        val listed = Regex("""android:name="([a-z]{2})"""").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("en") + languages.map { it.removePrefix("values-") }, listed)
    }
}

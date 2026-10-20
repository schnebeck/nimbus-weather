/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PrivacyPolicyTest.kt
 * The privacy policy names every service the app contacts, in German and English, and the app
 * links to it in its own language.
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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.nimbus.weather.data.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w411dp-h4000dp", application = android.app.Application::class)
class PrivacyPolicyTest {
    @get:Rule val compose = createComposeRule()

    /** How the policy names the operator of each host the app's code contacts. */
    private val named = mapOf(
        "open-meteo.com" to "Open-Meteo", "brightsky.dev" to "Bright Sky", "dwd.de" to "DWD",
        "rainviewer.com" to "RainViewer", "knmi.nl" to "KNMI", "met.no" to "MET Norway", "eumetsat.int" to "EUMETSAT",
        "openfreemap.org" to "OpenFreeMap", "openstreetmap.org" to "Nominatim", "pegelonline.wsv.de" to "PEGELONLINE",
        "hochwasserportal.nrw" to "Hochwasserportal NRW", "hlnug.de" to "HLNUG", "eea.europa.eu" to "EEA",
        "sachsen.de" to "Sachsen|Saxony|Saxon", "schleswig-holstein.de" to "Schleswig-Holstein", "lageso.de" to "LAGeSo",
        "geosphere.at" to "GeoSphere", "sensor.community" to "Sensor.Community", "geo.admin.ch" to "MeteoSwiss",
        "dmi.dk" to "DMI", "aviationweather.gov" to "aviationweather.gov", "azure-api.net" to "NLWKN",
        "hochwasserzentralen.de" to "Hochwasserportal",
        // links opened in the browser, no data from the app
        "github.com" to "",
    )

    private fun hosts(): Set<String> = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
        .flatMap { f -> Regex("""https?://([a-zA-Z0-9.-]+)""").findAll(f.readText()).map { it.groupValues[1].lowercase() } }.toSet()

    /** A new service in the code fails here until the policy names it – in both languages. */
    @Test fun everyServiceIsNamed() {
        val de = File("../docs/PRIVACY.md").readText()
        val en = File("../docs/PRIVACY.en.md").readText()
        val found = hosts()
        assertTrue("hosts found: $found", found.size >= 20)
        for (h in found) {
            val name = named.entries.firstOrNull { (domain, _) -> h == domain || h.endsWith(".$domain") }?.value
            assertTrue("$h: not in the list – add it to the privacy policy (de, en) and here", name != null)
            if (name!!.isEmpty()) continue
            for ((lang, text) in listOf("de" to de, "en" to en)) {
                assertTrue("$h ($name) not named in the policy ($lang)", Regex(name).containsMatchIn(text))
            }
        }
    }

    /** Settings → About: the policy opens on the project's page, in the app's language. */
    @Test fun theAppLinksToIt() {
        var opened: String? = null
        val handler = object : UriHandler { override fun openUri(uri: String) { opened = uri } }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                dev.nimbus.weather.ui.settings.SettingsScreen(Settings(), {}, {}, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Datenschutzerklärung"))
        compose.onNodeWithText("Datenschutzerklärung").performClick()
        assertEquals("https://schnebeck.github.io/nimbus-weather/PRIVACY.html", opened)
        // the English page beside the German one, as all documentation
        assertTrue(File("../docs/PRIVACY.en.md").exists() && File("../docs/index.en.md").exists())
    }
}

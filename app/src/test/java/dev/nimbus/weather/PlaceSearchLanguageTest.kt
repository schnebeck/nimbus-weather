/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PlaceSearchLanguageTest.kt
 * The place search asks in the app's language: German, Italian – any other in English.
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

import dev.nimbus.weather.data.remote.OpenMeteoSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchLanguageTest {
    @Test fun theAppsLanguagesAndEnglishForTheRest() {
        assertEquals("it", OpenMeteoSource.searchLanguage("it"))
        assertEquals("de", OpenMeteoSource.searchLanguage("de"))
        assertEquals("en", OpenMeteoSource.searchLanguage("en"))
        // French: the app shows English, the places come in English too
        assertEquals("en", OpenMeteoSource.searchLanguage("fr"))
    }

    @Test fun theSearchAsksInItalian() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().code(200).body(Fixtures.text("geocoding.json")).build())
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            OpenMeteoSource(OkHttpClient(), geocodingUrl = base).searchPlaces("Monaco", OpenMeteoSource.searchLanguage("it"))
            assertEquals("it", server.takeRequest().url.queryParameter("language"))
        } finally { server.close() }
    }
}

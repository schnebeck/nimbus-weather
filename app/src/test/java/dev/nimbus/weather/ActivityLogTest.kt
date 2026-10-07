/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ActivityLogTest.kt
 * The activity log: what it notes of a call, and what it leaves out.
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

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.nimbus.weather.ui.settings.ActivityLogSection
import dev.nimbus.weather.util.ActivityLog
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * „sollte man ein schlankes Debugging einbauen, was die tatsächlichen Tätigkeiten loggt?“ – „ok,
 * als 1.37.1 umsetzen mit schalter zum aktivien des Debuggings und bauen“: one line per network
 * call (without coordinates), per app shown or hidden and screen on or off – only while switched on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ActivityLogTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun lines(): List<String> {
        ActivityLog.flush()
        return File(ActivityLog.dir!!, "activity.log").takeIf { it.exists() }?.readLines().orEmpty()
    }

    private fun call(server: MockWebServer, client: OkHttpClient, path: String) =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.body.string() }

    @After fun tearDown() { ActivityLog.setEnabled(false); ActivityLog.clear() }

    @Test fun aCallWithoutItsCoordinates() {
        ActivityLog.dir = tmp.newFolder("log")
        ActivityLog.setEnabled(true)
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(200).body("x".repeat(5000)).build())
            server.start()
            call(server, OkHttpClient.Builder().eventListenerFactory(ActivityLog.events).build(), "/v1/forecast?latitude=52.3759&longitude=9.732")
        }
        val line = lines().single { "/v1/forecast" in it }
        assertTrue(line, " 200 " in line && "↓4.9kB" in line)
        assertFalse(line, "52.37" in line || "latitude" in line)
    }

    /** Map tiles: the numbers of their path say where one looks – masked. */
    @Test fun tileNumbersMasked() {
        assertEquals("tiles.openfreemap.org/planet/20251001_001001_pt/#/#/#",
            ActivityLog.where("https://tiles.openfreemap.org/planet/20251001_001001_pt/12/2150/1350.pbf".toHttpUrl()))
    }

    @Test fun switchedOffNothing() {
        ActivityLog.dir = tmp.newFolder("log")
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(200).body("x").build())
            server.start()
            call(server, OkHttpClient.Builder().eventListenerFactory(ActivityLog.events).build(), "/weather")
        }
        assertEquals(emptyList<String>(), lines())
    }

    /** Answered from the cache, nothing went over the air: no line. */
    @Test fun fromTheCacheNoLine() {
        ActivityLog.dir = tmp.newFolder("log")
        ActivityLog.setEnabled(true)
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(200).setHeader("Cache-Control", "max-age=600").body("x").build())
            server.start()
            val client = OkHttpClient.Builder().cache(Cache(tmp.newFolder("http"), 1_000_000)).eventListenerFactory(ActivityLog.events).build()
            call(server, client, "/radar")
            call(server, client, "/radar")
        }
        assertEquals(1, lines().count { "/radar" in it })
    }

    /** The switch in the settings: off by default, kept across starts, and the log can be shared then. */
    @Test fun theSwitchInTheSettings() {
        val app = RuntimeEnvironment.getApplication()
        ActivityLog.init(app)
        ActivityLog.dir = tmp.newFolder("log")
        assertFalse(ActivityLog.enabled)
        compose.setContent { ActivityLogSection(SnackbarHostState()) }
        val share = app.getString(R.string.activity_log_share)
        compose.onNodeWithText(share).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.activity_log)).performClick()
        assertTrue(ActivityLog.enabled)
        compose.onNodeWithText(share).assertIsDisplayed()
        assertTrue(app.getSharedPreferences("diagnostics", 0).getBoolean("activityLog", false))
        assertTrue(lines().any { it.endsWith("log on") })
    }
}

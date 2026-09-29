/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RetryInterceptorTest.kt
 * Tests for retrying failed tile requests.
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

import dev.nimbus.weather.ui.radar.RetryInterceptor
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun client(hosts: Set<String>) = OkHttpClient.Builder().addInterceptor(RetryInterceptor(hosts)).build()

    @Test
    fun `retries once on server error`() {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        val resp = client(setOf(server.hostName)).newCall(Request.Builder().url(server.url("/tile")).build()).execute()
        assertEquals(200, resp.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not retry other hosts or client errors`() {
        server.enqueue(MockResponse.Builder().code(503).build())
        val other = client(setOf("maps.dwd.de")).newCall(Request.Builder().url(server.url("/a")).build()).execute()
        assertEquals(503, other.code)
        server.enqueue(MockResponse.Builder().code(404).build())
        val notFound = client(setOf(server.hostName)).newCall(Request.Builder().url(server.url("/b")).build()).execute()
        assertEquals(404, notFound.code)
        assertEquals(2, server.requestCount)
    }
}

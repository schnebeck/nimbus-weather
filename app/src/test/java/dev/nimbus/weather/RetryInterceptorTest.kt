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

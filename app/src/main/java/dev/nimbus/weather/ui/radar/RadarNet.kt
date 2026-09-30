/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarNet.kt
 * Robust map and radar loading: stored copies when the network fails, and a status for the UI.
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

package dev.nimbus.weather.ui.radar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** How the map and radar requests went since the radar screen last (re)loaded. */
object RadarNetStatus {
    data class State(val failed: Int = 0, val fromCache: Int = 0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun reset() { _state.value = State() }
    internal fun failed() = _state.update { it.copy(failed = it.failed + 1) }
    internal fun fromCache() = _state.update { it.copy(fromCache = it.fromCache + 1) }
}

/**
 * When a map or radar request fails (no connection, timeout, server error), answer it with the
 * copy in the HTTP cache – even if that copy is expired. MapLibre shows an older tile instead of
 * a hole, and the base map works offline as long as it was loaded once.
 */
class StaleFallbackInterceptor(private val hosts: Set<String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host !in hosts) return chain.proceed(request)
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            RadarNetStatus.failed()
            return fromCache(chain) ?: throw e
        }
        if (response.code < 500) return response
        RadarNetStatus.failed()
        val cached = fromCache(chain) ?: return response
        response.close()
        return cached
    }

    private fun fromCache(chain: Interceptor.Chain): Response? {
        // MapLibre revalidates tiles from its own database with If-None-Match / If-Modified-Since;
        // OkHttp never answers such conditional requests from its cache, so drop the conditions.
        val request = chain.request().newBuilder()
            .removeHeader("If-None-Match").removeHeader("If-Modified-Since")
            .cacheControl(CacheControl.FORCE_CACHE).build()
        val cached = runCatching { chain.proceed(request) }.getOrNull() ?: return null
        // 504 = "only-if-cached" and nothing stored
        if (!cached.isSuccessful) { cached.close(); return null }
        RadarNetStatus.fromCache()
        return cached
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/MainSourceFirstTest.kt
 * The radar waits for the area's own source only; the sources beside it come step by step, and one
 * that does not answer is left out for a while.
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

import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarPlayer
import dev.nimbus.weather.ui.radar.RadarTimeline
import dev.nimbus.weather.ui.radar.SourceHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MainSourceFirstTest {
    private val step = 5 * 60_000L
    private val t0 = 1_791_219_000_000L / step * step - 5 * 24 * 3_600_000L     // steps of their own: no other test has them

    /**
     * Norden, the Dutch border in the picture: the KNMI hangs (seconds per answer), the DWD's
     * steps are there at once (here offline: played empty) – no step waits for the KNMI.
     */
    @Test fun aHangingSourceBesideHoldsNoStep() {
        val hanging = OkHttpClient.Builder().addInterceptor { chain ->
            if ("knmi" in chain.request().url.host) Thread.sleep(4_000)
            throw java.io.IOException("offline in the test")
        }.build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val tl = RadarTimeline((-24..0).map { k -> RadarFrame(t0 + k * step, false, null, null) }, 24, "")
            val p = RadarPlayer(scope, hanging, maxSide = 128)
            p.setTimeline(tl)
            p.setView(53.1, 54.1, 6.2, 8.2, 1080)
            val start = System.currentTimeMillis()
            while (!tl.frames.indices.all { p.isLoaded(it) }) {
                assertTrue(
                    "steps waited for the KNMI: ${tl.frames.indices.count { !p.isLoaded(it) }} not there after ${System.currentTimeMillis() - start} ms",
                    System.currentTimeMillis() - start < 3_500,
                )
                Thread.sleep(50)
            }
        } finally { scope.cancel() }
    }

    /** Two failures in a row: left out for five minutes; an answer counts it anew. */
    @Test fun aFailingSourceRests() {
        var now = 0L
        val h = SourceHealth { now }
        h.failed("knmi")
        assertTrue(h.usable("knmi"))
        h.failed("knmi")
        assertFalse(h.usable("knmi"))
        now += SourceHealth.PAUSE_MS
        assertTrue(h.usable("knmi"))
        h.failed("knmi"); h.answered("knmi"); h.failed("knmi")
        assertTrue("an answer between: counted anew", h.usable("knmi"))
    }
}

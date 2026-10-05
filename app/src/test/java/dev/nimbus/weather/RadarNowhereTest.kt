/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarNowhereTest.kt
 * "Wenn wir in die Vorhersage kommen, gibt es keine Daten … die Seite in der Vorhersage aufwecke,
 * dann hängt nach Laden": steps nobody has anything for in the area play as empty ones.
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RadarNowhereTest {
    private val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the test") }.build()
    private val step = 5 * 60_000L
    private val latest = 1_791_219_000_000L / step * step

    /** Two hours back and the DWD's nowcast ahead, no RainViewer frame (as offline). */
    private fun timeline() = RadarTimeline(
        (-24..24).map { k -> latest + k * step }.map { t -> RadarFrame(t, t > latest, if (t > latest) latest else null, null) }, 24, "",
    )

    private fun player(south: Double, north: Double, west: Double, east: Double, check: (RadarPlayer, RadarTimeline) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = RadarPlayer(scope, offline, maxSide = 128)
            val tl = timeline()
            p.setTimeline(tl)
            p.setView(south, north, west, east, 1080)
            check(p, tl)
        } finally { scope.cancel() }
    }

    /**
     * Alone the steps are there in under a second. In the whole suite other tests' downloads can
     * still hold the store's places (RadarStore is one per process, their network waits long) –
     * the offline steps then wait their turn: up to a minute.
     */
    private fun waitFor(what: String, cond: () -> Boolean) {
        val until = System.currentTimeMillis() + 60_000
        while (!cond()) { assertTrue("waited in vain: $what", System.currentTimeMillis() < until); Thread.sleep(50) }
    }

    /** Around Bergen (beyond the DWD's and the KNMI's grids): every step plays, the future too – and it says why it is empty. */
    @Test fun beyondTheCompositesEveryStepPlays() = player(59.9, 60.9, 4.3, 6.3) { p, tl ->
        waitFor("all steps shown around Bergen") { tl.frames.indices.all { p.isLoaded(it) } }
        assertTrue(p.canShow(30.5f))
        assertEquals(false, p.nowcastHere.value)
    }

    /** Around Hannover the DWD's nowcast reaches in: no such hint (its steps, here offline, play empty too). */
    @Test fun withTheDwdItsNowcast() = player(51.9, 52.9, 8.7, 10.7) { p, tl ->
        waitFor("all steps shown around Hannover") { tl.frames.indices.all { p.isLoaded(it) } }
        assertEquals(true, p.nowcastHere.value)
    }
}

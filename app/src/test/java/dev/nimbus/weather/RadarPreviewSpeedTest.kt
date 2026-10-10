/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarPreviewSpeedTest.kt
 * A fast preview: it fetches only its area's cells, no composite it does
 * not show, RainViewer coarser – and the places' previews one after the other.
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

import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.GridWindow
import dev.nimbus.weather.ui.radar.KnmiRadar
import dev.nimbus.weather.ui.radar.RadarComposite
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarPicture
import dev.nimbus.weather.ui.radar.RadarPreview
import dev.nimbus.weather.ui.radar.RadarSources
import dev.nimbus.weather.ui.radar.RadarStore
import dev.nimbus.weather.ui.radar.window
import dev.nimbus.weather.ui.radar.RadarTimeline
import dev.nimbus.weather.ui.radar.whole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RadarPreviewSpeedTest {
    private val frame = RadarFrame(1_790_900_100_000L, false, null, null)
    private val hannover = RadarPreview.geo(52.3759, 9.732, 380, 220)

    /** "nur den Bildausschnitt abrufen": of the DWD's 1.9 million cells the area's, about 2 %. */
    @Test fun thePreviewAsksOnlyForItsArea() = runBlocking {
        val asked = mutableListOf<Pair<RadarComposite, GridWindow>>()
        RadarPicture.still(OkHttpClient(), RadarTimeline(listOf(frame), 0, ""), frame, hannover) { c, w -> asked += c to w; null }
        assertEquals(listOf<RadarComposite>(DwdRadar), asked.map { it.first })
        val w = asked.single().second
        assertTrue("$w", w.size < DwdRadar.whole.size / 30)
        // it holds the whole area
        val c0 = floor((hannover.west - DwdRadar.LON0) / RadarComposite.STEP).toInt()
        val r0 = floor((DwdRadar.LAT1 - hannover.north) / RadarComposite.STEP).toInt()
        assertTrue("$w", w.col0 <= c0 && w.row0 <= r0)
        assertTrue("$w", w.col0 + w.w > floor((hannover.east - DwdRadar.LON0) / RadarComposite.STEP).toInt())
        assertTrue("$w", w.row0 + w.h > floor((DwdRadar.LAT1 - hannover.south) / RadarComposite.STEP).toInt())
    }

    /**
     * Only where the composites cover the area: the KNMI's rectangle reaches into the area, but
     * where the DWD's radars cover every pixel it shows none – not fetched. Where they leave
     * pixels, it is.
     */
    @Test fun aCompositeShowingNothingIsNotFetched() {
        val n = 100
        val all = BooleanArray(n) { true }
        assertEquals(listOf<RadarComposite>(DwdRadar), RadarPicture.shown(mapOf(KnmiRadar to all, DwdRadar to all), n))
        val west = BooleanArray(n) { it % 10 < 5 }
        assertEquals(listOf(DwdRadar, KnmiRadar), RadarPicture.shown(mapOf(DwdRadar to west, KnmiRadar to all), n))
        // the KNMI marks only its rectangle: the DWD's comes after it in no order, it hides nothing
        assertEquals(listOf<RadarComposite>(KnmiRadar), RadarPicture.shown(mapOf(KnmiRadar to all), n))
    }

    /** RainViewer's tiles in the preview: four at most – at zoom 6, since at 5 its rain shows as blocks. */
    @Test fun rainViewerCoarserForThePreview() {
        assertEquals(7, RadarPicture.rvZoom(hannover))
        assertEquals(6, RadarPicture.rvZoom(hannover, RadarPicture.STILL_RV_ZOOM))
        for ((lat, lon) in listOf(52.3759 to 9.732, 48.857 to 2.352, 47.421 to 10.985, 59.33 to 18.07)) {
            val g = RadarPreview.geo(lat, lon, 380, 220)
            val size = 2 * 20037508.342789244 / (1 shl RadarPicture.STILL_RV_ZOOM)
            val cols = floor((g.maxX + 20037508.342789244) / size) - floor((g.minX + 20037508.342789244) / size) + 1
            val rows = floor((20037508.342789244 - g.minY) / size) - floor((20037508.342789244 - g.maxY) / size) + 1
            assertTrue("$lat, $lon: ${cols * rows} tiles", cols * rows <= 4)
        }
    }

    /** A client counting the composites' requests running at once (each takes 300 ms, then fails). */
    private class Counting {
        val running = AtomicInteger(); val most = AtomicInteger(); val count = AtomicInteger()
        val http: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            if ("request=GetMap" in url || "request=GetCoverage" in url) {
                count.incrementAndGet()
                most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                try { Thread.sleep(300) } finally { running.decrementAndGet() }
            }
            throw java.io.IOException("offline in the test")
        }.build()
    }

    private suspend fun prefetch(http: OkHttpClient, lat: Double, lon: Double) =
        RadarPreview.prefetch(RuntimeEnvironment.getApplication(), http, http, lat, lon, 2f, "de", withBase = false)

    /** "Die Vorabberechnungen nacheinander statt parallel laufen lassen". */
    @Test fun theOtherPlacesOneAfterTheOther() = runBlocking {
        RadarPreview.cardSize = 380 to 220
        val net = Counting()
        listOf(52.3759 to 9.732, 48.137 to 11.575, 50.94 to 6.96).map { (lat, lon) ->
            async(Dispatchers.Default) { prefetch(net.http, lat, lon) }
        }.awaitAll()
        assertTrue("${net.count}", net.count.get() >= 3)
        assertEquals(1, net.most.get())
    }

    /** "Sichtbare Karte zuerst": the card does not queue behind the prefetching – and its picture, asked for twice, is fetched once. */
    @Test fun theCardDoesNotWaitAndSharesThePicture() = runBlocking {
        RadarPreview.cardSize = 380 to 220
        val net = Counting()
        val tl = RadarSources.timeline(net.http, anchor = DwdRadar)
        val now = tl.frames[tl.nowIndex]
        val card = listOf(
            async(Dispatchers.Default) { prefetch(net.http, 48.137, 11.575) },
            async(Dispatchers.Default) { kotlinx.coroutines.delay(50); RadarPreview.fetchOverlay(net.http, tl, now, 52.3759, 9.732, 380, 220) },
            async(Dispatchers.Default) { kotlinx.coroutines.delay(50); RadarPreview.fetchOverlay(net.http, tl, now, 52.3759, 9.732, 380, 220) },
        )
        card.awaitAll()
        // Munich's prefetch and Hannover's card at once; Hannover's picture once
        assertEquals(2, net.count.get())
        assertEquals(2, net.most.get())
    }

    /**
     * "Sichtbare Karte zuerst": while the radar loop's whole grids load (12 steps, 1.5 s each), the
     * preview's area is fetched at once – not after them.
     */
    @Test fun thePreviewDoesNotQueueBehindWholeGrids() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Thread.sleep(if ("width=${DwdRadar.W}" in chain.request().url.toString()) 1500 else 100)
            throw java.io.IOException("offline in the test")
        }.build()
        val bulk = (0 until 12).map { i ->
            async(Dispatchers.Default) { RadarStore.grid(http, DwdRadar, RadarFrame(1_700_000_000_000L + i * 300_000L, false, null, null)) }
        }
        kotlinx.coroutines.delay(300)
        val started = System.nanoTime()
        RadarStore.window(http, DwdRadar, frame, DwdRadar.window(hannover.west, hannover.east, hannover.south, hannover.north)!!)
        val ms = (System.nanoTime() - started) / 1_000_000
        bulk.awaitAll()
        assertTrue("the preview waited $ms ms", ms < 1000)
    }
}

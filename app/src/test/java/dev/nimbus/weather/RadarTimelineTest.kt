/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarTimelineTest.kt
 * The radar's time line follows the composite a place lies in: its newest step, and a nowcast
 * only where that composite has one.
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

import dev.nimbus.weather.ui.radar.HistoryRange
import dev.nimbus.weather.ui.radar.RadarComposite
import dev.nimbus.weather.ui.radar.RadarComposites
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarSources
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTimelineTest {
    private val min = 60_000L
    private val now = 1_790_900_000_000L / (5 * min) * (5 * min) + 2 * min
    private val latest = now - 12 * min
    /** No network: RainViewer's list fails at once. */
    private val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the test") }.build()

    private fun composite(id: String, newest: Long, nowcast: Boolean, south: Double, north: Double) = object : RadarComposite {
        override val id = id
        override val lon0 = 1.4
        override val lat1 = north
        override val w = 100
        override val h = ((north - south) / RadarComposite.STEP).toInt()
        override val hasNowcast = nowcast
        override val exactCoverage = true
        override fun covers(lat: Double, lon: Double) = lat in south..north
        override suspend fun latest(http: OkHttpClient) = newest
        override fun key(frame: RadarFrame) = "${id}_${frame.time}"
        override suspend fun fetch(http: OkHttpClient, frame: RadarFrame): ByteArray? = null
        override fun expired(base: String, modified: Long, now: Long, latestIssue: Long?): Boolean? = null
    }

    /** "Zeitachse … entkoppeln": with a nowcast two hours on, each such step from the newest analysis. */
    @Test fun aCompositeWithANowcast() = runBlocking {
        val tl = RadarSources.timeline(offline, HistoryRange.H2, now, force = true, anchor = composite("withNowcast", latest, true, 47.0, 55.0))
        assertEquals(latest, tl.frames[tl.nowIndex].time)
        assertEquals(latest + 120 * min, tl.frames.last().time)
        assertTrue(tl.frames.filter { it.isForecast }.all { it.issue == latest })
        assertTrue(tl.frames.filter { !it.isForecast }.all { it.issue == null })
    }

    /** Past steps only: the loop ends at its newest step. */
    @Test fun aCompositeWithPastStepsOnly() = runBlocking {
        val tl = RadarSources.timeline(offline, HistoryRange.H2, now, force = true, anchor = composite("pastOnly", latest, false, 47.0, 55.0))
        assertEquals(latest, tl.frames.last().time)
        assertTrue(tl.frames.none { it.isForecast })
        assertEquals(tl.frames.lastIndex, tl.nowIndex)
    }

    /** A place follows the first composite covering it; beyond them all, the first. */
    @Test fun aPlaceFollowsItsComposite() {
        val north = composite("north", latest, false, 52.0, 56.0)
        val south = composite("south", latest, true, 47.0, 52.0)
        val among = listOf(south, north)
        assertEquals("north", RadarComposites.anchorFor(53.0, 6.0, among).id)
        assertEquals("south", RadarComposites.anchorFor(50.0, 6.0, among).id)
        assertEquals("south", RadarComposites.anchorFor(40.0, 6.0, among).id)
    }
}

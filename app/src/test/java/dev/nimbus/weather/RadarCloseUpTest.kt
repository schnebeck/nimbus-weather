/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarCloseUpTest.kt
 * The radar close in: opened at about 13 km, played slower the closer the map, fast showers still moving.
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

import dev.nimbus.weather.ui.radar.FieldGeo
import dev.nimbus.weather.ui.radar.HistoryRange
import dev.nimbus.weather.ui.radar.Playback
import dev.nimbus.weather.ui.radar.RADAR_MAX_ZOOM
import dev.nimbus.weather.ui.radar.RADAR_ZOOM
import dev.nimbus.weather.ui.radar.RadarMotion
import dev.nimbus.weather.ui.radar.RadarPreview
import dev.nimbus.weather.ui.radar.ViewFrame
import dev.nimbus.weather.ui.radar.radarStartZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sinh

class RadarCloseUpTest {
    private val lat = 52.07
    private val lon = 9.98

    /** Real kilometres across a phone (411 dp) at [zoom]. */
    private fun km(zoom: Double): Double {
        val g = RadarPreview.geo(lat, lon, 411, 890, zoom)
        return (g.maxX - g.minX) / 1000 * cos(Math.toRadians(lat))
    }

    /** The live radar and its preview open at the same zoom, some 10–15 km across; the zoom left is kept for both. */
    @Test fun bothOpenAtAboutThirteenKilometres() {
        assertEquals(RADAR_ZOOM, RadarPreview.ZOOM, 0.0)
        assertEquals(RADAR_ZOOM, radarStartZoom(hasPlace = true, archive = false, remembered = null), 0.0)
        assertTrue("${km(RADAR_ZOOM)} km", km(RADAR_ZOOM) in 10.0..15.0)
        assertEquals(9.3, radarStartZoom(true, false, 9.3), 0.0)
    }

    /**
     * Closer in, slower: as set at the wide view, three times slower from the opening zoom on –
     * no more, the loop of two hours back and two ahead takes about half a minute there.
     */
    @Test fun slowerTheCloser() {
        assertEquals(1f, Playback.slowdown(5.0), 0f)
        assertEquals(1f, Playback.slowdown(6.6), 0f)
        assertEquals(3f, Playback.slowdown(RADAR_ZOOM), 0.001f)
        assertEquals(3f, Playback.slowdown(RADAR_MAX_ZOOM), 0f)
        val zooms = (66..105).map { it / 10.0 }
        assertTrue(zooms.zipWithNext().all { (a, b) -> Playback.slowdown(b) >= Playback.slowdown(a) })
        // 2 h back and 2 ahead in 5-minute steps, a beat (450 ms) showing 10 minutes
        val stepMs = 450f * HistoryRange.STEP_MINUTES / HistoryRange.H2.playMinutes
        val loopS = 4 * 12 * stepMs * Playback.slowdown(RADAR_ZOOM) / 1000
        assertTrue("$loopS s", loopS in 25f..40f)
    }

    /**
     * At the closest zoom a shower of 100 km/h moves 8 km in five minutes – more than the map is
     * wide: the motion is found all the same (it moves, it does not fade out and in elsewhere).
     */
    @Test fun aFastShowerMovesAtTheClosestZoom() {
        val view = RadarPreview.geo(lat, lon, 411, 890, RADAR_MAX_ZOOM)
        fun latOf(y: Double) = Math.toDegrees(atan(sinh(y / FieldGeo.R)))
        fun lonOf(x: Double) = Math.toDegrees(x / FieldGeo.R)
        val g = FieldGeo.forView(latOf(view.minY), latOf(view.maxY), lonOf(view.minX), lonOf(view.maxX))
        val w = g.w; val h = g.h
        val shiftPx = (100.0 * 5 / 60 / g.pxKm).toFloat()
        val radius = (2.0 / g.pxKm).toFloat()
        fun blob(cx: Float, cy: Float): ViewFrame {
            val d = ByteArray(w * h); val wet = ByteArray(w * h)
            for (y in 0 until h) for (x in 0 until w) if (hypot(x - cx, y - cy) <= radius) {
                d[y * w + x] = 60.toByte(); wet[y * w + x] = 255.toByte()
            }
            return ViewFrame(d, wet, null)
        }
        val x0 = w / 2f; val y0 = h * 0.25f
        assertTrue("shift $shiftPx px of a field $w × $h", y0 + shiftPx + radius < h)
        // as the player asks: up to 150 km/h over the five minutes
        val flow = RadarMotion.motion(blob(x0, y0), blob(x0, y0 + shiftPx), w, h, (150 * (5 / 60.0) / g.pxKm).toFloat())
        val v = FloatArray(2).also { flow.at(x0, y0, it) }
        assertEquals(0f, v[0], shiftPx * 0.15f)
        assertEquals(shiftPx, v[1], shiftPx * 0.15f)
    }
}

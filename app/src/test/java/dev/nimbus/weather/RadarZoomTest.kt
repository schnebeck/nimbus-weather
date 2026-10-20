/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarZoomTest.kt
 * The radar's zoom: closer (12), kept as the live radar was left, the preview at the same scale.
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

import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.radar.RADAR_MAX_ZOOM
import dev.nimbus.weather.ui.radar.RADAR_ZOOM
import dev.nimbus.weather.ui.radar.RadarPreview
import dev.nimbus.weather.ui.radar.radarStartZoom
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarZoomTest {
    @After fun reset() { RadarPreview.remembered = null }

    /** Zoom 12: twice as close as 11, four times as 10 – about 5 km across a phone in Germany. */
    @Test fun closerThanBefore() {
        assertEquals(12.0, RADAR_MAX_ZOOM, 0.0)
        val g = RadarPreview.geo(52.15, 9.95, 411, 220, RADAR_MAX_ZOOM)
        val km = (g.maxX - g.minX) / 1000 * kotlin.math.cos(Math.toRadians(52.15))
        assertEquals(5.0, km, 0.3)
    }

    /** Opened again at the zoom it was left at, within the map's limits; a past day and the first time at the preset. */
    @Test fun opensAtTheZoomItWasLeftAt() {
        assertEquals(RADAR_ZOOM, radarStartZoom(hasPlace = true, archive = false, remembered = null), 0.0)
        assertEquals(9.3, radarStartZoom(true, false, 9.3), 0.0)
        assertEquals(RADAR_MAX_ZOOM, radarStartZoom(true, false, 14.0), 0.0)
        assertEquals(RADAR_ZOOM, radarStartZoom(true, archive = true, remembered = 9.3), 0.0)
        // kept in the settings
        assertNull(Settings().radarZoom)
        val kept = JsonCodec.decodeFromString(Settings.serializer(), JsonCodec.encodeToString(Settings.serializer(), Settings(radarZoom = 9.3)))
        assertEquals(9.3, kept.radarZoom!!, 0.0)
    }

    /** The preview at the same scale: one zoom step closer, half the width; its pictures named apart. */
    @Test fun thePreviewAtTheSameScale() {
        val preset = RadarPreview.geo(52.15, 9.95, 380, 220)
        RadarPreview.remembered = RadarPreview.ZOOM + 1
        val closer = RadarPreview.geo(52.15, 9.95, 380, 220)
        assertEquals((preset.maxX - preset.minX) / 2, closer.maxX - closer.minX, 1.0)
        // the preset keeps its pictures' names; another zoom has its own
        assertEquals("52.150_9.950_380x220", RadarPreview.key(52.15, 9.95, 380, 220, RadarPreview.ZOOM))
        assertEquals("52.150_9.950_380x220_z7.40", RadarPreview.key(52.15, 9.95, 380, 220))
        assertTrue(RadarPreview.key(52.15, 9.95, 380, 220, 9.0) != RadarPreview.key(52.15, 9.95, 380, 220, 9.5))
    }
}

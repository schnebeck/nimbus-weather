/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SatelliteTest.kt
 * The satellite picture at the radar's time (EUMETSAT archive): which picture for which radar
 * step, the request for the view – and the layers offered for a past day too.
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
import dev.nimbus.weather.ui.radar.SatelliteLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class SatelliteTest {
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
    private val now = t("2026-10-03T09:00:00Z")

    @Test fun thePictureOfTheRadarsTime() {
        // standing: the 10-minute step, playing: the hour
        assertEquals(t("2026-10-02T14:30:00Z"), SatelliteLayer.timeFor(t("2026-10-02T14:35:00Z"), now, playing = false))
        assertEquals(t("2026-10-02T14:00:00Z"), SatelliteLayer.timeFor(t("2026-10-02T14:35:00Z"), now, playing = true))
        // the radar's forecast steps lie ahead: the newest picture there is (about 20 minutes old)
        assertEquals(t("2026-10-03T08:40:00Z"), SatelliteLayer.timeFor(t("2026-10-03T10:15:00Z"), now, playing = false))
        assertTrue(SatelliteLayer.timeFor(now, now, playing = false) <= now - SatelliteLayer.LATENCY_MS)
    }

    @Test fun theRequestCoversTheView() {
        val g = FieldGeo.forView(51.6, 53.4, 8.9, 10.5, maxSide = 1024, minPxKm = 0.0)
        val url = SatelliteLayer.url(g, t("2026-10-01T23:00:00Z"))
        assertTrue(url, url.startsWith("https://view.eumetsat.int/geoserver/wms?"))
        assertTrue(url, "layers=mtg_fd:rgb_geocolour" in url && "crs=EPSG:3857" in url && "time=2026-10-01T23:00:00.000Z" in url)
        // the picture in the view's proportions, its longer side 1024 px
        val w = Regex("width=(\\d+)").find(url)!!.groupValues[1].toInt()
        val h = Regex("height=(\\d+)").find(url)!!.groupValues[1].toInt()
        assertEquals(1024, maxOf(w, h))
        assertEquals((g.maxX - g.minX) / (g.maxY - g.minY), w.toDouble() / h, 0.01)
    }

    /** A past day of the look-back offers temperature, wind and satellite (it had no layers at all); the warnings stay live only. */
    @Test fun aPastDayHasTheLayers() {
        val src = File("src/main/java/dev/nimbus/weather/ui/radar/RadarScreen.kt").readText()
        assertTrue("temperature/wind hidden for a past day", "showTemp && !archive" !in src && "showWind && !archive" !in src)
        assertTrue("layer chips hidden for a past day", "if (!archive) Row(Modifier.fillMaxWidth().horizontalScroll" !in src)
        assertTrue("warnings on a past day", "warnings && !archive" in src)
        // and the DWD's satellite (every 3 hours, a day and a half back) is gone
        assertTrue("Satellite_meteosat" !in File("src/main/java/dev/nimbus/weather/ui/radar/RadarData.kt").readText())
    }
}

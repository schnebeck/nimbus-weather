package dev.nimbus.weather

import dev.nimbus.weather.ui.radar.Mercator
import dev.nimbus.weather.ui.radar.RadarPalette
import dev.nimbus.weather.ui.radar.RadarPalette.Source
import dev.nimbus.weather.ui.radar.RadarSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarPaletteTest {
    private fun alpha(c: Int) = c ushr 24

    @Test
    fun `DWD legend colours map to the unified palette`() {
        val yellow = RadarPalette.mapPixel(0xFFFFFF00.toInt(), Source.DWD)     // 32.5..37 dBZ
        assertEquals(RadarPalette.colorFor(35, false), yellow)
        assertTrue(alpha(yellow) > 200)
    }

    @Test
    fun `DWD no-data grey and transparent white are removed`() {
        assertEquals(0, RadarPalette.mapPixel(0x807D7D7D.toInt(), Source.DWD))
        assertEquals(0, RadarPalette.mapPixel(0x00FFFFFF, Source.DWD))
        // magenta radar coverage outline is not part of the legend
        assertEquals(0, alpha(RadarPalette.mapPixel(0xFFFF00FF.toInt(), Source.DWD)))
    }

    @Test
    fun `RainViewer universal blue decodes to the same scale`() {
        // #00a3e0ff is 20 dBZ in the rain block
        assertEquals(RadarPalette.colorFor(20, false), RadarPalette.mapPixel(0xFF00A3E0.toInt(), Source.RAINVIEWER))
        // #ffee00ff is 35 dBZ
        assertEquals(RadarPalette.colorFor(35, false), RadarPalette.mapPixel(0xFFFFEE00.toInt(), Source.RAINVIEWER))
        // snow block #7fbfffff is 20 dBZ snow
        assertEquals(RadarPalette.colorFor(20, true), RadarPalette.mapPixel(0xFF7FBFFF.toInt(), Source.RAINVIEWER))
        // very weak echoes (< 8 dBZ) are hidden to reduce clutter
        assertEquals(0, RadarPalette.mapPixel(0x14636159, Source.RAINVIEWER))
    }

    @Test
    fun `palette is monotonic in alpha for weak echoes`() {
        assertTrue(alpha(RadarPalette.colorFor(10, false)) < alpha(RadarPalette.colorFor(30, false)))
        assertNotEquals(RadarPalette.colorFor(40, false), RadarPalette.colorFor(40, true))
    }

    @Test
    fun `mercator round trip and bbox`() {
        val z = 7
        val x = Mercator.worldX(13.4, z)
        val y = Mercator.worldY(52.52, z)
        assertEquals(13.4, Mercator.lon(x, z), 1e-9)
        assertEquals(52.52, Mercator.lat(y, z), 1e-9)
        assertEquals("-20037508.343,-20037508.343,20037508.343,20037508.343", Mercator.bbox3857(0, 0, 0))
    }

    @Test
    fun `wms time format`() {
        assertEquals("2026-09-28T20:40:00.000Z", RadarSources.isoTime(java.time.Instant.parse("2026-09-28T20:40:00Z").toEpochMilli()))
    }
}

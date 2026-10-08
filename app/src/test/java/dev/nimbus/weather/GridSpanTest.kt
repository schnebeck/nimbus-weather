/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/GridSpanTest.kt
 * The temperature and wind field spans the whole visible map, however far it is zoomed out.
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

import dev.nimbus.weather.ui.radar.WeatherGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * „Doch noch einen Bug gefunden - gild auch für wind“ – the radar map zoomed out over Germany and
 * Scandinavia: the temperature field (and the wind arrows) filled only a rectangle around the
 * map's centre, from Brandenburg to Trondheim. The grid of 11 x 9 points was 1° apart at most.
 */
class GridSpanTest {
    private fun spans(zoom: Double, south: Double, north: Double, west: Double, east: Double): Boolean {
        val step = WeatherGrid.stepForView(zoom, south, north, west, east)
        val (lat0, lon0) = WeatherGrid.origin((south + north) / 2, (west + east) / 2, step)
        val lat1 = lat0 + 2 * WeatherGrid.HALF_ROWS * step
        val lon1 = lon0 + 2 * WeatherGrid.HALF_COLS * step
        return lat0 <= south && lat1 >= north && lon0 <= west && lon1 >= east
    }

    /** The view of the screenshot: Brittany to Lithuania, the Alps to Trondheim (zoom ~3). */
    @Test fun theZoomedOutViewIsFilled() {
        assertTrue(spans(3.2, 44.0, 66.0, -5.0, 30.0))
        // in between: Benelux to Poland, the Alps to Denmark
        assertTrue(spans(5.0, 47.0, 58.0, 2.0, 20.0))
        assertEquals(4.0, WeatherGrid.stepForView(5.0, 47.0, 58.0, 2.0, 20.0), 0.0)
    }

    /** Germany and closer: the spacing as before, by the zoom (points 100–200 dp apart). */
    @Test fun closerAsBefore() {
        assertEquals(1.0, WeatherGrid.stepForView(6.6, 47.5, 54.5, 7.0, 14.0), 0.0)
        assertEquals(0.25, WeatherGrid.stepForView(8.6, 52.0, 53.2, 9.2, 10.2), 0.0)
        assertTrue(spans(6.6, 47.5, 54.5, 7.0, 14.0))
    }

    /** Far north (and at the date line) the grid stays on the globe: Open-Meteo refuses other points. */
    @Test fun theGridStaysOnTheGlobe() {
        val (lat0, lon0) = WeatherGrid.origin(82.0, 178.0, 8.0)
        assertTrue(lat0 + 2 * WeatherGrid.HALF_ROWS * 8.0 <= 90.0)
        assertTrue(lon0 + 2 * WeatherGrid.HALF_COLS * 8.0 <= 180.0)
    }
}

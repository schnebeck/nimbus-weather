/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/GaugeSourceTest.kt
 * Tests for choosing the nearest water level gauge from PEGELONLINE.
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

import dev.nimbus.weather.data.model.LevelSample
import dev.nimbus.weather.data.remote.GaugeSource
import dev.nimbus.weather.ui.main.titleCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GaugeSourceTest {
    @Test fun riverGaugeInsteadOfTheNearerCanal() {
        // Garbsen: Lohnde on the Mittellandkanal is nearer, but canals are skipped.
        val g = GaugeSource.pickStation(Fixtures.json("pegel_stations_garbsen.json"), 52.42, 9.60)
        assertNotNull(g)
        assertEquals("HERRENHAUSEN", g!!.name)
        assertEquals("LEINE", g.water)
        assertFalse(g.tidal)
        assertTrue("MW" in g.marks)
        assertEquals(6.3, g.distanceKm, 0.3)
    }

    @Test fun tideGaugeInHamburg() {
        val g = GaugeSource.pickStation(Fixtures.json("pegel_stations_hamburg.json"), 53.55, 9.99)!!
        assertEquals("HAMBURG ST. PAULI", g.name)
        assertTrue(g.tidal)
        assertTrue(g.marks.getValue("MThw") > g.marks.getValue("MTnw"))
    }

    @Test fun tenMinuteMeans() {
        val raw = (0 until 30).map { LevelSample(it * 60_000L, it.toDouble()) }
        val b = GaugeSource.binned(raw)
        assertEquals(3, b.size)
        assertEquals(4.5, b[0].value, 1e-9)
        assertEquals(5 * 60_000L, b[0].time)
    }

    @Test fun namesInTitleCase() {
        assertEquals("Cuxhaven Steubenhöft", titleCase("CUXHAVEN STEUBENHÖFT"))
        assertEquals("Hamburg St. Pauli", titleCase("HAMBURG ST. PAULI"))
        assertEquals("Hamburg-Harburg", titleCase("HAMBURG-HARBURG"))
    }
}

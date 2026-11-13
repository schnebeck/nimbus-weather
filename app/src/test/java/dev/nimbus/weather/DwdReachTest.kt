/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DwdReachTest.kt
 * A DWD radar out of service: its area is told from the widest reach seen, not from the edge.
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

import dev.nimbus.weather.ui.radar.DwdCoverage
import dev.nimbus.weather.ui.radar.DwdReach
import dev.nimbus.weather.ui.radar.RadarComposites
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class DwdReachTest {
    private val n = DwdCoverage.W * DwdCoverage.H
    private fun cell(lat: Double, lon: Double) =
        ((DwdCoverage.LAT1 - lat) / 0.025).toInt() * DwdCoverage.W + ((lon - DwdCoverage.LON0) / 0.025).toInt()

    /** Germany covered: a rectangle over it. */
    private val full = BooleanArray(n) { i ->
        val lat = DwdCoverage.LAT1 - (i / DwdCoverage.W + 0.5) * 0.025
        val lon = DwdCoverage.LON0 + (i % DwdCoverage.W + 0.5) * 0.025
        lat in 47.3..55.0 && lon in 5.9..15.0
    }

    /** The radar on the coast out: no data within 1.5° of Emden. */
    private val emdenOut = BooleanArray(n) { i ->
        val lat = DwdCoverage.LAT1 - (i / DwdCoverage.W + 0.5) * 0.025
        val lon = DwdCoverage.LON0 + (i % DwdCoverage.W + 0.5) * 0.025
        full[i] && !(kotlin.math.abs(lat - 53.4) < 0.8 && kotlin.math.abs(lon - 7.2) < 1.3)
    }

    @Before fun setUp() {
        RadarComposites.dir = Files.createTempDirectory("reach").toFile()
        DwdReach.reset()
        // the made-up masks on their own, without the reach shipped with the app
        DwdReach.shipped = { null }
    }

    @After fun tearDown() {
        RadarComposites.dir?.deleteRecursively()
        RadarComposites.dir = null
        DwdReach.reset()
        DwdReach.shipped = DwdReach::reference
    }

    @Test fun aRadarOutIsAHoleInTheWidestReach() {
        DwdReach.join(full)
        DwdReach.join(emdenOut)
        // East Frisia in the picture: out; zoomed in on Norden (11 × 11 cells) too; Bavaria: not
        assertTrue(DwdReach.outIn(6.5, 8.5, 52.8, 54.0))
        assertTrue(DwdReach.outIn(7.07, 7.35, 53.46, 53.73))
        assertFalse(DwdReach.outIn(10.5, 13.0, 47.5, 49.5))
        // and back in service: nothing out
        DwdReach.join(full)
        assertFalse(DwdReach.outIn(6.5, 8.5, 52.8, 54.0))
    }

    /** A few cells at the edge wavering are no radar out. */
    @Test fun theEdgeWaveringIsNoOutage() {
        val wider = full.copyOf().also { m -> for (lon in 0 until 60) m[cell(55.03, 6.0 + lon * 0.025)] = true }
        DwdReach.join(wider)
        DwdReach.join(full)
        assertFalse(DwdReach.outIn(5.0, 8.0, 54.0, 56.0))
        // zoomed in on the edge: its wavering row a small share of the cells there
        assertFalse(DwdReach.outIn(6.0, 6.3, 54.8, 55.1))
    }

    /**
     * A first start while a radar is out: the reach shipped with the app knows it – East Frisia
     * covered, as it was on 12 Nov 2026; a mask with its hole tells the outage at once.
     */
    @Test fun theShippedReachTellsAnOutageAtTheFirstStart() {
        DwdReach.shipped = DwdReach::reference
        val ref = DwdReach.reference()!!
        assertTrue(ref[cell(53.6, 7.2)] && ref[cell(52.37, 9.73)] && ref[cell(48.14, 11.58)])
        assertFalse(ref[cell(48.85, 2.35)])
        val now = BooleanArray(n) { i -> ref[i] && emdenOut[i] }
        DwdReach.join(now)
        assertTrue(DwdReach.outIn(6.5, 8.5, 52.8, 54.0))
        assertFalse(DwdReach.outIn(10.5, 13.0, 47.5, 49.5))
    }

    /** The reference by its whole path: found from any class – the release build renames the packages. */
    @Test fun theReferenceIsFoundByItsWholePath() {
        assertTrue(DwdReach.REFERENCE.startsWith("/"))
        assertTrue(Any::class.java.getResourceAsStream(DwdReach.REFERENCE) != null || DwdReachTest::class.java.getResourceAsStream(DwdReach.REFERENCE) != null)
        assertTrue(java.io.File("src/main/resources" + DwdReach.REFERENCE).exists())
    }

    /** A reach stored while the radar was out: the shipped one fills its hole. */
    @Test fun theShippedReachFillsAStoredHole() {
        DwdReach.join(emdenOut)
        DwdReach.reset()
        DwdReach.shipped = { full }
        DwdReach.join(emdenOut)
        assertTrue(DwdReach.outIn(6.5, 8.5, 52.8, 54.0))
    }

    /** Kept on the device: after a new start the widest reach is still known. */
    @Test fun theWidestReachIsKept() {
        DwdReach.join(full)
        DwdReach.reset()
        DwdReach.join(emdenOut)
        assertTrue(DwdReach.outIn(6.5, 8.5, 52.8, 54.0))
    }
}

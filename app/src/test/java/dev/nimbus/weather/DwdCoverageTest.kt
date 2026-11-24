/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DwdCoverageTest.kt
 * Where the DWD's radars reach: read from the newest analysis, renewed by the hour, kept on the device.
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
import dev.nimbus.weather.ui.radar.DwdFiles
import dev.nimbus.weather.ui.radar.DwdReach
import dev.nimbus.weather.ui.radar.RadarComposites
import dev.nimbus.weather.ui.radar.RadarLatest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class DwdCoverageTest {
    private val min = 60_000L
    private val issue = Instant.parse("2026-11-24T19:20:00Z").toEpochMilli()
    private var now = issue + 3 * min
    private val server = DwdFileServer()

    @Before fun setUp() {
        RadarComposites.dir = Files.createTempDirectory("coverage").toFile()
        RadarLatest.forget()
        DwdFiles.reset()
        DwdFiles.clock = { now }
        DwdCoverage.clock = { now }
        DwdCoverage.set(null)
        DwdReach.reset()
    }

    @After fun tearDown() {
        RadarComposites.dir?.deleteRecursively()
        RadarComposites.dir = null
        RadarLatest.forget()
        DwdFiles.reset()
        DwdFiles.clock = System::currentTimeMillis
        DwdCoverage.clock = System::currentTimeMillis
        DwdCoverage.set(null)
        DwdReach.reset()
    }

    @Test fun theMaskIsTheReachOfTheNewestAnalysis() = runBlocking {
        server.put(issue, wnFile(), newest = true)
        assertFalse(DwdCoverage.ready)
        DwdCoverage.ensure(server.http)
        assertTrue(DwdCoverage.ready)
        assertTrue(DwdCoverage.covers(52.37, 9.73) && DwdCoverage.covers(48.14, 11.58))
        assertFalse(DwdCoverage.covers(48.85, 2.35))
        // the newest archive's head, then the analysis
        assertEquals(2, server.asked.size)
    }

    @Test fun withinTheHourNothingIsAskedAndThenTheNewestAnalysis() = runBlocking {
        server.put(issue, wnFile(), newest = true)
        DwdCoverage.ensure(server.http)
        now += 30 * min
        DwdCoverage.ensure(server.http)
        assertEquals(2, server.asked.size)
        now += 31 * min
        server.put(issue + 60 * min, wnFile(), newest = true)
        DwdCoverage.ensure(server.http)
        assertEquals(4, server.asked.size)
        assertTrue(server.asked.last().startsWith("composite_wn_20261124_2020.tar"))
    }

    @Test fun theMaskInUseStaysWhenTheServerFailsAndIsKeptOnTheDevice() = runBlocking {
        server.put(issue, wnFile(), newest = true)
        DwdCoverage.ensure(server.http)
        now += 2 * 60 * min
        server.down = true
        DwdCoverage.ensure(server.http)
        assertTrue(DwdCoverage.covers(52.37, 9.73))
        // a new start without a connection: the mask from the device
        DwdCoverage.set(null)
        DwdCoverage.ensure(server.http)
        assertTrue(DwdCoverage.covers(52.37, 9.73))
        assertFalse(DwdCoverage.covers(48.85, 2.35))
    }
}

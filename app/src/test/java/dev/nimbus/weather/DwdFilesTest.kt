/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DwdFilesTest.kt
 * The radar from the open data server's files: what is asked for, and when the map service steps in.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Fable 5.2 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Fable 5.2 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */


package dev.nimbus.weather

import dev.nimbus.weather.ui.radar.DwdFiles
import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.GridWindow
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.Tar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DwdFilesTest {
    private val min = 60_000L
    private val issue = Instant.parse("2026-11-24T19:20:00Z").toEpochMilli()
    private val file = wnFile()
    private val server = DwdFileServer()

    private fun analysis(time: Long) = RadarFrame(time, false, null, null)
    private fun ahead(minutes: Int) = RadarFrame(issue + minutes * min, true, issue, null)

    @Before fun setUp() {
        DwdFiles.reset()
        DwdFiles.clock = { issue + 3 * min }
    }

    @After fun tearDown() {
        DwdFiles.reset()
        DwdFiles.clock = System::currentTimeMillis
    }

    @Test fun theNamesTellTheAnalysisAndTheStep() {
        assertEquals("https://opendata.dwd.de/weather/radar/composite/wn/composite_wn_20261124_1920.tar", DwdFiles.url(issue))
        assertEquals(issue to issue + 35 * min, DwdFiles.time("composite_wn_20261124_1920_035-hd5"))
        assertNull(DwdFiles.time("composite_rv_20261124_1920_035-hd5"))
        assertNull(DwdFiles.time("composite_wn_20261341_1920_000-hd5"))
    }

    @Test fun theNewestAnalysisIsNamedByTheHeadOfTheNewestArchive() = runBlocking {
        server.put(issue, file, newest = true)
        assertEquals(issue, DwdFiles.latest(server.http))
        assertEquals(listOf("composite_wn__LATEST.tar bytes=0-511"), server.asked)
    }

    @Test fun theNewestArchiveUnderItsOtherName() = runBlocking {
        server.put(issue, file)
        server.archives["composite_wn_LATEST.tar"] = server.archives.values.first()
        assertEquals(issue, DwdFiles.latest(server.http))
        assertEquals(2, server.asked.size)
    }

    @Test fun anAnalysisIsTheBeginningOfItsArchive() = runBlocking {
        server.put(issue, file)
        assertNotNull(DwdFiles.step(server.http, analysis(issue)))
        // the head and as many bytes as an analysis is guessed to have: the file's 37 kB are among them
        assertEquals(listOf("composite_wn_20261124_1920.tar bytes=0-${Tar.BLOCK + 48 * 1024 - 1}"), server.asked)
    }

    @Test fun aLargerAnalysisTakesASecondRequestAndTheNextOneNone() = runBlocking {
        val large = file + ByteArray(40_000)
        server.put(issue, large)
        server.put(issue - 5 * min, large)
        assertNotNull(DwdFiles.step(server.http, analysis(issue)))
        assertEquals(2, server.asked.size)
        assertEquals("composite_wn_20261124_1920.tar bytes=${Tar.BLOCK + 48 * 1024}-${Tar.BLOCK + large.size - 1}", server.asked[1])
        // the next analysis is asked for with the size of this one and a margin
        assertNotNull(DwdFiles.step(server.http, analysis(issue - 5 * min)))
        assertEquals(3, server.asked.size)
    }

    @Test fun theStepsOfANowcastShareOneArchive() = runBlocking {
        server.put(issue, file)
        server.delayMs = 100
        val steps = listOf(5, 10, 60, 120).map { m -> async(Dispatchers.Default) { DwdFiles.step(server.http, ahead(m)) } }.awaitAll()
        assertTrue(steps.all { it != null })
        assertEquals(listOf("composite_wn_20261124_1920.tar"), server.asked)
        // its analysis comes out of the archive kept
        assertNotNull(DwdFiles.step(server.http, analysis(issue)))
        assertEquals(1, server.asked.size)
        assertNull(DwdFiles.step(server.http, ahead(125)))
    }

    /** A step the files do not have is the map service's: asked for its picture of the window. */
    @Test fun aStepTheServerLacksIsAskedOfTheMapService() = runBlocking {
        assertNull(DwdFiles.step(server.http, analysis(issue)))
        runCatching { DwdRadar.fetch(server.http, analysis(issue), GridWindow(0, 0, 10, 10)) }
        assertEquals("map", server.asked.last())
    }

    @Test fun aFailingConnectionIsNotAnsweredByTheMapService() = runBlocking {
        server.put(issue, file)
        server.down = true
        assertTrue(runCatching { DwdRadar.fetch(server.http, analysis(issue), GridWindow(0, 0, 10, 10)) }.isFailure)
        assertFalse("map" in server.asked)
    }

    @Test fun aStepOlderThanTheFilesComesFromTheMapService() = runBlocking {
        val old = issue - 48 * 60 * min
        server.put(old, file)
        runCatching { DwdRadar.fetch(server.http, analysis(old), GridWindow(0, 0, 10, 10)) }
        assertEquals(listOf("map"), server.asked)
    }

    @Test fun aWindowIsCutFromTheWholeStep() = runBlocking {
        server.put(issue, file)
        // around the strong cell over the Baltic coast (54.205 N, 12.08 E)
        val window = GridWindow(1060, 205, 16, 10)
        val cells = DwdRadar.fetch(server.http, analysis(issue), window)!!
        assertEquals(window.size, cells.size)
        assertEquals(59, cells[(209 - 205) * 16 + (1067 - 1060)].toInt())
    }
}

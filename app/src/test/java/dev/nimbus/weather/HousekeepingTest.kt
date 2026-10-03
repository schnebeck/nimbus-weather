/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/HousekeepingTest.kt
 * Every stored file has its time: data of a moment go after the look-back's four days (the live
 * radar's grids after a day), lists after a month without use, the weather of a place given up
 * at once.
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

import dev.nimbus.weather.data.repo.Housekeeping
import dev.nimbus.weather.data.repo.Store
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HousekeepingTest {
    private val h = 3_600_000L
    private val d = 24 * h
    private val now = 1_791_000_000_000L
    private val root = java.nio.file.Files.createTempDirectory("sweep").toFile()
    private val cache = File(root, "cache")
    private val files = File(root, "files")

    @After fun tearDown() { root.deleteRecursively() }

    /** A file in [dir] written [age] ago. */
    private fun file(dir: File, name: String, age: Long): File =
        File(dir, name).apply { parentFile!!.mkdirs(); writeText("x"); setLastModified(now - age) }

    @Test fun whatNobodyNeedsGoes() {
        val kept = mutableListOf<File>()
        val gone = mutableListOf<File>()
        // temperature and wind of the radar: the live one a day, a past day's (look-back) four days
        kept += file(File(cache, "grid"), "grid_1.0_47.0_6.0.json", 23 * h)
        gone += file(File(cache, "grid"), "grid_1.0_47.0_6.0_x.json", 25 * h)
        kept += file(File(cache, "grid"), "grid_1.0_47.0_6.0_p97.json", 3 * d)
        gone += file(File(cache, "grid"), "grid_1.0_48.0_6.0_p49.json", 5 * d)
        // the preview: its radar picture of a moment, its base map and lines a month
        gone += file(File(cache, "previews"), "radar_51.9_10.6.png", 5 * d)
        kept += file(File(cache, "previews"), "base2_51.9_10.6_de.png", 5 * d)
        gone += file(File(cache, "previews"), "lines2_48.1_11.6_de.png", 31 * d)
        // bathing waters: the latest samples of a moment, the list of an area a month
        gone += file(File(cache, "bathing"), "sh_proben.csv", 5 * d)
        kept += file(File(cache, "bathing"), "eea_52.4_9.7_25.json", 20 * d)
        gone += file(File(cache, "bathing"), "eea_47.4_8.5_25.json", 31 * d)
        // gauge lists and tide fits, radar coverage: a month without use
        kept += file(File(cache, "tides"), "wiski_nrw.json", 10 * d)
        gone += file(File(cache, "tides"), "tide_4711.json", 31 * d)
        gone += file(File(cache, "radar"), "dwd_coverage.bin", 31 * d)
        // not ours: the HTTP caches (by size) and the radar frames (their own store)
        kept += file(File(cache, "http"), "journal", 100 * d)
        kept += file(File(cache, "maptiles"), "a.0", 100 * d)
        kept += file(File(cache, "radarstore"), "dwd_1.nrd", 100 * d)
        // the last weather of the places: of those kept, at most four days old
        kept += file(File(files, Store.CACHE_DIR), Store.cacheFileName("current-location"), 1 * d)
        kept += file(File(files, Store.CACHE_DIR), Store.cacheFileName("berlin"), 3 * d)
        gone += file(File(files, Store.CACHE_DIR), Store.cacheFileName("paris"), 1 * h)       // given up
        gone += file(File(files, Store.CACHE_DIR), Store.cacheFileName("rome"), 5 * d)        // kept, but old

        val n = Housekeeping.sweep(cache, files, setOf("current-location", "berlin", "rome").map(Store::cacheFileName).toSet(), now)
        assertEquals(gone.size, n)
        for (f in kept) assertTrue("${f.parentFile!!.name}/${f.name} deleted", f.exists())
        for (f in gone) assertFalse("${f.parentFile!!.name}/${f.name} kept", f.exists())
    }

    @Test fun atMostOnceADay() {
        assertFalse(Housekeeping.due(now - 23 * h, now))
        assertTrue(Housekeeping.due(now - 24 * h, now))
        assertTrue(Housekeeping.due(0L, now))
    }
}

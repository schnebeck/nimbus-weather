/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/HourAxisTest.kt
 * Day charts: every hourly bar is centred under its long-press cursor, and touching a bar picks it.
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

import dev.nimbus.weather.ui.main.HourAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HourAxisTest {
    private val h = HourAxis.HOUR
    private val start = 1_790_800_000_000L / h * h
    private val end = start + 24 * h
    /** The points of a day chart: 00:00 … 24:00, each value for the hour before. */
    private val times = (0..24).map { start + it * h }
    private val axis = HourAxis(start, end, 40f, 1000f)

    @Test fun everyBarIsCentredUnderItsCursor() {
        for (t in times.drop(1)) {
            val left = axis.barLeft(t)
            val centre = left + axis.barWidth() / 2
            assertEquals("bar of ${(t - start) / h}:00", axis.cursor(t), centre, 0.01f)
        }
    }

    @Test fun barsStandOnTheirHourInsideThePlot() {
        for (t in times.drop(1)) {
            assertTrue(axis.barLeft(t) >= axis.x(t - h) - 0.01f)
            assertTrue(axis.barLeft(t) + axis.barWidth() <= axis.x(t) + 0.01f)
        }
        // the first bar (00–01) starts right of the axis, the last (23–24) ends left of its end
        assertTrue(axis.barLeft(times[1]) >= axis.left)
        assertTrue(axis.barLeft(times.last()) + axis.barWidth() <= axis.right)
    }

    @Test fun touchingABarPicksItsHour() {
        for (i in 1 until times.size) {
            val t = times[i]
            assertEquals(i, axis.indexAt(axis.cursor(t), times))
            assertEquals(i, axis.indexAt(axis.barLeft(t) + 1f, times))
            assertEquals(i, axis.indexAt(axis.barLeft(t) + axis.barWidth() - 1f, times))
        }
        // 00:00 belongs to the previous day: never picked
        assertEquals(1, axis.indexAt(axis.left, times))
    }

    /**
     * The charts must take bars and cursor from HourAxis only – computing their own positions is
     * how they drifted apart before (more than once).
     */
    @Test fun chartsUseTheSharedGeometry() {
        val main = File("src/main/java/dev/nimbus/weather/ui/main")
        for (name in listOf("Meteogram.kt", "DetailTiles.kt")) {
            val src = File(main, name).readText()
            assertTrue("$name: bars from HourAxis", src.contains("barLeft(") && src.contains("barWidth()"))
            assertTrue("$name: cursor from HourAxis", src.contains(".cursor("))
            assertTrue("$name: touch from HourAxis", src.contains(".indexAt("))
            // no hand-made bar geometry
            assertTrue("$name: own bar offset", !Regex("""hour[Ww] \* 0\.3|bw \* 0\.3""").containsMatchIn(src))
        }
    }
}

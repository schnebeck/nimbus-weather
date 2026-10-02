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

    @Test fun barCurvePointAndCursorOfAnHourAreOneColumn() {
        for (t in times.drop(1)) {
            val centre = axis.barLeft(t) + axis.barWidth() / 2
            assertEquals("bar of ${(t - start) / h}:00", axis.cursor(t), centre, 0.01f)
            assertEquals("curve point of ${(t - start) / h}:00", axis.cursor(t), axis.point(t), 0.01f)
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
        // the hourly charts: the meteogram, and the precipitation chart in DetailTiles (the sun's
        // path there is a continuous curve, not hourly)
        for ((name, chart) in listOf("Meteogram.kt" to "fun Meteogram(", "DetailTiles.kt" to "fun PrecipChart(")) {
            val file = File(main, name).readText()
            val from = file.indexOf(chart)
            assertTrue("$name: $chart not found", from >= 0)
            val src = file.substring(from, file.indexOf("\n@Composable", from).takeIf { it > 0 } ?: file.length)
            assertTrue("$name: bars from HourAxis", src.contains("barLeft(") && src.contains("barWidth()"))
            assertTrue("$name: cursor from HourAxis", src.contains(".cursor("))
            assertTrue("$name: touch from HourAxis", src.contains(".indexAt("))
            assertTrue("$name: curve points from HourAxis", src.contains("axis.point("))
            // no hand-made bar geometry, no curve points on the bare time stamp
            assertTrue("$name: own bar offset", !Regex("""hour[Ww] \* 0\.3|bw \* 0\.3""").containsMatchIn(src))
            assertTrue("$name: curve point on the time stamp", !Regex("""(moveTo|lineTo)\(x\(""").containsMatchIn(src))
        }
    }

    @Test fun labelBarCurvePointAndCursorOfAnHourAreOneColumn() {
        for (hs in axis.labelHours()) {
            val t = hs + h                                        // the hour's value has the time stamp at its end
            val bar = axis.barLeft(t) + axis.barWidth() / 2
            assertEquals("label ${(hs - start) / h}", axis.cursor(t), axis.label(hs), 0.01f)
            assertEquals(axis.label(hs), bar, 0.01f)
            assertEquals(axis.label(hs), axis.point(t), 0.01f)
        }
        // 00, 03 … 21 – no "24": no hour starts there
        assertEquals((0 until 24 step 3).map { start + it * h }, axis.labelHours())
    }

    /**
     * Every chart with a long-press cursor, wherever it is: hourly charts (bars) take labels,
     * bars, points and cursor from HourAxis; charts of moments (pressure, water level) put the
     * labels and the cursor on the same moment. A new chart with a cursor has to be put in one of
     * the two lists – that is the point: nobody adds a cursor without this check.
     */
    @Test fun everyCursorChartKeepsLabelsUnderTheCursor() {
        val hourly = mapOf("Meteogram.kt" to "fun Meteogram(", "DetailTiles.kt" to "fun PrecipChart(")
        val moments = mapOf("PressureCard.kt" to "private fun PressureChart(", "GaugeCard.kt" to "val cursorAlpha")
        val ui = File("src/main/java/dev/nimbus/weather/ui")
        val withCursor = ui.walkTopDown().filter { it.extension == "kt" && "cursorAlpha" in it.readText() }.map { it.name }.toSet()
        assertEquals("charts with a cursor", withCursor, hourly.keys + moments.keys)
        fun body(name: String, marker: String): String {
            val f = ui.walkTopDown().first { it.name == name }.readText()
            val from = f.indexOf(marker)
            assertTrue("$name: $marker", from >= 0)
            return f.substring(from, f.indexOf("\n@Composable", from).takeIf { it > 0 } ?: f.length)
        }
        for ((name, marker) in hourly) {
            val src = body(name, marker)
            assertTrue("$name: labels from HourAxis", ".label(" in src && "labelHours(" in src)
            assertTrue("$name: label on a bare time", !Regex("""val xm = x\(""").containsMatchIn(src))
        }
        for ((name, marker) in moments) {
            val src = body(name, marker)
            assertTrue("$name: cursor on the moment", Regex("""val xc = x\(p\.time\)""").containsMatchIn(src))
            assertTrue("$name: labels on the moment", Regex("""val xm = x\(mark\)""").containsMatchIn(src))
        }
    }
}

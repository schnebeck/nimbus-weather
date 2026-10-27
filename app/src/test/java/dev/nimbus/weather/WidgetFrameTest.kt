/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WidgetFrameTest.kt
 * The widget finds the frame the launcher gives it from the picture it shows – whatever size the
 * launcher reports.
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

import android.util.SizeF
import dev.nimbus.weather.widget.FrameSearch
import dev.nimbus.weather.widget.WidgetFrames
import dev.nimbus.weather.widget.WidgetProbe
import dev.nimbus.weather.widget.WidgetSize
import dev.nimbus.weather.widget.Widgets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WidgetFrameTest {
    private val app get() = RuntimeEnvironment.getApplication()

    /** The system's choice: the closest picture that fits the frame, the smallest where none fits. */
    private fun shown(offered: List<WidgetSize>, w: Float, h: Float): WidgetSize =
        offered.filter { it.widthDp <= w && it.heightDp <= h }
            .minByOrNull { (w - it.widthDp) * (w - it.widthDp) + (h - it.heightDp) * (h - it.heightDp) }
            ?: offered.minBy { it.widthDp * it.heightDp }

    /**
     * A launcher reporting [sizes] and [bounds] and giving the widget a frame of [w] × [h]: the
     * rounds until the frame is found – each round's pictures offered, the one shown answering.
     */
    private fun search(sizes: List<SizeF>, bounds: Widgets.Bounds, w: Float, h: Float, rounds: Int = 12): Pair<FrameSearch, Int> {
        val base = Widgets.offers(sizes, bounds)
        var s = FrameSearch("figures")
        var n = 0
        while (n < rounds) {
            s = s.next(base, now = 0L)
            n++
            s = s.heard(s.round, shown(s.offered, w, h)) ?: break
        }
        return s to n
    }

    private fun assertFound(s: FrameSearch, w: Float, h: Float) {
        val f = s.fit!!
        assertTrue("$s", s.settled)
        assertTrue("$f in $w × $h", f.widthDp <= w && f.widthDp >= w - FrameSearch.DONE)
        assertTrue("$f in $w × $h", f.heightDp <= h && f.heightDp >= h - FrameSearch.DONE)
    }

    /**
     * Nova reports 410 × 300 dp upright and gives a frame of 410.67 × 259.56 dp – with its
     * padding off 426 × 316 and 426.67 × 275.56: in a few rounds the frame within 2 dp.
     */
    @Test fun novasFrameWhateverItReports() {
        val (with, n1) = search(listOf(SizeF(410f, 300f), SizeF(808f, 132f)), Widgets.Bounds(410, 132, 808, 300), 410.67f, 259.56f)
        assertFound(with, 410.67f, 259.56f)
        assertTrue("$n1 rounds", n1 <= 4)
        val (without, n2) = search(listOf(SizeF(426f, 316f), SizeF(824f, 148f)), Widgets.Bounds(426, 148, 824, 316), 426.67f, 275.56f)
        assertFound(without, 426.67f, 275.56f)
        assertTrue("$n2 rounds", n2 <= 4)
    }

    /** A launcher whose figures are its frame (the Pixel's; Kvaesitso's one size): that frame, nothing larger. */
    @Test fun aTrueLaunchersFrame() {
        val (pixel, n) = search(listOf(SizeF(360f, 344f), SizeF(624f, 208f)), Widgets.Bounds(360, 208, 624, 344), 360f, 344f)
        assertTrue("$n rounds", n <= 4)
        assertEquals(WidgetSize(360, 344), pixel.fit)
        assertFound(pixel, 360f, 344f)
        val (one, _) = search(listOf(SizeF(233f, 94f)), Widgets.Bounds(233, 94, 233, 94), 233f, 94f)
        assertEquals(WidgetSize(233, 94), one.fit)
    }

    /** A frame larger than reported, and one smaller than the least size reported (none of those fits): found all the same. */
    @Test fun framesBeyondTheFigures() {
        val (larger, _) = search(listOf(SizeF(300f, 150f)), Widgets.Bounds(300, 150, 300, 150), 330.4f, 214.8f, rounds = 20)
        assertFound(larger, 330.4f, 214.8f)
        val (smaller, _) = search(listOf(SizeF(426f, 316f), SizeF(824f, 148f)), Widgets.Bounds(426, 148, 824, 316), 410.67f, 259.56f, rounds = 20)
        assertFound(smaller, 410.67f, 259.56f)
    }

    /** Found, the pictures stay those of the frame: shown anew, nothing more to search – no round after round. */
    @Test fun foundTheSearchRests() {
        val base = Widgets.offers(listOf(SizeF(410f, 300f), SizeF(808f, 132f)), Widgets.Bounds(410, 132, 808, 300))
        val (found, _) = search(listOf(SizeF(410f, 300f), SizeF(808f, 132f)), Widgets.Bounds(410, 132, 808, 300), 410.67f, 259.56f)
        val again = found.next(base, now = 0L)
        assertEquals(found.fit, again.offered.first())
        assertTrue(FrameSearch.FLOOR in again.offered)
        assertNull(again.heard(again.round, shown(again.offered, 410.67f, 259.56f)))
        // an answer to an earlier round counts for nothing
        assertNull(again.heard(again.round - 1, WidgetSize(410, 258)))
    }

    /** A frame that keeps changing is not chased: at most [FrameSearch.BUDGET] rounds of searching in ten minutes. */
    @Test fun aRestlessFrameIsNotChased() {
        val base = Widgets.offers(listOf(SizeF(410f, 300f), SizeF(808f, 132f)), Widgets.Bounds(410, 132, 808, 300))
        var s = FrameSearch("figures")
        var searched = 0
        for (i in 0 until 40) {
            val before = s.budgetUsed
            s = s.next(base, now = i * 1000L)
            if (s.budgetUsed > before) searched++
            // the frame flips between two sizes each round
            val (w, h) = if (i % 2 == 0) 410.67f to 259.56f else 380.2f to 230.1f
            s = s.heard(s.round, shown(s.offered, w, h)) ?: s
        }
        assertEquals(FrameSearch.BUDGET, searched)
        // then only the size that fitted and those from the launcher's figures
        assertTrue("${s.offered}", base.take(3).all { it in s.offered })
    }

    /**
     * The pixel fetched from the launcher: the widget's search told, and the pixel an empty PNG;
     * kept in the widget's store across restarts.
     */
    @Test fun thePixelTellsTheSearch() {
        val frames = WidgetFrames(app)
        val base = Widgets.offers(listOf(SizeF(410f, 300f), SizeF(808f, 132f)), Widgets.Bounds(410, 132, 808, 300))
        val first = frames.next(7, "figures", base)
        assertTrue(WidgetSize(410, 258) in first.offered)
        val uri = WidgetProbe.uri(app, 7, first.round, WidgetSize(410, 258))
        val provider = org.robolectric.Robolectric.buildContentProvider(WidgetProbe::class.java).create("${app.packageName}.probe").get()
        val png = provider.openFile(uri, "r")!!.let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), png.copyOf(4))
        val now = frames.of(7)
        assertNotNull(now)
        assertEquals(WidgetSize(410, 258), now!!.fit)
        assertEquals(300, now.overH)
        // the launcher's figures changed (the widget resized): searched anew
        assertNull(frames.next(7, "other figures", base).fit)
    }
}

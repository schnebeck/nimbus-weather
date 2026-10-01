/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ReorderTest.kt
 * Drag-to-sort: no flipping at the boundary, rows of different height, first and last row.
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

import dev.nimbus.weather.ui.components.Reorder
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    // Four rows of 100 px, no gap
    private val rows = List(4) { Reorder.Row(it * 100f, 100f) }

    @Test fun movesOnceTheLeadingEdgeCoversTheNeighbour() {
        // row 1 (top 100) dragged down: its bottom must pass 200 + 70, i.e. top > 170
        assertEquals(1, Reorder.target(rows, 1, 150f))
        assertEquals(1, Reorder.target(rows, 1, 169f))
        assertEquals(2, Reorder.target(rows, 1, 171f))
        assertEquals(3, Reorder.target(rows, 1, 280f))           // fast drag: two rows at once
        // dragged up: its top must pass 0 + 30
        assertEquals(1, Reorder.target(rows, 1, 31f))
        assertEquals(0, Reorder.target(rows, 1, 29f))
    }

    @Test fun noFlippingBackAtTheBoundary() {
        // Moved from 1 to 2 at top 171. Re-laid out, the row is index 2 (top 200), the former
        // row 2 at top 100. A finger wiggling ±8 px around 171 must not move it back:
        for (top in listOf(163f, 179f, 163f, 179f)) assertEquals(2, Reorder.target(rows, 2, top))
        // back up only after a clear counter-movement (top below 100 + 30)
        assertEquals(2, Reorder.target(rows, 2, 131f))
        assertEquals(1, Reorder.target(rows, 2, 129f))
    }

    @Test fun rowsOfDifferentHeight() {
        // A single row above an expanded group of 400 px and below it another single row
        val r = listOf(Reorder.Row(0f, 100f), Reorder.Row(100f, 400f), Reorder.Row(500f, 100f))
        // the single row passes the group once its bottom covers 70 % of it (100 + 280)
        assertEquals(0, Reorder.target(r, 0, 270f))
        assertEquals(1, Reorder.target(r, 0, 290f))
        // the tall group passes the single row below it as readily: its bottom past 500 + 70
        assertEquals(1, Reorder.target(r, 1, 160f))
        assertEquals(2, Reorder.target(r, 1, 175f))
    }

    @Test fun firstAndLastRowStayInTheList() {
        assertEquals(0f, Reorder.clampTop(rows, 0, -250f))       // first row pulled up
        assertEquals(0, Reorder.target(rows, 0, Reorder.clampTop(rows, 0, -250f)))
        assertEquals(300f, Reorder.clampTop(rows, 3, 900f))      // last row pulled down
        assertEquals(3, Reorder.target(rows, 3, Reorder.clampTop(rows, 3, 900f)))
        // the first row dragged all the way down ends last, the last all the way up ends first
        assertEquals(3, Reorder.target(rows, 0, Reorder.clampTop(rows, 0, 900f)))
        assertEquals(0, Reorder.target(rows, 3, Reorder.clampTop(rows, 3, -900f)))
    }
}

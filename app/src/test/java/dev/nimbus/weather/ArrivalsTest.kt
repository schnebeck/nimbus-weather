/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ArrivalsTest.kt
 * Which cards pop in: those whose data comes after the page is shown – once, not again when they
 * scroll back into view, and never the cards the page started with; and each card's status dot.
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

import dev.nimbus.weather.ui.components.CardStatus
import dev.nimbus.weather.ui.main.Arrivals
import dev.nimbus.weather.ui.main.cardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalsTest {
    private var now = 0L
    private val arrivals = Arrivals { now }

    @Test fun theFirstCardsAreThereTheLaterOnesPopIn() {
        arrivals.note(listOf("header", "precip", "daily", "sources"))
        assertFalse(arrivals.fresh("precip"))
        // the extras arrive
        now = 1_500
        arrivals.note(listOf("header", "precip", "daily", "pollen", "gauge", "sources"))
        assertTrue(arrivals.fresh("pollen"))
        assertTrue(arrivals.fresh("gauge"))
        assertFalse(arrivals.fresh("daily"))
        // the same list again (recomposition) changes nothing
        now = 2_000
        arrivals.note(listOf("header", "precip", "daily", "pollen", "gauge", "sources"))
        assertTrue(arrivals.fresh("pollen"))
    }

    @Test fun statusDotFollowsTheCardsData() {
        val pollen = setOf(dev.nimbus.weather.data.model.DataPart.POLLEN)
        assertEquals(CardStatus.STALE, cardStatus("pollen", pollen))
        assertEquals(CardStatus.FRESH, cardStatus("daily", pollen))
        // warnings: the DWD's and the states' flood alerts
        assertEquals(CardStatus.STALE, cardStatus("alerts", setOf(dev.nimbus.weather.data.model.DataPart.FLOOD)))
        assertNull(cardStatus("sources", pollen))
        assertNull(cardStatus("models", emptySet()))
    }

    /** Tablet columns: a card alone between wide ones (precipitation above the hourly row) spans the width. */
    @Test fun aLonelyCardSpansTheColumns() {
        // header, precipitation, hourly, 10 days, radar, tiles, sources
        val wide = listOf(true, false, true, false, false, false, true)
        assertTrue(dev.nimbus.weather.ui.main.lonely(wide, 1))           // precipitation: alone
        assertFalse(dev.nimbus.weather.ui.main.lonely(wide, 3))          // 10 days: beside the radar
        assertFalse(dev.nimbus.weather.ui.main.lonely(wide, 5))          // tiles: below the radar in the other column
        assertTrue(dev.nimbus.weather.ui.main.lonely(listOf(true, false), 1))   // the last one
    }

    @Test fun onlyOnceNotWhenScrolledBackLater() {
        arrivals.note(listOf("a"))
        now = 100
        arrivals.note(listOf("a", "b"))
        now = 100 + Arrivals.FRESH_MS + 1
        assertFalse(arrivals.fresh("b"))
    }
}

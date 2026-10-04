/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CardRedrawTest.kt
 * Model, view, controller: a record that goes out of date tells its card – that card alone is
 * drawn anew, at once; no timer redraws the page.
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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.repo.Shelf
import dev.nimbus.weather.ui.components.CardStatus
import dev.nimbus.weather.ui.components.LocalCardStatus
import dev.nimbus.weather.ui.main.Arrivals
import dev.nimbus.weather.ui.main.Card
import dev.nimbus.weather.ui.main.LocalShelf
import dev.nimbus.weather.ui.main.PageItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CardRedrawTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyTheCardOfTheRecordIsDrawnAnew() {
        val shelf = Shelf(kotlinx.coroutines.MainScope())
        val now = System.currentTimeMillis()
        compose.runOnUiThread {
            shelf.part("b", DataPart.POLLEN).arrived(now)
            shelf.part("b", DataPart.FORECAST).arrived(now)
        }
        val drawn = mutableMapOf("pollen" to 0, "hourly" to 0, "page" to 0)
        val shown = mutableMapOf<String, CardStatus?>()
        fun item(key: String) = PageItem(key) {
            val status = LocalCardStatus.current
            SideEffect { drawn[key] = drawn.getValue(key) + 1; shown[key] = status }
        }
        val pollen = item("pollen")
        val hourly = item("hourly")
        val arrivals = Arrivals()
        compose.setContent {
            CompositionLocalProvider(LocalShelf provides shelf) {
                // the page around the cards: an event of a record must not draw it anew
                SideEffect { drawn["page"] = drawn.getValue("page") + 1 }
                Column {
                    Card(pollen, arrivals, "b")
                    Card(hourly, arrivals, "b")
                }
            }
        }
        compose.waitForIdle()
        assertEquals(CardStatus.FRESH, shown["pollen"])
        val before = drawn.toMap()

        // the pollen record goes out of date: its card yellow at once – the forecast's card untouched
        compose.runOnUiThread { shelf.part("b", DataPart.POLLEN).stale() }
        compose.waitForIdle()
        assertEquals(CardStatus.STALE, shown["pollen"])
        assertEquals(CardStatus.FRESH, shown["hourly"])
        assertEquals("the pollen card drawn anew", before.getValue("pollen") + 1, drawn.getValue("pollen"))
        assertEquals("the forecast's card drawn for nothing", before.getValue("hourly"), drawn.getValue("hourly"))
        assertEquals("the page drawn anew", before.getValue("page"), drawn.getValue("page"))

        // new pollen data: green again, the other card still untouched
        compose.runOnUiThread { shelf.part("b", DataPart.POLLEN).arrived(System.currentTimeMillis()) }
        compose.waitForIdle()
        assertEquals(CardStatus.FRESH, shown["pollen"])
        assertEquals(before.getValue("hourly"), drawn.getValue("hourly"))
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PlaceListTest.kt
 * "My location" in the list of places shows whether its position is current – the pin with the
 * status dot of its page; the saved places have none.
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.ui.main.LocationMark
import dev.nimbus.weather.ui.places.PlaceCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de", application = android.app.Application::class)
class PlaceListTest {
    @get:Rule val compose = createComposeRule()

    private val here = Place("current-location", "Hannover", latitude = 52.37, longitude = 9.73, isCurrentLocation = true)
    private val berlin = Place("berlin", "Berlin", latitude = 52.52, longitude = 13.40)

    private fun count(text: Int) = compose.onAllNodesWithContentDescription(
        org.robolectric.RuntimeEnvironment.getApplication().getString(text),
    ).fetchSemanticsNodes().size

    @Test fun myLocationSaysWhetherItIsCurrent() {
        var mark = LocationMark(current = false, searching = false, off = false)
        val state = androidx.compose.runtime.mutableStateOf(mark)
        compose.setContent {
            Column {
                PlaceCard(here, null, Settings(), location = state.value) {}
                PlaceCard(berlin, null, Settings()) {}
            }
        }
        compose.waitForIdle()
        assertEquals(1, count(R.string.location_not_current))
        mark = mark.copy(current = true)
        state.value = mark
        compose.waitForIdle()
        assertEquals(1, count(R.string.location_current))
        assertEquals(0, count(R.string.location_not_current))
    }
}

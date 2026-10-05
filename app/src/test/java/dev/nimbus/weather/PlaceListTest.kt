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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.ui.UiState
import dev.nimbus.weather.ui.places.PlacesScreen
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.ui.main.LocationMark
import dev.nimbus.weather.ui.places.PlaceCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
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

    /**
     * The dot stands apart from the pin (on it, it covered half the pin's point) with its centre
     * at the height of the capitals of the name.
     */
    @Test fun theDotBesideThePinAtTheCapitals() {
        compose.setContent { PlaceCard(here, null, Settings(), location = LocationMark(current = true, searching = false, off = false)) {} }
        compose.waitForIdle()
        compose.assertDotBesidePin(org.robolectric.RuntimeEnvironment.getApplication().getString(R.string.my_location), 22f)
    }

    private fun places(
        saved: List<Place>, onSetModel: (Place, ForecastModel?) -> Unit = { _, _ -> },
        onDuplicate: (Place) -> Place = { dev.nimbus.weather.ui.places.PlaceTwins.copyOf(it, saved) },
    ) = compose.setContent {
        PlacesScreen(
            UiState(initialized = true, savedPlaces = saved, selectedPlaceId = saved.first().id), search = { emptyList() },
            onAdd = {}, onRemove = {}, onReorder = {}, onSetModel = onSetModel, onDuplicate = onDuplicate,
            onOpen = {}, onSettings = {}, onRequestLocation = {}, onBack = {},
        )
    }

    /** "⧉": the place once more – and its model chosen right away, for the copy. */
    @Test fun aPlaceOnceMoreForAnotherModel() {
        var copied: Place? = null
        var chosen: Pair<String, ForecastModel?>? = null
        places(listOf(berlin), onSetModel = { p, m -> chosen = p.id to m }, onDuplicate = { p -> dev.nimbus.weather.ui.places.PlaceTwins.copyOf(p, listOf(berlin)).also { copied = it } })
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        compose.onNodeWithTag("duplicate-berlin").performClick()
        assertEquals("berlin#2", copied?.id)
        compose.onNodeWithText("Vorhersagemodell für Berlin").assertExists()
        compose.onNodeWithText("KNMI Harmonie (2 km)").performScrollTo().performClick()
        assertEquals("berlin#2" to ForecastModel.KNMI, chosen)
    }

    /**
     * "Jeder gespeicherte Ort bekommt optional ein eigenes Modell, z. B. per langem Druck auf den Ort
     * in der Ortsliste": in the edit mode under its name, tapped – the choice.
     */
    @Test fun aPlaceGetsAModelOfItsOwn() {
        var chosen: Pair<String, ForecastModel?>? = null
        places(listOf(berlin), onSetModel = { p, m -> chosen = p.id to m })
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        compose.onNodeWithText("Modell: Automatisch (App-Einstellung)").performClick()
        compose.onNodeWithText("Eigenes Modell für diesen Ort").performClick()
        compose.onNodeWithText("MET Nordic (1 km)").performScrollTo().performClick()
        assertEquals("berlin" to ForecastModel.MET_NORWAY, chosen)
    }

    /** A place with its own model says which; back to the settings' one by "Wie in den App-Einstellungen". */
    @Test fun aPlaceSaysItsOwnModel() {
        var chosen: Pair<String, ForecastModel?>? = "none" to null
        places(listOf(berlin.copy(model = ForecastModel.MET_NORWAY)), onSetModel = { p, m -> chosen = p.id to m })
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        compose.onNodeWithText("Modell: MET Nordic").performClick()
        compose.onNodeWithText("Wie in den App-Einstellungen").performClick()
        assertEquals("berlin" to null, chosen)
    }

    /**
     * "zuerst zwischen Globale App einstellung oder die Lokalen Einstellungen wählen … aktuell ist
     * Standard auf gleicher ebene wie die anderen": first the app's setting or a model of its own,
     * the models only beneath the latter – switching to it alone changes nothing.
     */
    @Test fun firstAppSettingOrItsOwnThenTheModel() {
        var chosen: Pair<String, ForecastModel?>? = null
        places(listOf(berlin), onSetModel = { p, m -> chosen = p.id to m })
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        compose.onNodeWithText("Modell: Automatisch (App-Einstellung)").performClick()
        compose.onNodeWithText("Wie in den App-Einstellungen").assertExists()
        compose.onNodeWithText("zurzeit: Automatisch – ändert sich mit ihnen").assertExists()
        // no "Standard" among the models, and the models not yet shown
        compose.onAllNodesWithText("Standard", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("MET Nordic (1 km)").assertCountEquals(0)
        compose.onNodeWithText("Eigenes Modell für diesen Ort").performClick()
        compose.onNodeWithText("MET Nordic (1 km)").performScrollTo().assertExists()
        assertEquals(null, chosen)
        // the models beneath the choice of its own (indented)
        val own = compose.onNodeWithText("Eigenes Modell für diesen Ort").fetchSemanticsNode().boundsInRoot
        val model = compose.onNodeWithText("MET Nordic (1 km)").fetchSemanticsNode().boundsInRoot
        assertTrue("$model not beneath $own", model.left > own.left && model.top > own.top)
    }
}

/** The status dot of "my location" beside its pin, centred at the capitals' height of [name] in [sp]. */
internal fun androidx.compose.ui.test.junit4.ComposeContentTestRule.assertDotBesidePin(name: String, sp: Float) {
    val pin = onNode(androidx.compose.ui.test.hasTestTag("location-pin"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val dot = onNode(androidx.compose.ui.test.hasTestTag("location-dot"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    org.junit.Assert.assertTrue("dot $dot on the pin $pin", !pin.overlaps(dot))
    val text = onNode(androidx.compose.ui.test.hasText(name), useUnmergedTree = true).fetchSemanticsNode()
    val results = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
    text.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    val baseline = text.boundsInRoot.top + results.first().firstBaseline
    val density = org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics.scaledDensity
    val capTop = baseline - sp * density * dev.nimbus.weather.ui.main.CAP_HEIGHT
    org.junit.Assert.assertEquals("dot centre (${dot.center.y}) at the capitals' top ($capTop)", capTop, dot.center.y, 1.5f)
    org.junit.Assert.assertTrue("dot before the name's end", dot.left > text.boundsInRoot.left)
}

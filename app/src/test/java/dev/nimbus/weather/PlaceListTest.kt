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
import org.junit.Assert.assertFalse
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

    /**
     * The symbol of now left of the temperature – by day or night as the sun stands at the place
     * now, not as the data's flag says (it stands for their fetch): the sun at noon, the moon at
     * night, the words under the name alike.
     */
    @Test fun theSymbolBesideTheTemperature() {
        val noon = java.time.Instant.parse("2026-10-27T11:00:00Z").toEpochMilli()
        val night = java.time.Instant.parse("2026-10-27T21:00:00Z").toEpochMilli()
        val cur = dev.nimbus.weather.data.model.CurrentWeather(noon, 12.4, 11.0, dev.nimbus.weather.data.model.Condition.CLEAR, true, 70.0, 7.0, 1015.0, 12.0, 25.0, 240.0, 0.0, 20000.0, 2.0, 0.0)
        val data = dev.nimbus.weather.data.model.WeatherData(berlin, "Europe/Berlin", 0, cur, emptyList(), emptyList(), emptyList(), sources = emptyList(), fetchedAt = noon)
        val at = androidx.compose.runtime.mutableLongStateOf(noon)
        compose.setContent { PlaceCard(berlin, dev.nimbus.weather.ui.PlaceState(data = data), Settings(), now = at.longValue) {} }
        compose.waitForIdle()
        val symbol = compose.onNodeWithTag("place-symbol-berlin", useUnmergedTree = true).fetchSemanticsNode()
        val temperature = compose.onNodeWithText("12", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("${symbol.boundsInRoot} left of ${temperature.boundsInRoot}", symbol.boundsInRoot.right <= temperature.boundsInRoot.left)
        assertEquals(temperature.boundsInRoot.center.y, symbol.boundsInRoot.center.y, temperature.boundsInRoot.height / 4)
        assertEquals("Sonnig", symbol.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        compose.onAllNodesWithText("Sonnig", useUnmergedTree = true).assertCountEquals(1)
        // the same data at night: the moon, "Klar"
        at.longValue = night
        compose.waitForIdle()
        assertEquals("Klar", compose.onNodeWithTag("place-symbol-berlin", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        compose.onAllNodesWithText("Klar", useUnmergedTree = true).assertCountEquals(1)
    }

    private fun places(
        saved: List<Place>, onSetModel: (Place, ForecastModel?) -> Unit = { _, _ -> },
        onDuplicate: (Place) -> Place = { dev.nimbus.weather.ui.places.PlaceTwins.copyOf(it, saved) },
        onRemove: (Place) -> Unit = {},
    ) = compose.setContent {
        PlacesScreen(
            UiState(initialized = true, savedPlaces = saved, selectedPlaceId = saved.first().id), search = { emptyList() },
            onAdd = {}, onRemove = onRemove, onReorder = {}, onSetModel = onSetModel, onDuplicate = onDuplicate,
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

    /** A saved place may have a model of its own: in the edit mode under its name, tapped – the choice. */
    @Test fun aPlaceGetsAModelOfItsOwn() {
        var chosen: Pair<String, ForecastModel?>? = null
        places(listOf(berlin), onSetModel = { p, m -> chosen = p.id to m })
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        compose.onNodeWithText("Modell: Automatisch (App-Einstellung)").performClick()
        compose.onNodeWithText("Eigenes Modell für diesen Ort").performClick()
        compose.onNodeWithText("MET Nordic (1 km)").performScrollTo().performClick()
        assertEquals("berlin" to ForecastModel.MET_NORWAY, chosen)
    }

    /**
     * The model line whole in the edit mode, where it wraps ("Modell: Automatisch (App-Einstellung)"
     * on a narrow phone, with the system's larger font): the row grows with it, nothing of its
     * second line cut off.
     */
    @Test @Config(qualifiers = "de-w360dp-h800dp-xxhdpi", fontScale = 1.3f) @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun theModelLineWholeWhereItWraps() {
        places(listOf(berlin))
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        val line = compose.onNodeWithTag("model-berlin")
        val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        line.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals("wraps at this width", 2, layout.single().lineCount)
        assertFalse("its second line cut off", layout.single().didOverflowHeight)
        // and the row holds it: the line's bottom within the row's
        val row = compose.onNodeWithTag("edit-row-berlin").fetchSemanticsNode().boundsInRoot
        val text = line.fetchSemanticsNode().boundsInRoot
        assertTrue("$text in $row", text.bottom <= row.bottom && text.height >= layout.single().size.height - 1)
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
     * First the app's setting or a model of its own, the models only beneath the latter – switching
     * to it alone changes nothing.
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

    /** The copy's sheet closed without a model – the copy goes; with one, it stays. */
    @Test fun aCopyClosedWithoutAModelIsDiscarded() {
        val removed = mutableListOf<String>()
        var chosen: Pair<String, ForecastModel?>? = null
        places(listOf(berlin), onSetModel = { p, m -> chosen = p.id to m }, onRemove = { removed += it.id })
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        compose.onNodeWithTag("duplicate-berlin").performClick()
        compose.onNodeWithTag("model-sheet").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf("berlin#2"), removed)
        assertEquals(null, chosen)
        // the place itself: its sheet closed without a choice changes nothing
        compose.onNodeWithText("Modell: Automatisch (App-Einstellung)").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf("berlin#2"), removed)
    }

    /**
     * The list unfolds downwards from the divider: while it unfolds the models stand where they end
     * up – beneath the choice, nothing sliding up from below.
     */
    @Test fun theModelsUnfoldDownwardsFromTheLine() {
        places(listOf(berlin))
        compose.onNodeWithText("Berlin").performTouchInput { longClick() }
        compose.onNodeWithText("Modell: Automatisch (App-Einstellung)").performClick()
        compose.waitForIdle()
        // the first model's top below the choice "Eigenes Modell" – the sheet itself grows and moves
        fun gap(): Float? {
            val own = compose.onNodeWithText("Eigenes Modell für diesen Ort").fetchSemanticsNode().boundsInRoot
            val model = compose.onAllNodes(androidx.compose.ui.test.hasText("Automatisch (beste Auswahl)"), useUnmergedTree = true)
                .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.takeIf { it.height > 0f } ?: return null
            return model.top - own.top
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Eigenes Modell für diesen Ort").performClick()
        // every 32 ms of the unfolding, from its first frame on
        val gaps = (1..10).mapNotNull { compose.mainClock.advanceTimeBy(32); gap() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val end = gap()!!
        assertTrue("unfolding not seen: $gaps", gaps.size >= 5)
        assertTrue("models above the choice while unfolding: $gaps", gaps.all { it > 0f })
        assertTrue("models moving while unfolding: $gaps → $end", gaps.all { kotlin.math.abs(it - end) < 2f })
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

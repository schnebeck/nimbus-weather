/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PageLayoutTest.kt
 * The weather page on small phones and with a large system font: the header never runs into the
 * first card, its max/min are never cut off at the edge.
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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.main.LocalContentWidth
import dev.nimbus.weather.ui.main.WeatherPage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PageLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val now = System.currentTimeMillis() / h * h + 20 * 60_000L

    private fun data(): WeatherData {
        val start = now / h * h - 2 * h
        val hourly = (0..72).map { k -> HourlyPoint(start + k * h, 14.0, condition = Condition.CLOUDY, isDay = true, precipitation = 0.0, precipitationProbability = 10.0) }
        val daily = (-1..3).map { DailyPoint(start + it * 24 * h, Condition.CLOUDY, 20.0, 9.0, precipitationSum = 0.0) }
        val cur = CurrentWeather(
            now, 11.0, 13.0, Condition.FOG, true, 97.0, 10.0, 1032.0, 3.0, 6.0, 40.0, 100.0, 12000.0, 0.0, 0.0,
            stationName = "Hannover-Herrenhause", stationDistanceKm = 5.0,          // as the DWD writes it
        )
        return WeatherData(Place("p", "Garbsen", latitude = 52.42, longitude = 9.60), "Europe/Berlin", 7200, cur, hourly, daily, sources = emptyList(), fetchedAt = now)
    }

    /** The page at [widthDp] dp wide and [fontScale]: the header's bottom above the first card, max/min inside the screen. */
    /**
     * [heightDp]: tall enough that the list composes all cards (taller than the window, the page
     * is centred on it – positions are compared with each other only) – or the window's height
     * ([cards] false: the header and the first card only).
     */
    private fun check(widthDp: Int, fontScale: Float, heightDp: Int = 4000, cards: Boolean = true): Float {
        val settings = Settings(hiddenCards = setOf(WeatherCard.RADAR, WeatherCard.MODELS))
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalContentWidth provides widthDp.dp) {
                // tall enough that the list composes the cards down to the 10-day forecast at 200 %
                Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                    WeatherPage(Place("p", "Garbsen", latitude = 52.42, longitude = 9.60), PlaceState(data()), settings, null, false, {}, {}, {})
                }
            }
        }
        compose.waitForIdle()
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val station = compose.onAllNodesWithText("Hannover-Herrenhause", substring = true).fetchSemanticsNodes().first().boundsInRoot
        // the card below the header (the list may hold one more, not placed)
        val firstCard = compose.onAllNodesWithText(ctx.getString(R.string.precip_title), ignoreCase = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.width > 0f && it.top > station.top }.minBy { it.top }
        val what = "$widthDp dp, font ×$fontScale"
        assertTrue("$what: station line (bottom ${station.bottom}) runs into the first card (top ${firstCard.top})", station.bottom < firstCard.top)
        // the day's max ("20 °C") and min ("9 °C"): complete, inside the screen
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (t in listOf("20\u202F°", "9\u202F°")) {
            val node = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().firstOrNull { it.boundsInRoot.top < station.top }
            assertTrue("$what: \"$t\" missing in the header", node != null)
            assertTrue("$what: \"$t\" cut off at the edge (${node!!.boundsInRoot.right} > ${root.right})", node.boundsInRoot.right <= root.right)
            // and complete: on one line (squeezed it wraps, and its fixed-height row shows "2" of "20 °C")
            val results = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.first()
            assertTrue("$what: \"${layout.layoutInput.text}\" squeezed onto ${layout.lineCount} lines", layout.lineCount == 1)
        }
        // the hourly row's "Jetzt" and the 10-day card's "Heute" and temperatures: not cut ("Jet", "Heu", "20")
        if (cards) for (t in listOf(ctx.getString(R.string.now), ctx.getString(R.string.today), "20°")) {
            // the ones on screen (collapsed content has no size)
            val nodes = compose.onAllNodesWithText(t).fetchSemanticsNodes().filter { it.boundsInRoot.width > 0f }
            assertTrue("$what: \"$t\" missing", nodes.isNotEmpty())
            for (node in nodes) {
                val results = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                val layout = results.first()
                // one line holding the whole text (maxLines = 1 hides the rest: "Heu"), no wider than
                // the element (softWrap = false clips a wider line at its edge)
                val text = layout.layoutInput.text.text
                val lineWidth = layout.getLineRight(0) - layout.getLineLeft(0)
                assertTrue(
                    "$what: \"$t\" cut off (line 0 ends at ${layout.getLineEnd(0)} of ${text.length}, ${lineWidth} px in ${node.boundsInRoot.width})",
                    layout.lineCount == 1 && layout.getLineEnd(0) >= text.trimEnd().length && lineWidth <= node.boundsInRoot.width + 1f,
                )
            }
        }
        return firstCard.top
    }

    /**
     * A phone held sideways (411 dp high): the header as a pane of its own on the left, the cards
     * beside it in the full height – with the header above them they had a strip of sky. The
     * camera's cut-out at the left: nothing of the page under it (the 10-day card's sun symbol
     * lay in it).
     */
    @Test @Config(qualifiers = "de-w914dp-h411dp-xxhdpi") fun phoneSideways() {
        val settings = Settings(hiddenCards = setOf(WeatherCard.RADAR, WeatherCard.MODELS))
        val cut = 136
        compose.setContent {
            CompositionLocalProvider(LocalContentWidth provides 914.dp) {
                WeatherPage(Place("p", "Garbsen", latitude = 52.42, longitude = 9.60), PlaceState(data()), settings, null, false, {}, {}, {})
            }
        }
        compose.runOnUiThread {
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(), androidx.core.graphics.Insets.of(cut, 0, 0, 0))
                .build()
            val root = compose.onRoot().fetchSemanticsNode().root!!.let { (it as? android.view.View) } ?: error("no view")
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root.rootView, insets)
        }
        compose.waitForIdle()
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val px = ctx.resources.displayMetrics.density
        val station = compose.onAllNodesWithText("Hannover-Herrenhause", substring = true).fetchSemanticsNodes().first().boundsInRoot
        val name = compose.onAllNodesWithText("Garbsen").fetchSemanticsNodes().first().boundsInRoot
        val titles = compose.onAllNodesWithText(ctx.getString(R.string.precip_title), ignoreCase = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.width > 0f }
        val card = titles.minBy { it.top }
        assertTrue("header beside the cards: station line ends at ${station.right}, the card begins at ${card.left}", station.right < card.left)
        assertTrue("first card at ${card.top / px} dp of 411 – below the top bar, not below the header", card.top / px < 411 * 0.3f)
        assertTrue("header in the camera's cut-out (name ${name.left}, station line ${station.left} < $cut)", name.left >= cut && station.left >= cut)
        // the header stays where it is when the cards scroll
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(name, compose.onAllNodesWithText("Garbsen").fetchSemanticsNodes().first().boundsInRoot)
    }

    /** "My location" in the header: the status dot behind the name at the capitals' height, apart from the pin. */
    @Test fun theDotOfMyLocationInTheHeader() {
        val settings = Settings(hiddenCards = setOf(WeatherCard.RADAR, WeatherCard.MODELS))
        val here = Place("current-location", "Garbsen", latitude = 52.42, longitude = 9.60, isCurrentLocation = true)
        compose.setContent {
            CompositionLocalProvider(LocalContentWidth provides 411.dp) {
                WeatherPage(
                    here, PlaceState(data().copy(place = here)), settings, null, false, {}, {}, {},
                    location = dev.nimbus.weather.ui.main.LocationMark(current = true, searching = false, off = false),
                )
            }
        }
        compose.waitForIdle()
        compose.assertDotBesidePin("Garbsen", 32f)
    }

    /** Double-tapping the sky above the cards switches full screen; scrolling stays. */
    @Test fun doubleTapOnTheSkySwitchesFullScreen() {
        var settings = Settings(hiddenCards = setOf(WeatherCard.RADAR, WeatherCard.MODELS))
        compose.setContent {
            CompositionLocalProvider(
                LocalContentWidth provides 411.dp,
                dev.nimbus.weather.ui.main.LocalSettingsUpdater provides { f -> settings = f(settings) },
            ) {
                WeatherPage(Place("p", "Garbsen", latitude = 52.42, longitude = 9.60), PlaceState(data()), settings, null, false, {}, {}, {})
            }
        }
        compose.waitForIdle()
        // the place name in the header: the sky above the cards
        val name = compose.onAllNodesWithText("Garbsen").fetchSemanticsNodes().first().boundsInRoot
        compose.onRoot().performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(name.center.x, name.bottom + 40f)) }
        compose.waitForIdle()
        assertTrue("full screen not switched on", settings.fullscreen)
        compose.onRoot().performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(name.center.x, name.bottom + 40f)) }
        compose.waitForIdle()
        assertTrue("full screen not switched off again", !settings.fullscreen)
    }

    @Test fun phone() { check(411, 1f) }
    @Test fun phoneLargeFont() { check(411, 1.3f) }
    @Test fun phoneLargestFont() { check(411, 2f) }
    @Test fun narrowPhone() { check(320, 1f) }
    @Test fun narrowPhoneLargeFont() { check(320, 1.3f) }
}

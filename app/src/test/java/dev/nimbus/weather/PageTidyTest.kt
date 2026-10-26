/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PageTidyTest.kt
 * A shorter weather page, done where the cards are: the views by importance, the long press on a
 * card (fold, hide, arrange), arranging the cards and the small tiles, the note at the page's end,
 * and the settings with what is changed most at the top.
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.CurrentWeather
import dev.nimbus.weather.data.model.DailyPoint
import dev.nimbus.weather.data.model.HourlyPoint
import dev.nimbus.weather.data.model.PageView
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherCard
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.main.LocalSettingsUpdater
import dev.nimbus.weather.ui.main.WeatherPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h900dp-xxhdpi", application = android.app.Application::class)
class PageTidyTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val now = System.currentTimeMillis() / h * h + 20 * 60_000L
    private var settings by mutableStateOf(Settings())
    private fun str(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun data(): WeatherData {
        val start = now / h * h - 2 * h
        val hourly = (0..72).map { k -> HourlyPoint(start + k * h, 14.0, condition = Condition.CLOUDY, isDay = true, precipitation = 0.0, precipitationProbability = 10.0) }
        val daily = (-1..9).map { DailyPoint(start + it * 24 * h, Condition.CLOUDY, 20.0, 9.0, precipitationSum = 0.0) }
        val cur = CurrentWeather(now, 11.0, 13.0, Condition.CLOUDY, true, 97.0, 10.0, 1032.0, 3.0, 6.0, 40.0, 100.0, 12000.0, 0.0, 0.0)
        return WeatherData(Place("p", "Hannover", latitude = 52.3759, longitude = 9.7320), "Europe/Berlin", 7200, cur, hourly, daily, sources = emptyList(), fetchedAt = now)
    }

    /**
     * The weather page, its settings changed by the page as by the app. Shown without the radar's
     * preview and the model comparison (they load from the network), as the page's other tests.
     */
    private fun page() {
        val d = data()
        compose.setContent {
            CompositionLocalProvider(LocalSettingsUpdater provides { f: (Settings) -> Settings -> settings = f(settings) }) {
                Box(Modifier.requiredSize(411.dp, 900.dp)) {
                    WeatherPage(d.place, PlaceState(d), settings.withCard(WeatherCard.RADAR, false).withCard(WeatherCard.MODELS, false), null, true, {}, {}, {})
                }
            }
        }
        compose.waitForIdle()
    }

    // the page's list (the hourly row scrolls too, sideways)
    private fun scrollTo(text: String) = compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))

    /** Hold a card's title: its menu opens. */
    private fun hold(title: String) {
        scrollTo(title)
        compose.onNodeWithText(title).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    // ---- the views ------------------------------------------------------------------------------

    /**
     * By importance, not by order: the core (precipitation, hours, 10 days, radar), the pollen –
     * for many a must –, the small tiles, sky and air, the special cards.
     */
    @Test fun theViewsByImportance() {
        val core = WeatherCard.CORE
        assertEquals(PageView.FULL, Settings().view())
        val compact = Settings().withView(PageView.COMPACT)
        (core + WeatherCard.POLLEN).forEach { assertTrue("$it on in compact", compact.shows(it)) }
        (WeatherCard.SKY + WeatherCard.SPECIAL + WeatherCard.TILES).forEach { assertFalse("$it off in compact", compact.shows(it)) }
        assertTrue(compact.foldedCards.isEmpty())
        val normal = Settings().withView(PageView.NORMAL)
        WeatherCard.entries.filter { it !in WeatherCard.OPT_IN }.forEach { assertTrue("$it on in normal", normal.shows(it)) }
        assertEquals(WeatherCard.SKY + WeatherCard.SPECIAL, normal.foldedCards)
        assertFalse("the pollen open", WeatherCard.POLLEN in normal.foldedCards)
        val full = compact.withView(PageView.FULL)
        WeatherCard.entries.filter { it !in WeatherCard.OPT_IN }.forEach { assertTrue("$it on in full", full.shows(it)) }
        assertTrue(full.foldedCards.isEmpty())
        // each view known again; the order stays as it was
        PageView.entries.forEach { assertEquals(it, Settings().withView(it).view()) }
        val own = Settings(cardOrder = listOf(WeatherCard.RADAR, WeatherCard.POLLEN))
        assertEquals(own.orderedCards(), own.withView(PageView.COMPACT).orderedCards())
        // by hand: none of them
        assertNull(Settings().withCard(WeatherCard.MOON, false).view())
    }

    /** A new order of the cards on the page: the cards not on it (hidden, without data here) keep their places. */
    @Test fun theOthersKeepTheirPlaces() {
        val s = Settings(hiddenCards = setOf(WeatherCard.MOON))
        val shown = s.orderedCards().filter { s.shows(it) && it != WeatherCard.GAUGES }
        val moved = shown.toMutableList().apply { remove(WeatherCard.SUN); add(0, WeatherCard.SUN) }
        val after = s.withPageOrder(moved).orderedCards()
        assertEquals(WeatherCard.SUN, after.first())
        assertEquals(s.orderedCards().indexOf(WeatherCard.MOON), after.indexOf(WeatherCard.MOON))
        assertEquals(s.orderedCards().indexOf(WeatherCard.GAUGES), after.indexOf(WeatherCard.GAUGES))
    }

    // ---- the long press -------------------------------------------------------------------------

    /** Held, a card offers to fold, hide and arrange; hidden it goes, "Undo" brings it back. */
    @Test fun holdToFoldHideArrange() {
        page()
        val moon = str(R.string.moon).uppercase()
        hold(moon)
        compose.onNodeWithText(str(R.string.card_menu_fold)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.card_menu_hide)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.card_menu_arrange)).assertIsDisplayed()
        // folding from the menu
        compose.onNodeWithText(str(R.string.card_menu_fold)).performClick()
        compose.waitForIdle()
        assertTrue(WeatherCard.MOON in settings.foldedCards)
        hold(moon)
        compose.onNodeWithText(str(R.string.card_menu_unfold)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.card_menu_hide)).performClick()
        compose.waitForIdle()
        assertFalse(settings.shows(WeatherCard.MOON))
        compose.onNodeWithText(str(R.string.cards_undo_hidden, str(R.string.moon))).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.undo)).performClick()
        compose.waitForIdle()
        assertTrue(settings.shows(WeatherCard.MOON))
    }

    /**
     * "Arrange": every card down to its title line, the held one moves with the finger; let go,
     * the page is back with the new order and "Position changed · Undo".
     */
    @Test fun arrangeACard() {
        page()
        hold(str(R.string.moon).uppercase())
        compose.onNodeWithText(str(R.string.card_menu_arrange)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("arrange").assertIsDisplayed()
        // the whole page on the screen: the first card and the last in view
        compose.onNodeWithTag("arrange-precipitation").assertIsDisplayed()
        compose.onNodeWithTag("arrange-moon").assertIsDisplayed()
        val before = settings.orderedCards()
        assertTrue(before.indexOf(WeatherCard.SUN) < before.indexOf(WeatherCard.MOON))
        compose.onNodeWithTag("arrange-moon").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -30f)) }
            up()
        }
        compose.waitForIdle()
        val after = settings.orderedCards()
        assertTrue("the moon above the sun: $after", after.indexOf(WeatherCard.MOON) < after.indexOf(WeatherCard.SUN))
        compose.onNodeWithTag("arrange").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.cards_undo_order)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.undo)).performClick()
        compose.waitForIdle()
        assertEquals(before, settings.orderedCards())
    }

    /** The snackbar wiped aside: gone at once, the card stays hidden. */
    @Test fun theSnackbarWipedAway() {
        page()
        hold(str(R.string.moon).uppercase())
        compose.onNodeWithText(str(R.string.card_menu_hide)).performClick()
        compose.waitForIdle()
        val message = str(R.string.cards_undo_hidden, str(R.string.moon))
        compose.onNodeWithText(message).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText(message).assertDoesNotExist()
        assertFalse(settings.shows(WeatherCard.MOON))
        // to the other side as well
        hold(str(R.string.sun).uppercase())
        compose.onNodeWithText(str(R.string.card_menu_hide)).performClick()
        compose.waitForIdle()
        val sun = str(R.string.cards_undo_hidden, str(R.string.sun))
        compose.onNodeWithText(sun).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(sun).assertDoesNotExist()
        assertFalse(settings.shows(WeatherCard.SUN))
    }

    /** "Cancel", a tap beside the held card or Back: arranging ends, everything as it was. */
    @Test fun arrangingLeftAsItWas() {
        page()
        fun arrange(title: String) {
            hold(title)
            compose.onNodeWithText(str(R.string.card_menu_arrange)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("arrange").assertIsDisplayed()
        }
        arrange(str(R.string.moon).uppercase())
        compose.onNodeWithText(str(R.string.arrange_cancel)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("arrange").assertDoesNotExist()
        arrange(str(R.string.moon).uppercase())
        compose.onNodeWithTag("arrange-hourly").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("arrange").assertDoesNotExist()
        // the small tiles: "Cancel" too, a tap on another tile as well; the other cards are not there
        // meanwhile (nothing else to set off)
        arrange(str(R.string.wind).uppercase())
        compose.onNodeWithText(str(R.string.sun).uppercase()).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.arrange_cancel)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("arrange").assertDoesNotExist()
        arrange(str(R.string.wind).uppercase())
        compose.onNodeWithTag("arrange-uv_index").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("arrange").assertDoesNotExist()
        assertTrue(settings.cardOrder.isEmpty() && settings.tileOrder.isEmpty())
    }

    /** The small tiles alike, within their block: no folding there, arranging among themselves. */
    @Test fun arrangeASmallTile() {
        page()
        hold(str(R.string.wind).uppercase())
        compose.onNodeWithText(str(R.string.card_menu_fold)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.card_menu_arrange)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("arrange").assertIsDisplayed()
        // only the tiles to arrange, all of them in view
        compose.onNodeWithTag("arrange-moon").assertDoesNotExist()
        compose.onNodeWithTag("arrange-feels_like").assertIsDisplayed()
        compose.onNodeWithTag("arrange-pressure").assertIsDisplayed()
        compose.onNodeWithTag("arrange-wind").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -30f)) }
            up()
        }
        compose.waitForIdle()
        val tiles = settings.orderedTiles()
        assertTrue("the wind earlier: $tiles", tiles.indexOf(WeatherCard.WIND) < WeatherCard.DEFAULT_TILES.indexOf(WeatherCard.WIND))
        assertTrue(settings.cardOrder.isEmpty())
    }

    // ---- the note at the end --------------------------------------------------------------------

    /** At the page's end, once: a view chosen there, the note is gone for good. */
    @Test fun theNoteAtTheEnd() {
        page()
        scrollTo(str(R.string.view_hint_title))
        // a note, no data: no status dot
        assertNull(dev.nimbus.weather.ui.main.cardParts("view-hint"))
        // its choices in view too (the title alone may stand at the bottom edge, as long as the page above is)
        scrollTo(str(R.string.view_compact))
        compose.onNodeWithText(str(R.string.view_compact)).performClick()
        compose.waitForIdle()
        assertEquals(PageView.COMPACT, settings.view())
        assertTrue(settings.viewHintDone)
        compose.onNodeWithText(str(R.string.view_hint_title)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.cards_undo_view, str(R.string.view_compact))).assertIsDisplayed()
        // undone: the cards as they were, the note stays away
        compose.onNodeWithText(str(R.string.undo)).performClick()
        compose.waitForIdle()
        assertTrue(settings.shows(WeatherCard.MOON) && settings.shows(WeatherCard.TILES) && settings.foldedCards.isEmpty())
        assertTrue(settings.viewHintDone)
    }

    /**
     * For those who used the app before too (settings of their own, never answered): the note is
     * there, it goes only by an answer – "all fine as it is" leaves the page as it was.
     */
    @Test fun theNoteUntilAnswered() {
        settings = Settings(cardOrder = listOf(WeatherCard.SUN), foldedCards = setOf(WeatherCard.DAILY))
        val before = settings
        page()
        scrollTo(str(R.string.view_hint_title))
        // the answer below the note in view as well (a tall card above it leaves it below the screen)
        scrollTo(str(R.string.view_hint_keep))
        compose.onNodeWithText(str(R.string.view_hint_keep)).performClick()
        compose.waitForIdle()
        assertTrue(settings.viewHintDone)
        assertEquals(before.copy(viewHintDone = true), settings)
        compose.onNodeWithText(str(R.string.view_hint_title)).assertDoesNotExist()
    }

    // ---- the settings ---------------------------------------------------------------------------

    /**
     * What is changed most at the top; the pollen before the bathing waters; the forecast model
     * near the end in one line, all models on a tap. The views there too.
     */
    @Test fun theSettingsInTheirNewOrder() {
        // tall enough for the whole list: every section composed, their places compared
        compose.setContent {
            Box(Modifier.requiredSize(411.dp, 9000.dp)) {
                dev.nimbus.weather.ui.settings.SettingsScreen(settings, { f -> settings = f(settings) }, {}, {})
            }
        }
        val titles = listOf(
            R.string.settings_units, R.string.settings_cards, R.string.settings_radar, R.string.settings_display,
            R.string.pollen_forecast, R.string.bathing_title, R.string.settings_forecast_source, R.string.settings_language,
        ).map { str(it).uppercase() }
        val tops = titles.map { t -> compose.onNodeWithText(t).fetchSemanticsNode().positionInRoot.y }
        assertEquals("sections in order: $titles", tops.sorted(), tops)
        // the model: the chosen one, the others on a tap
        scrollTo(str(R.string.model_best_match))
        compose.onNodeWithText(str(R.string.model_ecmwf)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.model_best_match)).performClick()
        compose.waitForIdle()
        scrollTo(str(R.string.model_ecmwf))
        compose.onNodeWithText(str(R.string.model_ecmwf)).performClick()
        compose.waitForIdle()
        assertEquals(dev.nimbus.weather.data.model.ForecastModel.ECMWF, settings.model)
        // the views
        scrollTo(str(R.string.view_normal))
        compose.onNodeWithText(str(R.string.view_normal)).performClick()
        compose.waitForIdle()
        assertEquals(PageView.NORMAL, settings.view())
    }
}

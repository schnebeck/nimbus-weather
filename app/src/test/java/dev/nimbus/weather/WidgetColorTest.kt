/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WidgetColorTest.kt
 * The widget's background colour, chosen in its setup, and the writing coloured to read on it.
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

import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.widget.WIDGET_COLORS
import dev.nimbus.weather.widget.WidgetConfig
import dev.nimbus.weather.widget.WidgetConfigs
import dev.nimbus.weather.widget.WidgetInk
import dev.nimbus.weather.widget.WidgetSetup
import dev.nimbus.weather.widget.WidgetSize
import dev.nimbus.weather.widget.WidgetTint
import dev.nimbus.weather.widget.WidgetViews
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 30, 35], application = android.app.Application::class, qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class WidgetColorTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val darkWall = 0xFF202830.toInt()

    @After fun tearDown() { WidgetTint.wallpaper = { null } }

    /** The widget as the launcher shows it with [config], the wallpaper behind it [wall]. */
    private fun shown(config: WidgetConfig, wall: Int? = null): View {
        WidgetTint.wallpaper = { wall }
        val views = WidgetViews.build(app, 1, config, WidgetSize(320, 200), null, null, TemperatureUnit.CELSIUS, 1_790_850_000_000L)
        return views.apply(app, FrameLayout(app))
    }

    private fun View.text(name: String) = findViewById<TextView>(app.resources.getIdentifier(name, "id", app.packageName)).currentTextColor

    /**
     * Every view the layout gives a colour of the writing is listed by that colour – a view left
     * out would keep the system's ink on a chosen colour.
     */
    @Test fun everyPieceOfWritingIsColoured() {
        val xml = java.io.File("src/main/res/layout/widget_nimbus.xml").readText()
        val coloured = Regex("""<(\w+)((?:[^>"]|"[^"]*")*)>""").findAll(xml).mapNotNull { m ->
            val attrs = m.groupValues[2]
            val colour = Regex("""android:(?:textColor|tint)="@color/(widget_\w+)"""").find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            val id = Regex("""android:id="@\+id/(\w+)"""").find(attrs)?.groupValues?.get(1)
            assertNotNull("a ${m.groupValues[1]} in $colour without an id", id)
            id!! to colour
        }.toMap()
        fun names(ids: List<Int>) = ids.map { app.resources.getResourceEntryName(it) }.toSet()
        val listed = names(WidgetTint.TEXT + WidgetTint.TEXT_ICONS).associateWith { "widget_text" } +
            names(WidgetTint.DIM + WidgetTint.DIM_ICONS).associateWith { "widget_text_dim" } +
            names(WidgetTint.CHANCE).associateWith { "widget_chance" }
        assertEquals(coloured, listed)
    }

    @Test fun aChosenColourWithItsInk() {
        val white = shown(WidgetConfig(background = 0xFFFFFFFF.toInt(), opacity = 1f))
        assertEquals(WidgetInk.ON_LIGHT.text, white.text("widget_place"))
        assertEquals(WidgetInk.ON_LIGHT.dim, white.text("widget_as_of"))
        val black = shown(WidgetConfig(background = 0xFF000000.toInt(), opacity = 1f))
        assertEquals(WidgetInk.ON_DARK.text, black.text("widget_place"))
        assertEquals(WidgetInk.ON_DARK.dim, black.text("widget_as_of"))
        // the background takes the colour (the filter's colour can be read from Android 10 on)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val filter = black.findViewById<ImageView>(R.id.widget_bg).colorFilter
            assertEquals(PorterDuffColorFilter(0xFF000000.toInt(), android.graphics.PorterDuff.Mode.SRC_ATOP), filter)
        }
    }

    /** A light colour, nearly clear over a dark wallpaper: light writing – the wallpaper is what shows. */
    @Test fun aClearBackgroundReadsOnTheWallpaper() {
        val v = shown(WidgetConfig(background = 0xFFFFFFFF.toInt(), opacity = 0.1f), wall = darkWall)
        assertEquals(WidgetInk.ON_DARK.text, v.text("widget_place"))
    }

    /** The system's colour: its own ink while it covers the wallpaper, light writing when clear over a dark one. */
    @Test fun theSystemsColourOverTheWallpaper() {
        val own = app.getColor(R.color.widget_text)
        assertEquals(own, shown(WidgetConfig(opacity = 0.9f), wall = darkWall).text("widget_place"))
        assertEquals(own, shown(WidgetConfig(opacity = 0.1f), wall = null).text("widget_place"))
        assertEquals(WidgetInk.ON_DARK.text, shown(WidgetConfig(opacity = 0.1f), wall = darkWall).text("widget_place"))
    }

    /** The setup: a colour at hand, then one picked freely – red, from the hue bar and the field. */
    @Config(sdk = [35]) @Test fun theSetupKeepsTheColour() {
        val configs = WidgetConfigs(app)
        var done = 0
        compose.setContent { WidgetSetup(7, Store(app), configs) { done++ } }
        compose.waitForIdle()
        fun tap(tag: String) {
            compose.onNodeWithTag("widget-setup").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
            compose.waitForIdle()
        }
        tap("widget-color-#2F64B5")
        tap("widget-done")
        compose.waitUntil(5_000) { done == 1 }
        assertEquals(0xFF2F64B5.toInt(), configs.get(7)!!.background)

        tap("widget-color-custom")
        compose.onNodeWithTag("color-hue").performTouchInput { click(Offset(1f, centerY)) }
        compose.onNodeWithTag("color-field").performTouchInput { click(Offset(width - 12f, 12f)) }
        compose.onNodeWithTag("color-ok").performClick()
        compose.waitForIdle()
        tap("widget-done")
        compose.waitUntil(5_000) { done == 2 }
        val red = configs.get(7)!!.background!!
        assertFalse(red in WIDGET_COLORS)
        assertTrue("%08X".format(red), (red ushr 16 and 0xFF) > 0xF0 && (red ushr 8 and 0xFF) < 0x10 && (red and 0xFF) < 0x10)
        // and back to the system's colour
        tap("widget-color-system")
        tap("widget-done")
        compose.waitUntil(5_000) { done == 3 }
        assertEquals(null, configs.get(7)!!.background)
    }
}

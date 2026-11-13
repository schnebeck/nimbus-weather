/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WidgetInkTest.kt
 * The widget's writing light or dark by what lies behind it: the background over the wallpaper.
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

import dev.nimbus.weather.widget.WIDGET_COLORS
import dev.nimbus.weather.widget.WidgetInk
import dev.nimbus.weather.widget.hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetInkTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val dayGrey = 0xFFF4F3F8.toInt()
    private val nightGrey = 0xFF1D1D22.toInt()

    @Test fun darkOnLightAndLightOnDark() {
        assertEquals(WidgetInk.ON_LIGHT, WidgetInk.of(white))
        assertEquals(WidgetInk.ON_DARK, WidgetInk.of(black))
        // amber: dark writing, the chance's blue would not read on it – written as the text
        assertEquals(WidgetInk.ON_LIGHT.copy(chance = WidgetInk.ON_LIGHT.text), WidgetInk.of(0xFFF9A825.toInt()))
        // teal: neither ink reads well enough – white
        assertEquals(0xFFFFFFFF.toInt(), WidgetInk.of(0xFF00796B.toInt()).text)
        assertEquals(WidgetInk.ON_DARK.text, WidgetInk.of(0xFF1E2A44.toInt()).text)   // night blue
    }

    /** Every colour at hand, opaque: the writing reads (4.5:1), the dimmer lines and the chance of rain too (3:1). */
    @Test fun everyColourAtHandReads() {
        for (c in WIDGET_COLORS) {
            val ink = WidgetInk.of(c)
            assertTrue("${hex(c)}: text", WidgetInk.contrast(ink.text, c) >= 4.5)
            assertTrue("${hex(c)}: dim", WidgetInk.contrast(ink.dim, c) >= 3.0)
            assertTrue("${hex(c)}: chance", WidgetInk.contrast(ink.chance, c) >= 3.0)
        }
    }

    /** Any colour picked freely: the better of the two inks reads at least 4.5:1. */
    @Test fun anyColourReads() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            assertTrue(hex(c), WidgetInk.contrast(WidgetInk.of(c).text, c) >= 4.5)
        }
    }

    /**
     * The more transparent the background, the more the wallpaper decides: the system's light
     * colour at 90 % over a dark wallpaper keeps dark writing, at 10 % it turns light.
     */
    @Test fun theWallpaperShowsThroughATransparentBackground() {
        val darkWall = 0xFF202830.toInt()
        assertEquals(WidgetInk.ON_LIGHT, WidgetInk.of(WidgetInk.behind(dayGrey, 0.9f, darkWall)))
        assertEquals(WidgetInk.ON_DARK, WidgetInk.of(WidgetInk.behind(dayGrey, 0.1f, darkWall)))
        // and the night's dark colour over a light wallpaper: dark writing
        val lightWall = 0xFFE8E4DA.toInt()
        assertEquals(WidgetInk.ON_DARK, WidgetInk.of(WidgetInk.behind(nightGrey, 0.9f, lightWall)))
        assertEquals(WidgetInk.ON_LIGHT, WidgetInk.of(WidgetInk.behind(nightGrey, 0f, lightWall)))
        // the wallpaper not known: the background alone
        assertEquals(WidgetInk.ON_LIGHT, WidgetInk.of(WidgetInk.behind(dayGrey, 0f, null)))
    }

    @Test fun overMixesChannelByChannel() {
        assertEquals(0xFF808080.toInt(), WidgetInk.over(white, 0.5f, black))
        assertEquals(white, WidgetInk.over(white, 1f, black))
        assertEquals(black, WidgetInk.over(white, 0f, black))
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SkyInItsPageTest.kt
 * A page's sky stays on its page: clouds and fog drifting in from beside it are not drawn over
 * the page next to it while paging.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.background.WeatherBackground
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SkyInItsPageTest {
    @get:Rule val compose = createComposeRule()

    private val page = Color(0xFF102030)

    /**
     * Two pages side by side as the pager lays them out while paging: the one on the left in a
     * plain colour, the sky of the one on the right drawn after it – none of it on the left page.
     */
    private fun leaked(condition: Condition): Int {
        compose.setContent {
            Row {
                Box(Modifier.size(150.dp, 300.dp).background(page))
                WeatherBackground(SkyScene(condition, 0.6f, 0f, 0.9f), animate = false, modifier = Modifier.size(150.dp, 300.dp))
            }
        }
        compose.waitForIdle()
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        val left = shot.width / 2
        return (0 until left).sumOf { x -> (0 until shot.height).count { y -> shot.getPixel(x, y) != page.toArgb() } }
    }

    @Test fun theFogStaysOnItsPage() = assertEquals("pixels of the page beside it painted", 0, leaked(Condition.FOG))

    @Test fun theCloudsStayOnTheirPage() = assertEquals("pixels of the page beside it painted", 0, leaked(Condition.CLOUDY))
}

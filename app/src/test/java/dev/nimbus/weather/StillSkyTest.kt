/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/StillSkyTest.kt
 * No animation, no moving pictures: the sky stands without rain, snow and lightning.
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

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.background.WeatherBackground
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * „Wäre es evtl besser den Animationslayer grafisch auszublenden? So kann ein Starkregen im
 * hintergund eingefroren sein - keine Animation sollte auch keine Animationsgrafiken bedeuten“.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class StillSkyTest {
    @get:Rule val compose = createComposeRule()

    private val storm = SkyScene(Condition.HEAVY_RAIN, 1f, 0f, 0.9f)

    /** The [storm]'s sky – standing (not the page shown) – with moving pictures wanted, then not. */
    private fun skies(): Pair<Bitmap, Bitmap> {
        var motion by mutableStateOf(true)
        compose.setContent { WeatherBackground(storm, animate = false, modifier = Modifier.size(200.dp, 300.dp), motion = motion) }
        compose.waitForIdle()
        val wanted = compose.onRoot().captureToImage().asAndroidBitmap()
        motion = false
        compose.waitForIdle()
        return wanted to compose.onRoot().captureToImage().asAndroidBitmap()
    }

    private fun differing(a: Bitmap, b: Bitmap): Int =
        (0 until minOf(a.width, b.width)).sumOf { x -> (0 until minOf(a.height, b.height)).count { y -> a.getPixel(x, y) != b.getPixel(x, y) } }

    @Test fun animationOffNoRain() {
        val (moving, still) = skies()
        // a standing page of a moving sky keeps its rain (it moves when the page comes); switched off: none
        assertTrue("rain drawn when wanted", differing(moving, still) > 200)
    }

    /**
     * The battery saver switched on while the sky is shown: its rain goes at once – the same still
     * sky as with the animation switched off.
     */
    @Test fun batterySaverNoRain() {
        val app = RuntimeEnvironment.getApplication()
        var motion by mutableStateOf(true)
        compose.setContent { WeatherBackground(storm, animate = false, modifier = Modifier.size(200.dp, 300.dp), motion = motion) }
        compose.waitForIdle()
        val raining = compose.onRoot().captureToImage().asAndroidBitmap()
        Shadows.shadowOf(app.getSystemService(android.os.PowerManager::class.java)).setIsPowerSaveMode(true)
        app.sendBroadcast(android.content.Intent(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.waitForIdle()
        val saver = compose.onRoot().captureToImage().asAndroidBitmap()
        motion = false
        compose.waitForIdle()
        val off = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("the battery saver took the rain away", differing(raining, saver) > 200)
        assertTrue("the battery saver's sky is the still one (${differing(saver, off)} pixels differ)", differing(saver, off) == 0)
    }
}

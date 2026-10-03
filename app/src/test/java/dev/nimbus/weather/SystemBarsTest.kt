/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/SystemBarsTest.kt
 * Leaving full screen gives the system bars back as they were: with the full screen's behaviour
 * kept, the system offered no rotate button any more (manual rotation).
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

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.nimbus.weather.ui.components.SystemBarsVisibility
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SystemBarsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun leavingFullScreenGivesBackTheRotateButton() {
        var fullscreen by mutableStateOf(true)
        compose.setContent { SystemBarsVisibility(fullscreen) }
        compose.waitForIdle()
        val window = compose.activity.window
        fun behavior() = WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior
        assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, behavior())
        fullscreen = false
        compose.waitForIdle()
        assertEquals(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT, behavior())
    }
}

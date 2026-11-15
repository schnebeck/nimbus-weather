/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WindowBehindBarsTest.kt
 * The window behind the system bars from its theme on Android 9, 11 and 15 – no Window colour setters.
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

import android.graphics.Color
import android.os.Build
import android.view.WindowManager.LayoutParams
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import dev.nimbus.weather.ui.drawBehindSystemBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 30, 35], application = android.app.Application::class)
class WindowBehindBarsTest {
    private fun opened(theme: Int, darkIcons: Boolean): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(theme)
        val activity = controller.create().get()
        activity.drawBehindSystemBars(darkIcons)
        return activity
    }

    /** Android 9 and 10 know the short edges; from 11 on the window reaches into the cutout on every edge. */
    private val cutout =
        if (Build.VERSION.SDK_INT >= 30) LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

    @Test fun theMainWindowLiesBehindClearBarsWithLightIcons() {
        val window = opened(R.style.Theme_Nimbus, darkIcons = false).window
        assertEquals(Color.TRANSPARENT, window.statusBarColor)
        assertEquals(Color.TRANSPARENT, window.navigationBarColor)
        assertEquals(cutout, window.attributes.layoutInDisplayCutoutMode)
        if (Build.VERSION.SDK_INT >= 29) assertFalse(window.isNavigationBarContrastEnforced)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        assertFalse(bars.isAppearanceLightStatusBars)
        assertFalse(bars.isAppearanceLightNavigationBars)
    }

    @Test fun theWidgetSetupHasDarkIconsInTheLightLook() {
        val window = opened(R.style.Theme_Nimbus_WidgetSetup, darkIcons = true).window
        assertEquals(Color.TRANSPARENT, window.statusBarColor)
        assertEquals(cutout, window.attributes.layoutInDisplayCutoutMode)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        assertTrue(bars.isAppearanceLightStatusBars)
        assertTrue(bars.isAppearanceLightNavigationBars)
    }
}

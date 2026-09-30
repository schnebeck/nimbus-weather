/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/MainActivity.kt
 * The single activity: edge-to-edge window, Compose content and demo hooks for tests.
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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.ui.Demo
import dev.nimbus.weather.ui.MainViewModel
import dev.nimbus.weather.ui.background.Season
import dev.nimbus.weather.ui.NimbusRoot
import dev.nimbus.weather.ui.theme.NimbusTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyDemoExtras()
        setContent {
            NimbusTheme {
                NimbusRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDemoExtras()
    }

    override fun onResume() {
        super.onResume()
        dev.nimbus.weather.ui.background.UserActivity.touch()
        viewModel.onResume()
    }

    override fun onPause() {
        viewModel.onPause()
        super.onPause()
    }

    /** Every touch counts as activity: the sky animation slows down when nobody looks for a while. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        dev.nimbus.weather.ui.background.UserActivity.touch()
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Test hook for visual QA: `adb shell am start -n dev.nimbus.weather/.MainActivity
     *   --es demo_condition RAIN --ez demo_night true --es demo_season AUTUMN --es demo_wind 0.8
     *   --es demo_pollen 0.6` forces a background scene.
     */
    private fun applyDemoExtras() {
        val i = intent ?: return
        val cond = i.getStringExtra("demo_condition")?.let { runCatching { Condition.valueOf(it) }.getOrNull() }
        val season = i.getStringExtra("demo_season")?.let { runCatching { Season.valueOf(it) }.getOrNull() }
        val wind = i.getStringExtra("demo_wind")?.toFloatOrNull()
        val pollen = i.getStringExtra("demo_pollen")?.toFloatOrNull()
        val demo = if (cond != null || season != null || wind != null || pollen != null) {
            Demo(cond, i.getBooleanExtra("demo_night", false), season, wind, pollen)
        } else null
        viewModel.setDemo(demo, i.getStringExtra("demo_screen"))
        dev.nimbus.weather.ui.radar.RadarPalette.demoTempOffset = i.getStringExtra("demo_radar_temp")?.toFloatOrNull() ?: 0f
        // Test hook: run the background radar refresh once right now (debug builds only).
        if (BuildConfig.DEBUG && i.getBooleanExtra("demo_radar_worker", false)) {
            androidx.work.WorkManager.getInstance(this).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<dev.nimbus.weather.data.repo.RadarWorker>().build(),
            )
        }
    }
}

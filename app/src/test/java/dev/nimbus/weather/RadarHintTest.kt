/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarHintTest.kt
 * The radar's loading hint goes as soon as the step shown is there, also while paused.
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

import androidx.compose.ui.test.junit4.createComposeRule
import dev.nimbus.weather.ui.radar.rememberCanShow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RadarHintTest {
    @get:Rule val compose = createComposeRule()

    /**
     * Paused on a step still being cut (the slider moved there): the hint stands; the step made
     * ready – nothing else changes on the screen – and it goes.
     */
    @Test fun theHintGoesWhenTheStepIsThere() {
        val ready = MutableStateFlow(0)
        var there = false
        var loading = false
        compose.setContent { loading = !rememberCanShow(ready, 5, "day") { there } }
        compose.waitForIdle()
        assertTrue(loading)
        there = true
        ready.value = 1
        compose.waitForIdle()
        assertFalse("the hint still stands", loading)
    }
}

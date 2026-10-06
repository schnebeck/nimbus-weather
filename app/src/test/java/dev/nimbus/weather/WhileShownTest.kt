/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/WhileShownTest.kt
 * Timers of the screens rest behind the lock screen: work runs while the app is shown only.
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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nimbus.weather.ui.components.LaunchedWhileShown
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * „meiner Meinnug nach, hat Bis auf ein paar wetterwarnungen Nimbus keine Eigenschaften, für die es
 * überhaupt bei ausgeschaltetem Bildschirm noch weiterarbeiten sollte“ – the screens too: the
 * radar asking for a newer analysis every minute, the look-back's sky changing its part every 5 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WhileShownTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** Shown: the work runs; the phone locked (stopped): called off; shown again: started anew. */
    @Test fun workRestsWhileNotShown() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var runs = 0
        var stops = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LaunchedWhileShown(Unit) {
                    runs++
                    try { awaitCancellation() } finally { stops++ }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1 to 0, runs to stops)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertEquals("stopped: called off", 1 to 1, runs to stops)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        assertEquals("shown again: anew", 2 to 1, runs to stops)
    }

    /**
     * Every endless loop of the screens (and their view model) runs while the app is shown – in
     * [LaunchedWhileShown], the view model's work while shown (called off in onPause) or a
     * lifecycle's STARTED – or waits for display frames, which do not come behind the lock screen.
     */
    @Test fun noScreenTimerRunsBehindTheLockScreen() {
        val ui = File("src/main/java/dev/nimbus/weather/ui")
        val shown = listOf("LaunchedWhileShown(", "launchWhileShown {", "repeatOnLifecycle(")
        val loose = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val lines = f.readLines()
            lines.indices.filter { lines[it].contains("while (true)") }.mapNotNull { i ->
                val before = lines.subList(maxOf(0, i - 12), i).joinToString("\n")
                val body = lines.subList(i, minOf(lines.size, i + 12)).joinToString("\n")
                if (shown.any { it in before } || "withFrameNanos" in body) null else "${f.name}:${i + 1}"
            }
        }.toList()
        assertTrue("loops that run on behind the lock screen: $loose", loose.isEmpty())
    }
}

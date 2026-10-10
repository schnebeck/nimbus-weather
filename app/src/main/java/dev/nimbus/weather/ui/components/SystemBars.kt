/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/components/SystemBars.kt
 * How the app meets the system bars: drawn under the gesture handle, kept clear of the three
 * buttons of the button navigation, or full screen (setting).
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

package dev.nimbus.weather.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * How the app meets the navigation bar:
 * - [EDGE_TO_EDGE]: gesture navigation – the sky goes on under the thin handle;
 * - [RESERVED]: button navigation (back, home, recents) – the app ends above (beside) the bar, the
 *   buttons stay on a calm dark ground instead of over the moving sky and the cards;
 * - [FULLSCREEN]: the setting – status and navigation bar hidden, a swipe from the edge shows
 *   them for a moment.
 */
enum class NavBarMode { EDGE_TO_EDGE, RESERVED, FULLSCREEN }

fun navBarMode(fullscreen: Boolean, buttons: Boolean): NavBarMode = when {
    fullscreen -> NavBarMode.FULLSCREEN
    buttons -> NavBarMode.RESERVED
    else -> NavBarMode.EDGE_TO_EDGE
}

/** True where the app already keeps clear of the navigation bar (button navigation): the screens add no bottom space of their own. */
val LocalNavBarReserved = staticCompositionLocalOf { false }

/** Space a screen keeps free at its bottom for the navigation bar – none where the app as a whole already ends above it. */
@Composable
fun navBarBottom(): Dp =
    if (LocalNavBarReserved.current) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Whether the navigation bar has buttons to tap (button navigation) – the gesture handle has none. */
@Composable
fun hasNavButtons(): Boolean {
    val d = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val t = WindowInsets.tappableElement
    return t.getBottom(d) > 0 || t.getLeft(d, dir) > 0 || t.getRight(d, dir) > 0
}

/** Hides (full screen) or shows the system bars of the activity this runs in. */
@Composable
fun SystemBarsVisibility(fullscreen: Boolean) {
    val view = LocalView.current
    DisposableEffect(fullscreen, view) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullscreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                // back to the default: with the full screen's behaviour kept, the system offers
                // no rotate button (manual rotation) – the bars count as hidden
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The space at the top whether the status bar is shown or hidden (full screen), and at least the
 * camera cut-out: menu, radar button and header stay where they are when the bars go – they slid
 * up into the screen's rounded corners.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
val WindowInsets.Companion.statusBarsStable: WindowInsets
    @Composable get() = WindowInsets.statusBarsIgnoringVisibility.union(
        WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Top),
    )

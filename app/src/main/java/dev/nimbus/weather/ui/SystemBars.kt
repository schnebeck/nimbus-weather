/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/SystemBars.kt
 * The window drawn behind the status and navigation bar, their icons light or dark.
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

package dev.nimbus.weather.ui

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The content laid out behind the system bars. Their transparent colour, the display cutout and
 * the contrast scrim come from the window's theme (values, values-v28, values-v29, values-v30);
 * here only the layout and the icons' appearance – [darkIcons] for a light content behind them.
 */
fun Activity.drawBehindSystemBars(darkIcons: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val bars = WindowInsetsControllerCompat(window, window.decorView)
    bars.isAppearanceLightStatusBars = darkIcons
    bars.isAppearanceLightNavigationBars = darkIcons
}

/** Whether the system shows its dark look. */
fun Activity.inNightMode(): Boolean =
    resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

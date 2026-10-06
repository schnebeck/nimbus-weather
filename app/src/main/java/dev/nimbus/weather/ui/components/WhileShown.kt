/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/components/WhileShown.kt
 * Work of the screens that runs only while the app is shown – nothing behind the lock screen.
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * Runs [block] while the app is shown (its lifecycle at least STARTED): called off when the app
 * goes to the background or the phone is locked, started anew when it comes back – a timer in a
 * screen never wakes the phone behind the lock screen. Started anew, like [LaunchedEffect], when
 * one of [keys] changes.
 */
@Composable
fun LaunchedWhileShown(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, *keys) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block) }
}

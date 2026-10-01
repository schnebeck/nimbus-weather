/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/components/Reorderable.kt
 * A column whose rows are sorted by dragging a handle (also with TalkBack: move up / down).
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

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Rows of [items], sorted by dragging the modifier handed to [row] as `handle` (a drag handle
 * icon). Rows may differ in height (an expanded group moves as a whole). The new order is passed
 * to [onMove] when the row is dropped; [moveUp]/[moveDown] label the TalkBack actions on the handle.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (List<T>) -> Unit,
    moveUp: String,
    moveDown: String,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    row: @Composable (item: T, dragging: Boolean, handle: Modifier) -> Unit,
) {
    var order by remember(items) { mutableStateOf(items) }
    var dragKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val haptics = LocalHapticFeedback.current

    fun move(from: Int, to: Int) {
        if (from == to || to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        order.forEach { item ->
            val k = key(item)
            key(k) {
                val dragging = dragKey == k
                val handle = Modifier
                    .pointerInput(k) {
                        detectDragGestures(
                            onDragStart = { dragKey = k; dragOffset = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val i = order.indexOfFirst { key(it) == k }
                                // Swap with a neighbour once the row has passed half of it
                                val next = order.getOrNull(i + 1)?.let { heights[key(it)] }
                                val prev = order.getOrNull(i - 1)?.let { heights[key(it)] }
                                if (next != null && dragOffset > next / 2f) {
                                    move(i, i + 1); dragOffset -= next + gapPx
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (prev != null && -dragOffset > prev / 2f) {
                                    move(i, i - 1); dragOffset += prev + gapPx
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = { dragKey = null; dragOffset = 0f; onMove(order) },
                            onDragCancel = { dragKey = null; dragOffset = 0f; onMove(order) },
                        )
                    }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(moveUp) {
                                val i = order.indexOfFirst { key(it) == k }
                                (i > 0).also { if (it) { move(i, i - 1); onMove(order) } }
                            },
                            CustomAccessibilityAction(moveDown) {
                                val i = order.indexOfFirst { key(it) == k }
                                (i < order.lastIndex).also { if (it) { move(i, i + 1); onMove(order) } }
                            },
                        )
                    }
                Box(
                    Modifier
                        .onSizeChanged { heights[k] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                        },
                ) { row(item, dragging, handle) }
            }
        }
    }
}

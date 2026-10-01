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

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Pure decision logic of [ReorderableColumn] – kept free of Compose for unit tests. */
object Reorder {
    /**
     * A row moves past a neighbour once its leading edge (the bottom when dragged down, the top
     * when dragged up) covers this much of the neighbour – as Android's ItemTouchHelper does. It
     * works for rows of any height (a tall expanded group swaps as readily as a single row), and
     * moving back needs a clear counter-movement (0.4 of a row): a finger never holds perfectly
     * still, and without such a margin the row flipped back and forth at the boundary.
     */
    const val OVERLAP = 0.7f

    /** Row bounds within the column: top and height. */
    data class Row(val top: Float, val height: Float)

    /**
     * New index of the dragged row whose top is at [dragTop] (column coordinates), currently at
     * [index] among [rows] (in their current order and layout).
     */
    fun target(rows: List<Row>, index: Int, dragTop: Float): Int {
        val h = rows[index].height
        var i = index
        while (i + 1 < rows.size) {
            val n = rows[i + 1]
            if (dragTop + h > n.top + n.height * OVERLAP) i++ else break
        }
        if (i != index) return i
        while (i - 1 >= 0) {
            val p = rows[i - 1]
            if (dragTop < p.top + p.height * (1f - OVERLAP)) i-- else break
        }
        return i
    }

    /** The dragged row stays within the column: from the first row's top to the last row's bottom. */
    fun clampTop(rows: List<Row>, index: Int, dragTop: Float): Float {
        val minTop = rows.first().top
        val maxTop = rows.last().let { it.top + it.height } - rows[index].height
        return dragTop.coerceIn(minTop, maxOf(minTop, maxTop))
    }
}

/**
 * Rows of [items], sorted by holding and then dragging the modifier handed to [row] as
 * `handle` (a drag handle icon). The dragged row follows the finger in absolute terms – its offset is computed from its
 * actual place in the layout, so it never jumps when the rows around it change places. Rows may
 * differ in height (an expanded group moves as a whole). The new order goes to [onMove] when the
 * row is dropped; [moveUp]/[moveDown] label the TalkBack actions on the handle.
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
    // One state for the lifetime of the list: the drag handlers keep a reference to it. (Keyed by
    // [items], it was replaced after every drop when the new order came back from the settings –
    // the handlers then sorted a stale copy and the row drifted away from the layout.)
    var order by remember { mutableStateOf(items) }
    var dragKey by remember { mutableStateOf<Any?>(null) }
    // A new order from outside (saved, reset) – taken over when no row is being dragged
    if (dragKey == null && order != items) order = items
    /** Top of the dragged row in column coordinates, where the finger has taken it. */
    var dragTop by remember { mutableFloatStateOf(0f) }
    /** Layout of every row (top, height), updated after each placement. */
    val bounds = remember { mutableStateMapOf<Any, Reorder.Row>() }
    val haptics = LocalHapticFeedback.current

    /** Rows in the current order – null until the layout has caught up with the last move. */
    fun rows() = order.mapNotNull { bounds[key(it)] }
        .takeIf { r -> r.size == order.size && r.zipWithNext().all { (a, b) -> a.top < b.top } }

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
                        // Hold the handle first: a swipe that merely starts on a handle scrolls
                        // the page instead of moving a row by accident
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragKey = k
                                dragTop = bounds[k]?.top ?: 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                // The movement always counts; only the decision waits for the layout
                                // to catch up with the last move (otherwise fast drags lagged behind)
                                dragTop += amount.y
                                val rows = rows() ?: return@detectDragGesturesAfterLongPress
                                val i = order.indexOfFirst { key(it) == k }
                                dragTop = Reorder.clampTop(rows, i, dragTop)
                                val to = Reorder.target(rows, i, dragTop)
                                if (to != i) {
                                    move(i, to)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = { dragKey = null; onMove(order) },
                            onDragCancel = { dragKey = null; onMove(order) },
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
                        .onPlaced { c -> bounds[k] = Reorder.Row(c.positionInParent().y, c.size.height.toFloat()) }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            // Read at draw time: always relative to where the row is laid out now
                            translationY = if (dragging) dragTop - (bounds[k]?.top ?: dragTop) else 0f
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                        },
                ) { row(item, dragging, handle) }
            }
        }
    }
}

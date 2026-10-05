/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RvSteps.kt
 * How a radar step gets RainViewer's picture: its own, one computed between the two around it, the
 * nearest – or none.
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

package dev.nimbus.weather.ui.radar

import kotlin.math.abs

/** Where a step's RainViewer picture comes from ([RvSteps.plan]). */
sealed interface RvPlan {
    /** A RainViewer frame of the step's own time. */
    data class Own(val path: String) : RvPlan
    /** Between the frames [before] and [after] ([gapMs] apart): computed at [t] (0–1) from the motion between them. */
    data class Between(val before: String, val after: String, val t: Float, val gapMs: Long) : RvPlan
    /** The nearest frame (at most 5 minutes off) – no later one to move towards (the newest step). */
    data class Near(val path: String) : RvPlan
    data object None : RvPlan
}

/**
 * RainViewer has a frame every 10 minutes, the time line a step every 5 (the composites'). A step
 * between two frames took the nearer one as it was: the rain stood still for a step and jumped
 * the next – it stuttered where RainViewer shows it, and only there. Now such a step gets a
 * picture of its own, moved half the way from the frame before to the one after.
 */
object RvSteps {
    /** Off by less than this, a RainViewer frame is the step's own. */
    const val OWN_MS = 150_000L
    /** Two RainViewer frames at most this far apart are moved between. */
    const val MAX_GAP_MS = 20 * 60_000L

    fun plan(tl: RadarTimeline, i: Int): RvPlan {
        val f = tl.frames.getOrNull(i) ?: return RvPlan.None
        // RainViewer has no forecast: a nowcast step never shows its last past frame
        if (f.isForecast) return RvPlan.None
        fun own(k: Int) = tl.frames.getOrNull(k)?.takeIf { g -> g.rainViewerPath != null && g.rainViewerTime != null && abs(g.rainViewerTime - g.time) < OWN_MS }
        own(i)?.let { return RvPlan.Own(it.rainViewerPath!!) }
        val before = (i - 1 downTo 0).firstNotNullOfOrNull { k -> own(k)?.takeIf { f.time - it.time <= MAX_GAP_MS } }
        val after = (i + 1 until tl.frames.size).firstNotNullOfOrNull { k -> own(k)?.takeIf { it.time - f.time <= MAX_GAP_MS } }
        if (before != null && after != null) {
            val t = ((f.time - before.time).toFloat() / (after.time - before.time)).coerceIn(0f, 1f)
            return RvPlan.Between(before.rainViewerPath!!, after.rainViewerPath!!, t, after.time - before.time)
        }
        return f.rainViewerPath?.let { RvPlan.Near(it) } ?: RvPlan.None
    }
}

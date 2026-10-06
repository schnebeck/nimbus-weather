/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/Playback.kt
 * Radar playback: loading a long time line coarse to fine, and one display frame of playback.
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

/**
 * Loading a long time line coarse to fine: first one step every 2 hours, then every hour, every
 * 30 minutes, every 15, finally all – the player shows the whole time line from the start and moves the rain
 * between the steps it has; every finer level makes it more exact.
 */
object Progressive {
    private val LEVEL_MINUTES = intArrayOf(120, 60, 30, 15, 1)

    /** Strides (in steps) of the levels for steps of [stepMinutes]: 5 min → 24, 12, 6, 3, 1. */
    fun strides(stepMinutes: Int): IntArray =
        LEVEL_MINUTES.map { maxOf(1, it / maxOf(1, stepMinutes)) }.distinct().sortedDescending().toIntArray()

    /** Coarse levels loaded over the whole time line first (an overview: every 2 hours, every hour). */
    private const val OVERVIEW_MINUTES = 60
    /** Loaded before the overview: the steps right after the position. */
    private const val START_MINUTES = 30

    /**
     * Load order of [n] steps: the first [START_MINUTES] from [from], then the overview levels (every [OVERVIEW_MINUTES] and coarser) over the
     * whole time line, coarse first – the last step and [from] with the first –, then all others in
     * playback order from [from] on (those behind last): playback needs them in that order, and
     * they arrive faster than it plays.
     */
    fun order(n: Int, from: Int, strides: IntArray, stepMinutes: Int = 5, overviewMinutes: Int = OVERVIEW_MINUTES): List<Int> {
        if (n <= 0) return emptyList()
        val seen = BooleanArray(n)
        val out = ArrayList<Int>(n)
        fun rank(i: Int) = if (i >= from) i - from else n + (from - i)      // ahead first, then behind
        // The first half hour from the position first: playback can start at once
        (from..minOf(n - 1, from + START_MINUTES / maxOf(1, stepMinutes))).forEach { seen[it] = true; out += it }
        strides.filter { it * stepMinutes >= overviewMinutes }.forEachIndexed { level, s ->
            val take = (0 until n).filter { !seen[it] && (it % s == 0 || level == 0 && (it == n - 1 || it == from)) }
            take.sortedBy { rank(it) }.forEach { seen[it] = true; out += it }
        }
        (0 until n).filter { !seen[it] }.sortedBy { rank(it) }.forEach { out += it }
        return out
    }

    /** Frames further apart than this are not moved into each other (measured: beyond it the motion is guessed, not found). */
    const val MAX_MOTION_MS = 30 * 60_000L

    /**
     * How to show [t] (0–1) between two frames [gapMs] apart: moving the rain (true, at [t]) – or,
     * across a wider gap (finer steps still loading), without motion: the earlier frame, a short
     * blend in the middle, then the later one (returned fraction).
     */
    fun blend(gapMs: Long, t: Float): Pair<Boolean, Float> =
        if (gapMs <= MAX_MOTION_MS) true to t else false to ((t - 0.4f) / 0.2f).coerceIn(0f, 1f)

    /**
     * Steps a long live time line keeps for good while windowed: every [keepMinutes] by the clock
     * (:00, :20, :40 …; at most [MAX_MOTION_MS] apart, so playback can always move between them),
     * the first and the last one. The loop then starts over without preparing its start again –
     * and since they are chosen by time, a refreshed time line (a step later every 5 minutes)
     * keeps the same ones.
     */
    fun keepSteps(times: List<Long>, keepMinutes: Int): Set<Int> {
        val every = minOf(keepMinutes.toLong() * 60_000L, MAX_MOTION_MS)
        if (times.isEmpty()) return emptySet()
        return (times.indices.filter { times[it] % every == 0L } + 0 + times.lastIndex).toSet()
    }

    /** Can position [p] play: on a frame, or between two at most [MAX_MOTION_MS] apart ([timeOf] of an index). */
    fun playable(p: Float, n: Int, has: (Int) -> Boolean, timeOf: (Int) -> Long): Boolean {
        val (a, b) = bracket(p, n, has) ?: return false
        return if (b == a) abs(p - a) < 1e-3f || a == n - 1 else timeOf(b) - timeOf(a) <= MAX_MOTION_MS
    }

    /**
     * The steps to show at position [p]: the nearest one at or before it and the nearest after it
     * among those [has]; (a, a) right on a step or past the last one there is; null if there is
     * none at or before [p].
     */
    fun bracket(p: Float, n: Int, has: (Int) -> Boolean): Pair<Int, Int>? {
        if (n <= 0) return null
        val i = p.toInt().coerceIn(0, n - 1)
        var a = i
        while (a >= 0 && !has(a)) a--
        if (a < 0) return null
        if (p - i < 1e-4f && a == i) return a to a
        var b = i + 1
        while (b < n && !has(b)) b++
        return if (b < n) a to b else a to a
    }
}

/**
 * One display frame of radar playback – the same rule for the live loop and an archived day: the
 * position moves on at [stepMs] per step while the next step can be shown; when it cannot (not
 * loaded yet, or the frame window of a long time line is still moving), playback buffers – with
 * the loading hint – until [resumeAhead] steps ahead are there. It never just stands still. At the
 * end the live loop rests [restMs] and starts over; an archived day stops.
 */
object Playback {
    data class State(val position: Float, val buffering: Boolean = false, val restedMs: Float = 0f, val playing: Boolean = true)

    fun step(
        s: State, dtMs: Float, last: Int, loop: Boolean, stepMs: Float, canShow: (Float) -> Boolean,
        resumeAhead: Int = 12, restMs: Float = 1400f,
    ): State {
        if (!s.playing) return s
        val p = s.position
        if (p >= last) {
            if (!loop) return s.copy(playing = false, buffering = false)
            val rested = s.restedMs + dtMs
            return if (rested < restMs) s.copy(restedMs = rested, buffering = false)
            else State(0f, buffering = !canShow(minOf(resumeAhead, last).toFloat()))
        }
        // buffering ends with a stretch ahead – and playback moves on in the same frame
        if (s.buffering && !canShow(minOf(p + resumeAhead, last.toFloat()))) return s
        // on to where the time has gone – over several steps when a display frame took long,
        // but never past a step that cannot be shown
        val target = minOf(p + dtMs / stepMs, last.toFloat())
        var reach = p
        var k = p.toInt() + 1
        while (k <= target) {
            if (!canShow(k.toFloat())) return s.copy(position = reach, buffering = true)
            reach = k.toFloat(); k++
        }
        if (target > reach && !canShow(k.toFloat())) return s.copy(position = reach, buffering = reach == p)
        return s.copy(position = target, restedMs = 0f, buffering = false)
    }
}

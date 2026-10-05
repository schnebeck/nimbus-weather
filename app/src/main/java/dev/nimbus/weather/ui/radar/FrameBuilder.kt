/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/FrameBuilder.kt
 * The frame of a radar step for the picture area: the composites' cells, RainViewer's beyond them –
 * its own frame, one moved between two, or the nearest – or, where nothing can be had, empty.
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

import okhttp3.OkHttpClient
import kotlin.math.ceil

/**
 * Builds the frames of one picture area [g] of the time line [tl]; [covered]: where each composite
 * reaches into it. Keeps RainViewer's frames of the area and the motion between them, so the step
 * between two (every other one) costs only the moving.
 */
class FrameBuilder(
    private val http: OkHttpClient,
    private val tl: RadarTimeline,
    private val g: FieldGeo,
    covered: Map<RadarComposite, BooleanArray>,
    /** Extra smoothing where a field pixel is larger than a screen pixel ([screenSmooth]). */
    private val minSmooth: Int = 0,
) {
    private val n = g.w * g.h
    private val needsRv = RadarPicture.needsRainViewer(covered, n)
    private val rvFields = object : LinkedHashMap<String, ViewFrame>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ViewFrame>?) = size > 4
    }
    private val rvFlows = HashMap<String, Flow>()

    /**
     * The frame of step [i] from the composites' [layers] (those of the step at hand). Never
     * missing: a step nobody has anything for in the area (RainViewer has no forecast) is empty –
     * playback runs through it instead of waiting for it.
     */
    suspend fun build(i: Int, layers: List<RadarLayer>): ViewFrame =
        when (val plan = if (needsRv) RvSteps.plan(tl, i) else RvPlan.None) {
            is RvPlan.Own -> RadarField.extract(g, layers, mosaic(plan.path), minSmooth = minSmooth)
            is RvPlan.Near -> RadarField.extract(g, layers, mosaic(plan.path), minSmooth = minSmooth)
            is RvPlan.Between -> {
                val mid = between(plan)
                if (layers.isEmpty()) mid
                else BooleanArray(n).let { fromRv -> RvBetween.merge(RadarField.extract(g, layers, null, fromRv, minSmooth), mid, fromRv) }
            }
            RvPlan.None -> if (layers.isEmpty()) RvBetween.empty(n) else RadarField.extract(g, layers, null, minSmooth = minSmooth)
        }

    private suspend fun mosaic(path: String) = RadarPicture.mosaic(http, tl.rainViewerHost, path, g)

    /** RainViewer alone in the area (its frame [path]). */
    private suspend fun rvField(path: String): ViewFrame =
        rvFields[path] ?: RadarField.extract(g, emptyList(), mosaic(path), minSmooth = minSmooth).also { rvFields[path] = it }

    private suspend fun between(p: RvPlan.Between): ViewFrame {
        val a = rvField(p.before)
        val b = rvField(p.after)
        val flow = rvFlows.getOrPut("${p.before}>${p.after}") {
            // fast showers move up to ~150 km/h (as playback looks for)
            RadarField.motion(a, b, g.w, g.h, (150 * p.gapMs / 3_600_000.0 / g.pxKm).toFloat().coerceAtLeast(2f))
        }
        return RvBetween.between(a, b, flow, p.t, g.w, g.h)
    }

    companion object {
        /**
         * Smoothing for a field whose pixel ([fieldPxM]) is larger than a screen pixel ([screenPxM]):
         * zoomed out, a field pixel spans up to three on the screen – unsmoothed, they showed as soft
         * blocks. One field pixel of smoothing per screen pixel beyond the first, at most 3.
         */
        fun screenSmooth(fieldPxM: Double, screenPxM: Double): Int {
            if (screenPxM <= 0.0) return 0
            val s = fieldPxM / screenPxM
            return if (s <= 1.25) 0 else ceil(s - 1).toInt().coerceIn(0, 3)
        }
    }
}

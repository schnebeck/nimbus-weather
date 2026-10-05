/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RvBetween.kt
 * A RainViewer picture between two of its frames, moved along the rain's motion – and put
 * together with the composites' picture of the same step.
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

import kotlin.math.roundToInt

object RvBetween {
    /**
     * The picture at [t] (0–1) between [a] and [b] (both [w] × [h]): every pixel takes [a] from
     * where the motion [flow] comes from and [b] from where it goes, weighted by [t] – as playback
     * draws between two steps ([RadarField.render]), but kept as a frame.
     */
    fun between(a: ViewFrame, b: ViewFrame, flow: Flow, t: Float, w: Int, h: Int): ViewFrame {
        val n = w * h
        val dbz = ByteArray(n); val wet = ByteArray(n)
        val snow = if (a.snow != null || b.snow != null) ByteArray(n) else null
        val sa = FloatArray(3); val sb = FloatArray(3); val mv = FloatArray(2)
        for (y in 0 until h) for (x in 0 until w) {
            flow.at(x.toFloat(), y.toFloat(), mv)
            RadarField.sample(a, w, h, x - t * mv[0], y - t * mv[1], sa)
            RadarField.sample(b, w, h, x + (1 - t) * mv[0], y + (1 - t) * mv[1], sb)
            val c = (1 - t) * sa[0] + t * sb[0]
            if (c < 1e-3f) continue
            val i = y * w + x
            wet[i] = (c * 255).roundToInt().coerceIn(0, 255).toByte()
            dbz[i] = (((1 - t) * sa[0] * sa[1] + t * sb[0] * sb[1]) / c * 2).roundToInt().coerceIn(0, 255).toByte()
            snow?.set(i, (((1 - t) * sa[0] * sa[2] + t * sb[0] * sb[2]) / c * 255).roundToInt().coerceIn(0, 255).toByte())
        }
        return ViewFrame(dbz, wet, snow)
    }

    /** [composites] where they have values, [rainViewer] where [fromRv] marks none. */
    fun merge(composites: ViewFrame, rainViewer: ViewFrame, fromRv: BooleanArray): ViewFrame {
        val n = fromRv.size
        val dbz = composites.dbz.copyOf(); val wet = composites.wet.copyOf()
        val snow = if (composites.snow != null || rainViewer.snow != null) composites.snow?.copyOf() ?: ByteArray(n) else null
        for (i in 0 until n) if (fromRv[i]) {
            dbz[i] = rainViewer.dbz[i]; wet[i] = rainViewer.wet[i]
            snow?.set(i, rainViewer.snow?.get(i) ?: 0)
        }
        return ViewFrame(dbz, wet, snow)
    }

    /** Nothing falls (or nobody can say): a frame of the area, empty. */
    fun empty(n: Int) = ViewFrame(ByteArray(n), ByteArray(n), null)
}

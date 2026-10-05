/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarData.kt
 * Radar frames and time lines.
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

/** One animation frame of the radar loop. */
data class RadarFrame(
    val time: Long,
    val isForecast: Boolean,
    /** A nowcast step: the analysis it was computed from (null for past steps). */
    val issue: Long?,
    /** RainViewer tile path (Europe, past only), null if no matching frame. */
    val rainViewerPath: String?,
)

/**
 * The step's identity in the radar view: its time – a nowcast step also the analysis it was
 * computed from (a newer analysis makes it another step).
 */
val RadarFrame.id: String
    get() = if (isForecast) "${time / 60_000L}_n${(issue ?: 0L) / 60_000L}" else "${time / 60_000L}"

data class RadarTimeline(
    val frames: List<RadarFrame>, val nowIndex: Int, val rainViewerHost: String, val range: HistoryRange = HistoryRange.H2,
    /** Start (local midnight) of a past day shown in full (look-back archive), else null. */
    val day: Long? = null,
)

/**
 * How far the radar loop looks back. DWD keeps three days of radar, RainViewer (Europe) only two
 * hours, so the longer ranges show the composites only. Every range has all 5-minute steps (the
 * slider steps through them), [playMinutes] is how much weather playback shows per beat – the
 * longer ranges play faster, so every loop takes about the same time.
 */
enum class HistoryRange(val hours: Int, val playMinutes: Int) {
    H2(2, 10), H6(6, 20), H24(24, 60);

    companion object {
        /** Every step of the composites, analysis and nowcast. */
        const val STEP_MINUTES = 5
        const val STEP_MS = STEP_MINUTES * 60_000L
    }
}

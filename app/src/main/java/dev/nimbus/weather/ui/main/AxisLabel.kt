/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/AxisLabel.kt
 * Where a chart's axis label goes: centred on its mark, but never beyond the chart's edge.
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

package dev.nimbus.weather.ui.main

/**
 * The left edge of a label [width] wide centred on [center], kept inside a chart [chartWidth]
 * wide: at the edges ("00", "24", "12 AM") it moves in rather than being cut off. The marks and
 * the data stay where they are.
 */
fun axisLabelLeft(center: Float, width: Int, chartWidth: Float): Float =
    (center - width / 2f).coerceIn(0f, (chartWidth - width).coerceAtLeast(0f))

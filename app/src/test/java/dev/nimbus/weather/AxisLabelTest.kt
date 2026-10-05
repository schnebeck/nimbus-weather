/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/AxisLabelTest.kt
 * The sun chart's axis in English cut off ("2 AM", "12 A"): labels stay inside the chart.
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

package dev.nimbus.weather

import dev.nimbus.weather.ui.main.axisLabelLeft
import org.junit.Assert.assertEquals
import org.junit.Test

class AxisLabelTest {
    /** A chart 900 px wide whose margins are made for "24" (34 px): "12 AM" (78 px) at both ends. */
    @Test fun twelveAmInsideAtBothEnds() {
        val chart = 900f; val margin = 17f; val label = 78
        // centred on its mark it began at −22 px ("2 AM") and ended 22 px beyond the edge ("12 A")
        assertEquals(0f, axisLabelLeft(margin, label, chart), 0f)
        assertEquals(chart - label, axisLabelLeft(chart - margin, label, chart), 0f)
        // in between: centred on its mark
        assertEquals(450f - 39f, axisLabelLeft(450f, label, chart), 0f)
    }

    /** A label wider than the chart: at its left edge (no crash). */
    @Test fun widerThanTheChart() = assertEquals(0f, axisLabelLeft(10f, 120, 100f), 0f)
}

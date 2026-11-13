/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarOutOfServiceTest.kt
 * A radar out of service says "no data" over the place: nothing of it counts as dry.
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

import dev.nimbus.weather.data.remote.SpotSource
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Norden in East Frisia, a day the radar there measured nothing: the DWD's answers at the place
 * as they came – RY -1, the analysis and its nowcast -999 at every step.
 */
class RadarOutOfServiceTest {
    @Test fun theAnalysisHasNoStepsAndSoNoHours() {
        val steps = SpotSource.analysis(Fixtures.json("radar_wn_no_data_norden.json"))
        assertTrue("no-data steps read as dry: $steps", steps.isEmpty())
        assertTrue(SpotSource.hourEchoes(steps).isEmpty())
    }

    @Test fun theNowcastIsNone() {
        assertNull(SpotSource.nowcast(Fixtures.json("radar_nowcast_no_data_norden.json")))
    }

    /** No radar now: the page takes the station's sky or the model's, never "dry by the radar". */
    @Test fun thereIsNoRadarNow() {
        val ry = SpotSource.series(Fixtures.json("radar_ry_no_data_norden.json"))
        assertTrue(ry.isEmpty())
        assertNull(SpotSource.now(ry))
    }

    /** A dry pixel (-64 dBZ) is still a step: no echo, 0 mm/h. */
    @Test fun aDryStepStaysAStep() {
        val dry = dev.nimbus.weather.data.remote.JsonCodec.parseToJsonElement(
            """{"features":[{"properties":{"WN_ANALYSIS":-64.0029296875,"TIME":"2026-11-13T14:00:00Z"}}]}""",
        )
        assertTrue(SpotSource.analysis(dry).values.single() == 0.0)
    }
}

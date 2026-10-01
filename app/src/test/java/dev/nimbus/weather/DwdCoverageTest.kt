/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DwdCoverageTest.kt
 * The DWD radar area is the inside of the "no data" ring of a composite image.
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

import dev.nimbus.weather.ui.radar.DwdCoverage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DwdCoverageTest {
    @Test fun insideOfTheGreyRingIsCovered() {
        // 7 x 5: transparent outside, a grey ring, inside "no rain" (transparent) and one rain pixel
        val T = 0x00FFFFFF; val G = 0x807E7E7E.toInt(); val R = 0xFF99CC00.toInt()
        val img = intArrayOf(
            T, T, T, T, T, T, T,
            T, G, G, G, G, G, T,
            T, G, T, R, T, G, T,
            T, G, G, G, G, G, T,
            T, T, T, T, T, T, T,
        )
        val m = DwdCoverage.compute(img, 7, 5)
        assertTrue(m[2 * 7 + 2] && m[2 * 7 + 3] && m[2 * 7 + 4])   // inside: no rain and rain
        assertFalse(m[0])                                          // outside the ring
        assertFalse(m[1 * 7 + 1])                                  // the ring itself
    }
}

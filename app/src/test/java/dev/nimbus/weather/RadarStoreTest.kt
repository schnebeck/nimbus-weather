/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/RadarStoreTest.kt
 * Stored radar steps: packed losslessly, small, and removed when they expire.
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

import dev.nimbus.weather.ui.radar.DwdRadar
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarStoreTest {
    private val min = 60_000L
    private val now = 1_790_900_000_000L / min * min

    @Test fun packedLosslesslyAndSmall() {
        // A composite with a band of rain: mostly dry, like the real thing
        val grid = ByteArray(DwdRadar.W * DwdRadar.H)
        for (y in 300 until 500) for (x in 200 until 900) grid[y * DwdRadar.W + x] = (8 + (x + y) % 40).toByte()
        val packed = RadarStore.pack(grid)
        assertArrayEquals(grid, RadarStore.unpack(packed))
        assertTrue("packed ${packed.size} bytes", packed.size < grid.size / 10)
        assertNull(RadarStore.unpack(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)))
    }

    @Test fun nowcastStepsCarryTheirAnalysis() {
        val f = RadarFrame(now + 30 * min, true, now, null)
        assertEquals("dwd_${(now + 30 * min) / min}_n${now / min}", DwdRadar.key(f, now))
        assertEquals("dwd_${now / min}", DwdRadar.key(RadarFrame(now, false, null, null), now))
    }

    @Test fun expiry() {
        // analyses: as long as the DWD archive (about 3½ days)
        assertFalse(RadarStore.expired("dwd_${(now - 3 * 24 * 60 * min) / min}.nrd", now, now, now))
        assertTrue(RadarStore.expired("dwd_${(now - 5 * 24 * 60 * min) / min}.nrd", now, now, now))
        // nowcast of the newest analysis stays; of an older one it goes after a grace period
        val t = (now + 20 * min) / min
        assertFalse(RadarStore.expired("dwd_${t}_n${now / min}.nrd", now, now, now))
        assertFalse(RadarStore.expired("dwd_${t}_n${(now - 5 * min) / min}.nrd", now - 10 * min, now, now))
        assertTrue(RadarStore.expired("dwd_${t}_n${(now - 5 * min) / min}.nrd", now - 40 * min, now, now))
        // RainViewer after 3 hours; unknown files always
        assertFalse(RadarStore.expired("rv_v2-radar-abc_6_33_21.nrd", now - 2 * 60 * min, now, now))
        assertTrue(RadarStore.expired("rv_v2-radar-abc_6_33_21.nrd", now - 4 * 60 * min, now, now))
        assertTrue(RadarStore.expired("something.nrd", now, now, now))
        // the KNMI's steps: as long as the DWD's analyses
        assertFalse(RadarStore.expired("knmi_${(now - 3 * 24 * 60 * min) / min}.nrd", now, now, now))
        assertTrue(RadarStore.expired("knmi_${(now - 5 * 24 * 60 * min) / min}.nrd", now, now, now))
    }
}

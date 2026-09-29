/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/CommunityTest.kt
 * Tests for the Sensor.Community readings.
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

import dev.nimbus.weather.data.remote.CommunitySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityTest {
    @Test
    fun `aggregates real sensor community data`() {
        val c = CommunitySource.aggregate(Fixtures.json("sensor_community.json"), 3.0)!!
        assertTrue(c.sensorCount >= 10)
        assertTrue(c.temperature!! in 0.0..35.0)
        assertTrue(c.humidity!! in 1.0..100.0)
        assertTrue(c.pressure!! in 950.0..1060.0)
        assertNotNull(c.pm25)
    }

    @Test
    fun `robust filter removes sun-heated outliers`() {
        val values = listOf(18.1, 18.4, 17.9, 18.6, 18.2, 31.5, -12.0)
        val kept = CommunitySource.robust(values)
        assertEquals(5, kept.size)
        assertEquals(18.2, CommunitySource.median(kept)!!, 0.001)
    }

    @Test
    fun `median of even count`() {
        assertEquals(2.5, CommunitySource.median(listOf(4.0, 1.0, 2.0, 3.0))!!, 0.0)
    }
}

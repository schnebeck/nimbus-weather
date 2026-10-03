/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/FreshnessTest.kt
 * The app brought back from the background knows what has expired; the navigation bar and
 * full screen: how the app meets the system bars.
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

import dev.nimbus.weather.data.model.DataPart
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.repo.Freshness
import dev.nimbus.weather.ui.components.NavBarMode
import dev.nimbus.weather.ui.components.navBarMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessTest {
    private val min = 60_000L
    private val now = 1_790_900_000_000L

    @Test fun theForecastExpiresAfterTenMinutes() {
        assertFalse(Freshness.forecastDue(now - 5 * min, now, emptySet()))
        assertTrue(Freshness.forecastDue(now - 10 * min, now, emptySet()))
    }

    @Test fun olderPartsAreTriedAgainSoon() {
        // the background refresh's extras, a source that failed: again after 2 minutes, not 10
        val stale = setOf(DataPart.POLLEN)
        assertFalse(Freshness.forecastDue(now - 1 * min, now, stale))
        assertTrue(Freshness.forecastDue(now - 2 * min, now, stale))
    }

    /** The case of the screenshot: the look-back of 01:00 still shown at 09:28. */
    @Test fun theLookBackExpires() {
        assertTrue(Freshness.historyDue(now - (8 * 60 + 28) * min, now))
        assertFalse(Freshness.historyDue(now - 10 * min, now))
        assertTrue(Freshness.historyDue(now - 15 * min, now))
    }

    @Test fun navigationBarAndFullScreen() {
        assertEquals(NavBarMode.EDGE_TO_EDGE, navBarMode(fullscreen = false, buttons = false))   // gesture handle
        assertEquals(NavBarMode.RESERVED, navBarMode(fullscreen = false, buttons = true))       // back, home, recents
        assertEquals(NavBarMode.FULLSCREEN, navBarMode(fullscreen = true, buttons = true))
        // full screen and its button are off until chosen
        assertFalse(Settings().fullscreen)
        assertFalse(Settings().fullscreenButton)
    }
}

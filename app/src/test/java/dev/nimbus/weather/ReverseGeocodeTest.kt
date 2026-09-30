/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/ReverseGeocodeTest.kt
 * Tests for the place name from OpenStreetMap (Nominatim) reverse geocoding.
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

import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.data.repo.LocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseGeocodeTest {
    @Test fun townFromNominatim() {
        val p = LocationProvider.parseNominatim(JsonCodec.parseToJsonElement(Fixtures.text("nominatim_reverse.json")), 52.4256, 9.6177)!!
        assertEquals("Garbsen", p.name)
        assertEquals("Niedersachsen", p.region)
        assertEquals("DE", p.countryCode)
        assertTrue(p.isCurrentLocation)
        assertEquals(52.4256, p.latitude, 0.0)
    }

    @Test fun villageAndCountyFallbacks() {
        fun parse(json: String) = LocationProvider.parseNominatim(JsonCodec.parseToJsonElement(json), 0.0, 0.0)
        assertEquals("Wennigsen", parse("""{"address":{"village":"Wennigsen","county":"Region Hannover"}}""")!!.name)
        assertEquals("Region Hannover", parse("""{"address":{"county":"Region Hannover"}}""")!!.name)
        assertNull(parse("""{"error":"Unable to geocode"}"""))
    }
}

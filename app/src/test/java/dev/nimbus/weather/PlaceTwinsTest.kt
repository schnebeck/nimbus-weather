/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PlaceTwinsTest.kt
 * The same place more than once, each with its own model: the copies' ids, the model named.
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

import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.ui.places.PlaceTwins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceTwinsTest {
    private val cux = Place("geo-2939623", "Cuxhaven", latitude = 53.871, longitude = 8.694)

    /** "Eine etwas freakyge Sonderfunktion … dreimal den gleichen Ort mit unterschiedlichen lokalen modellen": ids of their own. */
    @Test fun eachCopyItsOwnId() {
        val second = PlaceTwins.copyOf(cux, listOf(cux))
        assertEquals("geo-2939623#2", second.id)
        assertEquals(cux.latitude to cux.longitude, second.latitude to second.longitude)
        // a copy of a copy: the next free number of the place
        assertEquals("geo-2939623#3", PlaceTwins.copyOf(second, listOf(cux, second)).id)
    }

    /** The model is named only where the place is in the list more than once. */
    @Test fun theModelNamedBesideTwinsOnly() {
        val settings = Settings(model = ForecastModel.BEST_MATCH)
        assertNull(PlaceTwins.label(cux, listOf(cux), settings))
        val met = PlaceTwins.copyOf(cux, listOf(cux)).copy(model = ForecastModel.MET_NORWAY)
        val all = listOf(cux, met)
        assertEquals("Open-Meteo", PlaceTwins.label(cux, all, settings))
        assertEquals("MET Nordic", PlaceTwins.label(met, all, settings))
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/PollenSelectionTest.kt
 * Tests for choosing the pollen types the pollen card shows.
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

import dev.nimbus.weather.data.model.PollenDay
import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.main.PollenGroup
import dev.nimbus.weather.ui.main.groupSelection
import dev.nimbus.weather.ui.main.onlyTypes
import dev.nimbus.weather.ui.main.toggled
import org.junit.Assert.assertEquals
import org.junit.Test

class PollenSelectionTest {
    private val all = PollenType.entries.toSet()

    @Test fun defaultShowsAllTypes() {
        assertEquals(all, Settings().pollenTypes)
        // Settings stored by older versions (without the field) still load with all types.
        val old = JsonCodec.decodeFromString(Settings.serializer(), """{"model":"DWD_ICON"}""")
        assertEquals(all, old.pollenTypes)
    }

    @Test fun groupsAndToggles() {
        assertEquals(PollenGroup.TREES.types, groupSelection(all, PollenGroup.TREES))
        assertEquals(all, groupSelection(PollenGroup.TREES.types, PollenGroup.TREES))   // tap again: all
        assertEquals(setOf(PollenType.BIRCH, PollenType.GRASS), toggled(setOf(PollenType.BIRCH), PollenType.GRASS))
        assertEquals(setOf(PollenType.BIRCH), toggled(setOf(PollenType.BIRCH), PollenType.BIRCH))   // last one stays
    }

    @Test fun forecastIsReducedToTheChosenTypes() {
        val p = PollenForecast(
            PollenSourceKind.DWD, null,
            listOf(PollenDay(0L, mapOf(PollenType.GRASS to 2f, PollenType.BIRCH to 1f))),
            listOf(0L), mapOf(PollenType.GRASS to listOf(10.0), PollenType.BIRCH to listOf(3.0)),
        )
        val trees = onlyTypes(p, PollenGroup.TREES.types)
        assertEquals(setOf(PollenType.BIRCH), trees.days[0].levels.keys)
        assertEquals(setOf(PollenType.BIRCH), trees.hourly.keys)
    }
}

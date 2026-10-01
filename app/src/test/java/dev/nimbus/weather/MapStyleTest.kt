/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/MapStyleTest.kt
 * Map style: areas below the radar, lines and names above it; split into the two parts.
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

import dev.nimbus.weather.ui.radar.MapStyle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
    private val style = Fixtures.json("openfreemap_dark_style.json").jsonObject
    private fun types(s: JsonObject) = (s["layers"] as JsonArray).map { ((it as JsonObject)["type"] as JsonPrimitive).content }
    private fun ids(s: JsonObject) = (s["layers"] as JsonArray).map { ((it as JsonObject)["id"] as JsonPrimitive).content }

    @Test fun fullPipelineKeepsAllLayers() {
        val out = MapStyle.fillsFirst(MapStyle.slate(MapStyle.localize(style, "de")))
        assertEquals(ids(style).toSet(), ids(out).toSet())
        // all areas first, then all lines and names
        val t = types(out)
        val cut = t.indexOfFirst { it == "line" || it == "symbol" }
        assertTrue(t.drop(cut).none { it == "fill" || it == "background" })
        assertTrue(ids(out).indexOf("building") < ids(out).indexOf("waterway"))
    }

    @Test fun rastersGoBetweenAreasAndLines() {
        val out = MapStyle.withRasters(MapStyle.fillsFirst(style), listOf(MapStyle.Raster("r", "https://x/{z}/{x}/{y}.png", 10, 1f)))
        val t = types(out); val i = ids(out).indexOf("r")
        assertTrue(t.take(i).none { it == "line" || it == "symbol" })
        assertEquals("line", t[i + 1])
    }

    @Test fun partsSplitTheMap() {
        val s = MapStyle.fillsFirst(style)
        assertTrue(types(MapStyle.part(s, MapStyle.Part.AREAS)).all { it == "fill" || it == "background" })
        assertTrue(types(MapStyle.part(s, MapStyle.Part.LINES)).all { it == "line" || it == "symbol" })
    }
}

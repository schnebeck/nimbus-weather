/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/MapStyle.kt
 * Map style: slate-blue base map with labels in the app language.
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

package dev.nimbus.weather.ui.radar

import dev.nimbus.weather.data.remote.getJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.maplibre.android.maps.Style

/**
 * The OpenFreeMap style prefers English place names (`name_en`). It is loaded once and its label
 * expressions are rewritten to the app language before any map (radar screen, preview card,
 * background prefetch) uses it, so all maps show "Niedersachsen" instead of "Lower Saxony".
 */
object MapStyle {
    private val mutex = Mutex()
    private val cache = HashMap<String, JsonObject>()

    /** A raster layer that is part of the style JSON itself (see [builder]). */
    data class Raster(
        val id: String, val url: String, val maxZoom: Int, val opacity: Float,
        val minZoom: Int? = null, val bounds: List<Double>? = null, val resampling: String = "linear",
    )

    /**
     * Style builder for the given language. [rasters] are embedded into the JSON below the first
     * label layer. MapSnapshotter needs this: with a JSON style it finishes loading synchronously
     * in its constructor, before sources could be added through the builder ("invalid native peer").
     */
    suspend fun builder(http: OkHttpClient, language: String, rasters: List<Raster> = emptyList(), part: Part = Part.ALL): Style.Builder {
        val style = runCatching { localized(http, language) }.getOrNull()
            ?: return Style.Builder().fromUri(STYLE_URL)
        return Style.Builder().fromJson(part(withRasters(style, rasters), part).toString())
    }

    /** The whole map, only its areas (below the radar), or only lines and names (above it, on transparent). */
    enum class Part { ALL, AREAS, LINES }

    fun part(style: JsonObject, part: Part): JsonObject {
        if (part == Part.ALL) return style
        val layers = (style["layers"] as? JsonArray)?.toList() ?: return style
        val kept = layers.filter { l ->
            val t = ((l as? JsonObject)?.get("type") as? JsonPrimitive)?.content
            if (part == Part.LINES) t in OVER_RADAR else t !in OVER_RADAR
        }
        return JsonObject(style + ("layers" to JsonArray(kept)))
    }

    private suspend fun localized(http: OkHttpClient, language: String): JsonObject = mutex.withLock {
        cache[language]?.let { return it }
        val out = fillsFirst(slate(localize(http.getJson(STYLE_URL).jsonObject, language)))
        cache[language] = out
        out
    }

    fun withRasters(style: JsonObject, rasters: List<Raster>): JsonObject {
        if (rasters.isEmpty()) return style
        val sources = (style["sources"] as? JsonObject ?: JsonObject(emptyMap())).toMutableMap()
        val newLayers = rasters.map { r ->
            sources[r.id] = buildJsonObject {
                put("type", "raster")
                put("tileSize", 512)
                put("maxzoom", r.maxZoom)
                r.minZoom?.let { put("minzoom", it) }
                r.bounds?.let { b -> put("bounds", buildJsonArray { b.forEach { add(JsonPrimitive(it)) } }) }
                put("tiles", buildJsonArray { add(JsonPrimitive(r.url)) })
            }
            buildJsonObject {
                put("id", r.id)
                put("type", "raster")
                put("source", r.id)
                put("paint", buildJsonObject {
                    put("raster-opacity", r.opacity)
                    put("raster-fade-duration", 0)
                    put("raster-resampling", r.resampling)
                })
            }
        }
        val layers = (style["layers"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        // Radar above all areas, below rivers, roads, borders and names
        layers.addAll(radarIndex(layers), newLayers)
        return JsonObject(style + mapOf("sources" to JsonObject(sources), "layers" to JsonArray(layers)))
    }

    /** Index of the first layer that is drawn above the radar: the first line or label layer. */
    fun radarIndex(layers: List<JsonElement>): Int =
        layers.indexOfFirst { (it as? JsonObject)?.get("type")?.let { t -> t is JsonPrimitive && t.content in OVER_RADAR } == true }
            .let { if (it < 0) layers.size else it }

    private val OVER_RADAR = setOf("line", "symbol")

    /**
     * Moves every area layer (fill) in front of the first line or label layer, keeping their own
     * order: the radar is inserted between the two groups, so areas such as buildings lie below
     * it and every line and name above it (in the original style buildings come after rivers).
     */
    fun fillsFirst(style: JsonObject): JsonObject {
        val layers = (style["layers"] as? JsonArray)?.toList() ?: return style
        fun type(e: JsonElement) = ((e as? JsonObject)?.get("type") as? JsonPrimitive)?.content
        val cut = radarIndex(layers)
        val late = layers.drop(cut)
        val ordered = layers.take(cut) + late.filter { type(it) !in OVER_RADAR } + late.filter { type(it) in OVER_RADAR }
        return JsonObject(style + ("layers" to JsonArray(ordered)))
    }

    /** Replaces every label expression that reads a name by "name in [language], else local name". */
    fun localize(style: JsonObject, language: String): JsonObject {
        val layers = (style["layers"] as? JsonArray)?.map { layer ->
            val l = layer as? JsonObject ?: return@map layer
            val layout = l["layout"] as? JsonObject ?: return@map layer
            val field = layout["text-field"] ?: return@map layer
            if (!field.toString().contains("\"name")) return@map layer
            JsonObject(l + ("layout" to JsonObject(layout + ("text-field" to nameExpression(language)))))
        } ?: return style
        return JsonObject(style + ("layers" to JsonArray(layers)))
    }

    // ---- colours ---------------------------------------------------------------------------

    /**
     * Re-tones the dark OpenFreeMap style to a mid slate blue that matches the app's glass cards:
     * land and areas mid-light, roads and borders clearly lighter for contrast, water a bit darker
     * and bluer, labels white with a dark halo. Weak rain (white/grey) stays visible on it.
     */
    fun slate(style: JsonObject): JsonObject {
        val layers = (style["layers"] as? JsonArray)?.map { layer ->
            val l = layer as? JsonObject ?: return@map layer
            val paint = l["paint"] as? JsonObject ?: return@map layer
            val id = (l["id"] as? JsonPrimitive)?.content ?: ""
            val type = (l["type"] as? JsonPrimitive)?.content ?: ""
            val newPaint = paint.mapValues { (prop, value) ->
                when {
                    prop == "text-color" -> JsonPrimitive("rgba(255,255,255,0.88)")
                    prop == "text-halo-color" -> JsonPrimitive("rgba(28,38,54,0.85)")
                    id == "water" && prop == "fill-color" -> JsonPrimitive("hsl(214,30%,27%)")
                    prop.endsWith("-color") -> recolor(value) { toSlate(it, isLine = type == "line") }
                    else -> value
                }
            }
            JsonObject(l + ("paint" to JsonObject(newPaint)))
        } ?: return style
        return JsonObject(style + ("layers" to JsonArray(layers)))
    }

    /** Applies [f] to every colour string inside a paint value (plain or inside expressions). */
    private fun recolor(v: JsonElement, f: (Rgba) -> Rgba): JsonElement = when (v) {
        is JsonPrimitive -> if (v.isString) parseColor(v.content)?.let { JsonPrimitive(f(it).css()) } ?: v else v
        is JsonArray -> JsonArray(v.map { recolor(it, f) })
        is JsonObject -> JsonObject(v.mapValues { recolor(it.value, f) })
    }

    data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double) {
        val lightness: Double get() = (maxOf(r, g, b) + minOf(r, g, b)) / 2
        fun css() = "rgba(${(r * 255).toInt()},${(g * 255).toInt()},${(b * 255).toInt()},${"%.2f".format(java.util.Locale.US, a)})"
    }

    fun toSlate(c: Rgba, isLine: Boolean): Rgba {
        val l = c.lightness
        val newL = if (isLine) (0.52 + 1.0 * l).coerceAtMost(0.86) else (0.34 + 0.8 * l).coerceAtMost(0.62)
        return hsl(214.0, if (isLine) 0.10 else 0.16, newL, c.a)
    }

    fun parseColor(s: String): Rgba? {
        val t = s.trim().lowercase()
        runCatching {
            if (t.startsWith("#")) {
                val h = t.drop(1).let { if (it.length == 3) it.map { ch -> "$ch$ch" }.joinToString("") else it }
                val v = h.take(6).toLong(16)
                val a = if (h.length == 8) h.substring(6, 8).toInt(16) / 255.0 else 1.0
                return Rgba(((v shr 16) and 0xFF) / 255.0, ((v shr 8) and 0xFF) / 255.0, (v and 0xFF) / 255.0, a)
            }
            val nums = t.substringAfter('(').substringBefore(')').split(',').map { it.trim().removeSuffix("%").toDouble() }
            return when {
                t.startsWith("rgb") -> Rgba(nums[0] / 255, nums[1] / 255, nums[2] / 255, nums.getOrElse(3) { 1.0 })
                t.startsWith("hsl") -> hsl(nums[0], nums[1] / 100, nums[2] / 100, nums.getOrElse(3) { 1.0 })
                else -> null
            }
        }
        return null
    }

    fun hsl(h: Double, s: Double, l: Double, a: Double): Rgba {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val hp = ((h % 360) + 360) % 360 / 60
        val x = c * (1 - kotlin.math.abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        val m = l - c / 2
        return Rgba(r1 + m, g1 + m, b1 + m, a)
    }

    private fun nameExpression(language: String): JsonElement = buildJsonArray {
        add(JsonPrimitive("coalesce"))
        add(get("name:$language"))
        add(get("name_$language"))
        add(get("name:latin"))
        add(get("name"))
    }

    private fun get(key: String) = buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive(key)) }
}

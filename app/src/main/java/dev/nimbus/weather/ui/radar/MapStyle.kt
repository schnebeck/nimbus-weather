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
    suspend fun builder(http: OkHttpClient, language: String, rasters: List<Raster> = emptyList()): Style.Builder {
        val style = runCatching { localized(http, language) }.getOrNull()
            ?: return Style.Builder().fromUri(STYLE_URL)
        return Style.Builder().fromJson(withRasters(style, rasters).toString())
    }

    private suspend fun localized(http: OkHttpClient, language: String): JsonObject = mutex.withLock {
        cache[language]?.let { return it }
        val out = localize(http.getJson(STYLE_URL).jsonObject, language)
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
        val firstLabel = layers.indexOfFirst { (it as? JsonObject)?.get("type")?.toString() == "\"symbol\"" }
            .let { if (it < 0) layers.size else it }
        layers.addAll(firstLabel, newLayers)
        return JsonObject(style + mapOf("sources" to JsonObject(sources), "layers" to JsonArray(layers)))
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

    private fun nameExpression(language: String): JsonElement = buildJsonArray {
        add(JsonPrimitive("coalesce"))
        add(get("name:$language"))
        add(get("name_$language"))
        add(get("name:latin"))
        add(get("name"))
    }

    private fun get(key: String) = buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive(key)) }
}

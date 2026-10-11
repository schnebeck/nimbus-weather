/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/LightningLayer.kt
 * Lightning on the radar as points: each flash where it struck, shrinking and fading from its minute on.
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

import dev.nimbus.weather.data.remote.LightningSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Where lightning struck, as points: each flash the Lightning Imager of Meteosat Third Generation
 * saw (as the DWD serves it) at the middle of its first group, from its minute on a white-yellow
 * dot that shrinks to nothing and fades within [FADE_MS]. The map draws them from the radar's time
 * ([at]) – playback moves them smoothly, nothing is drawn into pictures. Loaded per quarter hour of
 * the view's area; the quarters playback comes to next ahead ([ahead]).
 */
class LightningLayer(
    /** Runs on the main thread (the map's). */
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
    private val wfs: String = LightningSource.WFS,
) {
    /** A flash: where its first group lit, and when (the minute). */
    data class Flash(val lat: Double, val lon: Double, val time: Long)

    /** A quarter hour of flashes in the area; [complete]: over (and delivered) when it was asked. */
    private class Quarter(val flashes: List<Flash>, val asked: Long, val complete: Boolean)

    private var source: GeoJsonSource? = null
    private var layer: CircleLayer? = null
    @Volatile private var geo: FieldGeo? = null
    private val quarters = ConcurrentHashMap<String, Quarter>()
    private val loading = ConcurrentHashMap<String, Deferred<Quarter?>>()
    private var shownKeys: Set<String> = emptySet()

    /** The radar's time the flashes are shown at; null: none. */
    internal var shownTime: Long? = null
        private set

    /** The source and the layer of the points ([RadarMapController] puts the layer in its place). */
    fun source(): GeoJsonSource = GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyList())).also { source = it; shownKeys = emptySet() }

    fun layer(): CircleLayer = CircleLayer(LAYER_ID, SOURCE).withProperties(
        PropertyFactory.circleColor(COLOUR), PropertyFactory.circleStrokeColor(RIM), PropertyFactory.circleStrokeWidth(1.2f),
        PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP), PropertyFactory.visibility(Property.NONE),
    ).also { layer = it; it.setFilter(Expression.literal(false)) }

    /** The visible area changed: the points cover it with a margin. */
    fun setView(south: Double, north: Double, west: Double, east: Double) {
        val g = geo
        val inside = g != null && west >= g.west && east <= g.east && south >= g.south && north <= g.north
        if (inside) return
        geo = FieldGeo.forView(south, north, west, east, maxSide = 256, minPxKm = 0.0)
    }

    /** Shows the flashes at the radar's time [time] ([timeFor]); null: none (a time ahead of now). */
    fun at(time: Long?, now: Long = System.currentTimeMillis()) {
        shownTime = time
        val l = layer
        if (time == null) { l?.setFilter(Expression.literal(false)); return }
        // the quarters the flashes of the 15 minutes before [time] lie in
        quartersFor(time).forEach { ensure(it, now) }
        refresh()
        l ?: return
        val age = Expression.subtract(Expression.literal(time.toDouble()), Expression.get("t"))
        l.setFilter(Expression.all(Expression.gte(age, Expression.literal(0)), Expression.lt(age, Expression.literal(FADE_MS.toDouble()))))
        l.setProperties(
            PropertyFactory.circleRadius(Expression.interpolate(Expression.linear(), age, Expression.stop(0, look(0).first), Expression.stop(FADE_MS, look(FADE_MS).first))),
            PropertyFactory.circleOpacity(Expression.interpolate(Expression.linear(), age, Expression.stop(0, look(0).second), Expression.stop(FADE_MS, look(FADE_MS).second))),
            PropertyFactory.circleStrokeOpacity(Expression.interpolate(Expression.linear(), age, Expression.stop(0, look(0).second), Expression.stop(FADE_MS, look(FADE_MS).second))),
        )
    }

    /** The radar's times playback comes to next: their quarters loaded before it does. */
    fun ahead(times: List<Long>, now: Long = System.currentTimeMillis()) {
        times.flatMap { quartersFor(it) }.distinct().forEach { ensure(it, now) }
    }

    /** Whether the flashes of [time] are loaded (its quarters of the area). */
    internal fun ready(time: Long): Boolean = geo?.let { g -> quartersFor(time).all { quarters.containsKey(key(g, it)) } } == true

    /** The flashes shown at [time] of those loaded (newest first). */
    internal fun flashesAt(time: Long): List<Flash> = geo?.let { g ->
        quartersFor(time).flatMap { quarters[key(g, it)]?.flashes.orEmpty() }.filter { time - it.time in 0 until FADE_MS }.sortedByDescending { it.time }
    }.orEmpty()

    /** The quarter (start) [time]'s flashes of the last [FADE_MS] lie in. */
    private fun quartersFor(time: Long): List<Long> =
        listOf(Math.floorDiv(time - FADE_MS + 1, QUARTER_MS) * QUARTER_MS, Math.floorDiv(time, QUARTER_MS) * QUARTER_MS).distinct()

    /** The quarter from [start] of the area loaded – once, or again if it was not over then (a minute later). */
    private fun ensure(start: Long, now: Long) {
        val g = geo ?: return
        val k = key(g, start)
        val q = quarters[k]
        if (q != null && (q.complete || now - q.asked < AGAIN_MS)) return
        if (start > now) return
        loading.getOrPut(k) {
            scope.async {
                load(g, start, now).also { r ->
                    if (r != null) quarters[k] = r
                    loading.remove(k)
                    withContext(Dispatchers.Main.immediate) { refresh() }
                }
            }
        }
    }

    /** The points of the area loaded so far on the map (when they changed). */
    private fun refresh() {
        val g = geo ?: return
        val prefix = areaKey(g) + "@"
        val keys = quarters.keys.filter { it.startsWith(prefix) }.toSet()
        if (keys == shownKeys) return
        shownKeys = keys
        val src = source ?: return
        val features = keys.flatMap { quarters[it]?.flashes.orEmpty() }.map { f ->
            Feature.fromGeometry(Point.fromLngLat(f.lon, f.lat)).apply { addNumberProperty("t", f.time.toDouble()) }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private suspend fun load(g: FieldGeo, start: Long, now: Long): Quarter? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url(g, start)).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val flashes = flashes(r.body.string()) ?: return@use null
                Quarter(flashes, now, complete = start + QUARTER_MS + LATENCY_MS < now)
            }
        }.getOrNull()
    }

    /** The WFS request for the flash groups of the quarter hour from [start] over [g], the newest first. */
    internal fun url(g: FieldGeo, start: Long): String {
        val box = listOf(g.west, g.south, g.east, g.north).joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it) }
        return wfs.toHttpUrl().newBuilder()
            .addQueryParameter("service", "WFS").addQueryParameter("version", "2.0.0").addQueryParameter("request", "GetFeature")
            .addQueryParameter("typeNames", LightningSource.LAYER).addQueryParameter("outputFormat", "csv")
            .addQueryParameter("propertyName", "FLASH_ID,GROUP_END_TIME,GEOM").addQueryParameter("srsName", "CRS:84")
            .addQueryParameter("sortBy", "GROUP_END_TIME DESC").addQueryParameter("count", MAX_GROUPS.toString())
            .addQueryParameter(
                "cql_filter",
                "GROUP_END_TIME DURING ${Instant.ofEpochMilli(start)}/${Instant.ofEpochMilli(start + QUARTER_MS)} AND BBOX(GEOM,$box,'CRS:84')",
            )
            .build().toString()
    }

    private fun areaKey(g: FieldGeo) = "${g.minX.toLong()},${g.minY.toLong()},${g.maxX.toLong()},${g.maxY.toLong()}"
    private fun key(g: FieldGeo, start: Long) = areaKey(g) + "@" + start

    companion object {
        const val SOURCE = "lightning"
        const val LAYER_ID = "lightning"
        /** A flash shows this long after it struck, shrinking and fading. */
        const val FADE_MS = 15 * 60_000L
        const val QUARTER_MS = 15 * 60_000L
        /** Size of a flash's dot as it strikes (dp). */
        const val RADIUS_DP = 9f
        /** A flash reaches the DWD a minute or two after it struck. */
        const val LATENCY_MS = 2 * 60_000L
        /** A quarter not yet over when asked: asked again after this. */
        private const val AGAIN_MS = 60_000L
        /** Groups per quarter hour at most – the newest: a storm in the area stays far below. */
        const val MAX_GROUPS = 5000
        private const val COLOUR = "#FFFBE0"
        private const val RIM = "#FFE24D"

        /** A flash [ageMs] after it struck: its dot's radius (dp) and opacity – from full to nothing in [FADE_MS]. */
        fun look(ageMs: Long): Pair<Float, Float> {
            val k = 1f - (ageMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
            return RADIUS_DP * k to k
        }

        /** The flashes of the radar's time [frame] at [now]: up to it; null for a time ahead of now (no lightning is forecast). */
        fun timeFor(frame: Long, now: Long): Long? = if (frame > now + 150_000L) null else minOf(frame, now)

        /**
         * The flashes of the CSV answer: each at the middle of its first group's outline (lon/lat),
         * at that group's minute; null if it is no such answer (an error the server sends as text).
         */
        fun flashes(csv: String): List<Flash>? {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val head = lines.firstOrNull()?.split(',') ?: return null
            val id = head.indexOf("FLASH_ID"); val at = head.indexOf("GROUP_END_TIME"); val geom = head.indexOf("GEOM")
            if (id < 0 || at < 0 || geom < 0) return null
            val first = HashMap<String, Flash>()
            for (line in lines.drop(1)) {
                // the geometry comes last, quoted (commas inside)
                val cells = line.substringBefore(",\"").split(',')
                val wkt = line.substringAfter(",\"", "").substringBeforeLast('"')
                val flash = cells.getOrNull(id) ?: continue
                val time = cells.getOrNull(at)?.let { t -> runCatching { Instant.parse(if (t.endsWith("Z")) t else t + "Z").toEpochMilli() }.getOrNull() } ?: continue
                val (lat, lon) = middle(wkt) ?: continue
                if (first[flash]?.let { it.time <= time } != true) first[flash] = Flash(lat, lon, time)
            }
            return first.values.toList()
        }

        /** The middle of a WKT outline's points (lon lat pairs); null without any. */
        private fun middle(wkt: String): Pair<Double, Double>? {
            val pts = Regex("""(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)""").findAll(wkt).map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }.toList()
            if (pts.isEmpty()) return null
            return pts.sumOf { it.second } / pts.size to pts.sumOf { it.first } / pts.size
        }
    }
}

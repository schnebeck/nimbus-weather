/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/WeatherOverlays.kt
 * Temperature and wind overlays on the radar map.
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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import dev.nimbus.weather.data.model.TemperatureUnit
import dev.nimbus.weather.ui.main.Insights
import dev.nimbus.weather.util.Units
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.atan
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Temperature (smooth colour field + values) and wind (arrows + speed) overlays for the radar
 * map, drawn from the [WeatherGrid] for the hour of the currently shown radar frame.
 */
internal class WeatherOverlays(private val unit: TemperatureUnit) {
    private var style: Style? = null
    private var grid: WeatherGrid? = null
    private var hour = -1
    var showTemperature = false
        private set
    var showWind = false
        private set

    /** Installs the layers: colour field above the base map, labels and arrows on top. */
    fun install(style: Style, fieldBelow: String, linesBelow: String? = null) {
        this.style = style
        val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val quad = LatLngQuad(LatLng(1.0, 0.0), LatLng(1.0, 1.0), LatLng(0.0, 1.0), LatLng(0.0, 0.0))
        style.addSource(ImageSource(FIELD, quad, empty))
        style.addLayerBelow(
            RasterLayer(FIELD, FIELD).withProperties(
                PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.rasterResampling("linear"),
            ),
            fieldBelow,
        )
        // Isolines as vector lines above the radar, below roads and names: a thin light line on a
        // slightly wider dark one – low in contrast, yet clearly set off on any radar colour.
        style.addSource(GeoJsonSource(ISO))
        fun addLine(layer: org.maplibre.android.style.layers.LineLayer) =
            if (linesBelow != null) style.addLayerBelow(layer, linesBelow) else style.addLayerBelow(layer, fieldBelow)
        val width = Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(5, 0.6f), Expression.stop(10, 1.2f))
        addLine(
            org.maplibre.android.style.layers.LineLayer(ISO_CASING, ISO).withProperties(
                PropertyFactory.lineColor("#000000"),
                PropertyFactory.lineOpacity(0.28f),
                PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(5, 2.0f), Expression.stop(10, 2.8f))),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
        addLine(
            org.maplibre.android.style.layers.LineLayer(ISO, ISO).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineWidth(width),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
        style.addImage(ARROW, arrowBitmap(), true)
        style.addSource(GeoJsonSource(WIND))
        style.addLayer(
            SymbolLayer(WIND, WIND).withProperties(
                PropertyFactory.iconImage(ARROW),
                PropertyFactory.iconRotate(Expression.get("r")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconSize(Expression.get("s")),
                PropertyFactory.iconColor(windColor()),
                // Dark rim: the arrows stay visible on the light radar colours (yellow, white)
                PropertyFactory.iconHaloColor("#CC000000"),
                PropertyFactory.iconHaloWidth(1.6f),
                PropertyFactory.textField(Expression.get("v")),
                // The map style only serves Noto Sans; the MapLibre default font would not load.
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                PropertyFactory.textColor(windColor()),
                PropertyFactory.textHaloColor("#CC000000"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.iconOpacity(thinOut(thinZoom(WeatherGrid.STEP))),
                PropertyFactory.textOpacity(thinOut(thinZoom(WeatherGrid.STEP))),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
        style.addSource(GeoJsonSource(TEMP))
        style.addLayer(
            SymbolLayer(TEMP, TEMP).withProperties(
                PropertyFactory.textField(Expression.get("t")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textHaloColor("#CC000000"),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                // Temperature and wind share the grid points and are offset against each other, so
                // both layers may overlap – otherwise MapLibre's collision check hides one of them.
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textOpacity(thinOut(thinZoom(WeatherGrid.STEP))),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
    }

    fun setVisible(temperature: Boolean, wind: Boolean) {
        showTemperature = temperature
        showWind = wind
        val s = style ?: return
        s.getLayer(FIELD)?.setProperties(PropertyFactory.rasterOpacity(if (temperature) 0.6f else 0f))
        s.getLayer(ISO)?.setProperties(PropertyFactory.visibility(if (temperature) Property.VISIBLE else Property.NONE))
        s.getLayer(ISO_CASING)?.setProperties(PropertyFactory.visibility(if (temperature) Property.VISIBLE else Property.NONE))
        s.getLayer(TEMP)?.setProperties(
            PropertyFactory.visibility(if (temperature) Property.VISIBLE else Property.NONE),
            // With wind arrows at the same points the temperature moves up a bit.
            PropertyFactory.textOffset(if (wind) arrayOf(0f, -1.6f) else arrayOf(0f, 0f)),
        )
        s.getLayer(WIND)?.setProperties(PropertyFactory.visibility(if (wind) Property.VISIBLE else Property.NONE))
    }

    fun setGrid(g: WeatherGrid) {
        if (g === grid) return
        grid = g
        hour = -1
        computed.clear()
        // Thin out labels once the grid points get closer than ~90 dp on screen.
        val thin = thinOut(thinZoom(g.step))
        style?.getLayer(WIND)?.setProperties(PropertyFactory.iconOpacity(thin), PropertyFactory.textOpacity(thin))
        style?.getLayer(TEMP)?.setProperties(PropertyFactory.textOpacity(thin))
        (style?.getSource(FIELD) as? ImageSource)?.setCoordinates(
            LatLngQuad(LatLng(g.lat1, g.lon0), LatLng(g.lat1, g.lon1), LatLng(g.lat0, g.lon1), LatLng(g.lat0, g.lon0)),
        )
    }

    private val scope = kotlinx.coroutines.MainScope()
    private var job: kotlinx.coroutines.Job? = null
    /** Computed field + isolines per hour of the current grid (the loop revisits the same hours). */
    private val computed = HashMap<Int, Pair<Bitmap, List<Isolines.Segment>>>()

    fun dispose() = scope.cancel()

    /**
     * Shows the hour closest to [timeMs]. Field and isolines are computed off the UI thread in an
     * own scope: the radar loop changes the frame every 450 ms, which must not cancel a running
     * computation (it would never finish).
     */
    fun update(timeMs: Long) {
        val g = grid ?: return
        val h = g.hourIndex(timeMs)
        if (h == hour) return
        hour = h
        job?.cancel()
        job = scope.launch {
            val (bitmap, iso) = computed[h] ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // Bands and lines in the display unit, so every area holds exactly one label value.
                val display = FloatArray(g.temp[h].size) { i -> g.temp[h][i].let { if (it.isNaN()) it else Units.temperature(it.toDouble(), unit).toFloat() } }
                fieldBitmap(g, h, unit) to Isolines.compute(g, display, BAND, offset = BAND / 2)
            }.also { if (g === grid) computed[h] = it }
            val s = style ?: return@launch
            if (g !== grid || h != hour) return@launch           // superseded while computing
            apply(s, g, h, bitmap, iso)
        }
    }

    private fun apply(s: Style, g: WeatherGrid, h: Int, bitmap: Bitmap, iso: List<Isolines.Segment>) {
        (s.getSource(FIELD) as? ImageSource)?.setImage(bitmap)
        (s.getSource(ISO) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(iso.map {
                Feature.fromGeometry(org.maplibre.geojson.LineString.fromLngLats(listOf(Point.fromLngLat(it.lon1, it.lat1), Point.fromLngLat(it.lon2, it.lat2))))
            }),
        )
        val temps = ArrayList<Feature>(g.rows * g.cols)
        val winds = ArrayList<Feature>(g.rows * g.cols)
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val i = r * g.cols + c
            val p = Point.fromLngLat(g.lon0 + c * g.step, g.lat0 + r * g.step)
            val t = g.temp[h][i]
            val major = r % 2 == 0 && c % 2 == 0
            if (!t.isNaN()) temps += Feature.fromGeometry(p).apply {
                addStringProperty("t", Units.temp(t.toDouble(), unit))
                addBooleanProperty("major", major)
            }
            val v = g.windSpeed[h][i]
            val d = g.windDir[h][i]
            if (!v.isNaN() && !d.isNaN()) winds += Feature.fromGeometry(p).apply {
                addNumberProperty("r", (d + 180f) % 360f)          // arrow points where the wind blows to
                addNumberProperty("s", (0.7f + v / 50f).coerceIn(0.7f, 1.6f))
                addNumberProperty("kmh", v)
                addStringProperty("v", v.roundToInt().toString())
                addBooleanProperty("major", major)
            }
        }
        (s.getSource(TEMP) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(temps))
        (s.getSource(WIND) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(winds))
    }

    companion object {
        const val FIELD = "ov-temp-field"
        const val TEMP = "ov-temp"
        const val WIND = "ov-wind"
        const val ARROW = "ov-arrow"
        const val ISO = "ov-isolines"
        const val ISO_CASING = "ov-isolines-casing"

        /** Zoom at which points [step]° apart are ~90 dp apart (MapLibre world = 512 · 2^zoom dp). */
        fun thinZoom(step: Double): Double = kotlin.math.log2(90.0 * 360.0 / (512.0 * step))

        /** Below [zoom] only every second grid point (in both directions) is shown. */
        private fun thinOut(zoom: Double): Expression = Expression.step(
            Expression.zoom(),
            Expression.switchCase(Expression.get("major"), Expression.literal(1f), Expression.literal(0f)),
            Expression.stop(zoom, Expression.literal(1f)),
        )

        /**
         * Width of the temperature bands in the display unit. Band k covers [k − ½, k + ½): exactly
         * the values that are rounded to the label "k°", so labels and areas always agree.
         */
        const val BAND = 1.0

        fun band(display: Double): Int = kotlin.math.floor(display / BAND + 0.5).toInt()

        /** Colour of band [k] (display unit), taken from the °C colour scale. */
        fun bandColor(k: Int, unit: TemperatureUnit): Int {
            val v = k * BAND
            val c = if (unit == TemperatureUnit.FAHRENHEIT) (v - 32) * 5 / 9 else v
            return Insights.temperatureColor(c).toArgb()
        }

        /** Wind colour by speed (km/h): calm white → Bft 6 yellow → gale orange → storm red. */
        private fun windColor(): Expression = Expression.step(
            Expression.get("kmh"), Expression.color(0xFFFFFFFF.toInt()),
            Expression.stop(39, Expression.color(0xFFFFE08A.toInt())),
            Expression.stop(62, Expression.color(0xFFFFA54A.toInt())),
            Expression.stop(89, Expression.color(0xFFFF5A4A.toInt())),
        )

        private fun arrowBitmap(): Bitmap {
            val s = 48
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
            p.strokeWidth = 5f
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(s / 2f, s * 0.9f, s / 2f, s * 0.3f, p)
            val head = Path().apply {
                moveTo(s / 2f, s * 0.06f)
                lineTo(s * 0.26f, s * 0.42f)
                lineTo(s * 0.74f, s * 0.42f)
                close()
            }
            c.drawPath(head, p)
            return bmp
        }

        private fun mercY(lat: Double) = ln(tan(Math.PI / 4 + Math.toRadians(lat) / 2))
        private fun latOf(y: Double) = Math.toDegrees(2 * atan(exp(y)) - Math.PI / 2)

        /**
         * Temperature as areas of equal temperature: colour bands of [BAND]°, sampled in Mercator
         * space so it lines up with the map. The isolines are drawn separately as vectors.
         */
        fun fieldBitmap(g: WeatherGrid, hour: Int, unit: TemperatureUnit = TemperatureUnit.CELSIUS): Bitmap {
            val w = 512
            val h = 640
            val field = g.temp[hour]
            val yTop = mercY(g.lat1)
            val yBottom = mercY(g.lat0)
            val bands = IntArray(w * h) { Int.MIN_VALUE }
            val lons = DoubleArray(w) { g.lon0 + (g.lon1 - g.lon0) * (it + 0.5) / w }
            for (row in 0 until h) {
                val lat = latOf(yTop + (yBottom - yTop) * (row + 0.5) / h)
                for (col in 0 until w) {
                    g.sample(field, lat, lons[col])?.let { bands[row * w + col] = band(Units.temperature(it.toDouble(), unit)) }
                }
            }
            val colors = HashMap<Int, Int>()
            val px = IntArray(w * h)
            for (i in bands.indices) {
                val b = bands[i]
                if (b == Int.MIN_VALUE) continue
                px[i] = colors.getOrPut(b) { bandColor(b, unit) }
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
    }
}

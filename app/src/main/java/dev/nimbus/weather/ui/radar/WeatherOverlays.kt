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
    fun install(style: Style, fieldBelow: String) {
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
        style.addImage(ARROW, arrowBitmap(), true)
        style.addSource(GeoJsonSource(WIND))
        style.addLayer(
            SymbolLayer(WIND, WIND).withProperties(
                PropertyFactory.iconImage(ARROW),
                PropertyFactory.iconRotate(Expression.get("r")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconSize(Expression.get("s")),
                PropertyFactory.iconColor(windColor()),
                PropertyFactory.iconHaloColor("#99000000"),
                PropertyFactory.iconHaloWidth(1.2f),
                PropertyFactory.textField(Expression.get("v")),
                // The map style only serves Noto Sans; the MapLibre default font would not load.
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                PropertyFactory.textColor(windColor()),
                PropertyFactory.textHaloColor("#B3000000"),
                PropertyFactory.textHaloWidth(1.2f),
                PropertyFactory.textOptional(true),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
        style.addSource(GeoJsonSource(TEMP))
        style.addLayer(
            SymbolLayer(TEMP, TEMP).withProperties(
                PropertyFactory.textField(Expression.get("t")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textHaloColor("#B3000000"),
                PropertyFactory.textHaloWidth(1.4f),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
    }

    fun setVisible(temperature: Boolean, wind: Boolean) {
        showTemperature = temperature
        showWind = wind
        val s = style ?: return
        s.getLayer(FIELD)?.setProperties(PropertyFactory.rasterOpacity(if (temperature) 0.4f else 0f))
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
        (style?.getSource(FIELD) as? ImageSource)?.setCoordinates(
            LatLngQuad(LatLng(g.lat1, g.lon0), LatLng(g.lat1, g.lon1), LatLng(g.lat0, g.lon1), LatLng(g.lat0, g.lon0)),
        )
    }

    /** Shows the hour closest to [timeMs]; cheap if the hour did not change. */
    fun update(timeMs: Long) {
        val g = grid ?: return
        val s = style ?: return
        val h = g.hourIndex(timeMs)
        if (h == hour) return
        hour = h
        (s.getSource(FIELD) as? ImageSource)?.setImage(fieldBitmap(g, h))
        val temps = ArrayList<Feature>(g.rows * g.cols)
        val winds = ArrayList<Feature>(g.rows * g.cols)
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val i = r * g.cols + c
            val p = Point.fromLngLat(g.lon0 + c * g.step, g.lat0 + r * g.step)
            val t = g.temp[h][i]
            if (!t.isNaN()) temps += Feature.fromGeometry(p).apply { addStringProperty("t", Units.temp(t.toDouble(), unit)) }
            val v = g.windSpeed[h][i]
            val d = g.windDir[h][i]
            if (!v.isNaN() && !d.isNaN()) winds += Feature.fromGeometry(p).apply {
                addNumberProperty("r", (d + 180f) % 360f)          // arrow points where the wind blows to
                addNumberProperty("s", (0.7f + v / 50f).coerceIn(0.7f, 1.6f))
                addNumberProperty("kmh", v)
                addStringProperty("v", v.roundToInt().toString())
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

        /** Smooth temperature colour field, sampled in Mercator space so it lines up with the map. */
        fun fieldBitmap(g: WeatherGrid, hour: Int): Bitmap {
            val w = 160
            val h = 160
            val field = g.temp[hour]
            val yTop = mercY(g.lat1)
            val yBottom = mercY(g.lat0)
            val px = IntArray(w * h)
            for (row in 0 until h) {
                val lat = latOf(yTop + (yBottom - yTop) * (row + 0.5) / h)
                for (col in 0 until w) {
                    val lon = g.lon0 + (g.lon1 - g.lon0) * (col + 0.5) / w
                    val t = g.sample(field, lat, lon)
                    px[row * w + col] = if (t == null) 0 else Insights.temperatureColor(t.toDouble()).toArgb()
                }
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
    }
}

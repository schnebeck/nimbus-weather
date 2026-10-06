/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarMapController.kt
 * The MapLibre side of the radar screen: satellite and warning layers, the location.
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

import dev.nimbus.weather.data.model.Place
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Point

/**
 * MapLibre loads the tiles of every layer whose visibility is "visible" – even at opacity 0.
 * Layers that must not load yet are therefore switched to visibility "none".
 */
private fun RasterLayer.state(visible: Boolean, opacity: Float) = setProperties(
    PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
    PropertyFactory.rasterOpacity(opacity),
)

/**
 * Holds the MapLibre objects: satellite and warning layers and the location. The radar itself is
 * one picture between them, drawn by the [RadarPlayer].
 */
internal class RadarMapController {
    var map: MapLibreMap? = null
    var style: Style? = null
    var satellite = false
    var warnings = false
    /** First layer drawn above the radar (lines and names); overlays such as isolines go below it. */
    var anchor: String? = null

    /** Satellite and warning layers; the radar picture is inserted between them. */
    fun installBase(style: Style, satellite: SatelliteLayer) {
        this.style = style
        // No animated property changes (MapLibre fades every change over 300 ms by default)
        style.transition = org.maplibre.android.style.layers.TransitionOptions(0, 0, false)
        // Satellite, radar and warnings between the areas and the lines: rivers, roads, borders
        // and names stay visible on top of the (opaque) radar colours
        val below = style.layers.firstOrNull { it is SymbolLayer || it is org.maplibre.android.style.layers.LineLayer }?.id
        anchor = below
        fun add(layer: RasterLayer) = if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        // the satellite: one picture of the view at the radar's time (EUMETSAT, see SatelliteLayer)
        style.addSource(satellite.source())
        satellite.install(style)
        add(RasterLayer("sat", SatelliteLayer.SOURCE).withProperties(PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.visibility(Property.NONE)))
        style.addSource(RasterSource("warn", TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.WARN_LAYER, null)).apply {
            maxZoom = 10f
            setBounds(5.5f, 47.0f, 15.5f, 55.2f)
        }, 512).apply { prefetchZoomDelta = 0 })
        add(RasterLayer("warn", "warn").withProperties(PropertyFactory.rasterOpacity(0f), PropertyFactory.rasterFadeDuration(0f), PropertyFactory.visibility(Property.NONE)))
    }

    fun addLocation(style: Style, place: Place) {
        style.addSource(GeoJsonSource("me", Point.fromLngLat(place.longitude, place.latitude)))
        style.addLayer(
            CircleLayer("me-halo", "me").withProperties(
                PropertyFactory.circleRadius(14f), PropertyFactory.circleColor("#3D8BFF"), PropertyFactory.circleOpacity(0.3f),
            ),
        )
        style.addLayer(
            CircleLayer("me", "me").withProperties(
                PropertyFactory.circleRadius(6f), PropertyFactory.circleColor("#3D8BFF"),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
    }

    fun setOverlays(sat: Boolean, warn: Boolean) {
        val s = style ?: return
        satellite = sat
        warnings = warn
        (s.getLayer("sat") as? RasterLayer)?.state(sat, if (sat) 0.75f else 0f)
        (s.getLayer("warn") as? RasterLayer)?.state(warn, if (warn) 0.55f else 0f)
    }
}

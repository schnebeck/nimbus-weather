package dev.nimbus.weather.ui.radar

import android.content.Context
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** Static map + latest radar for the preview card, rendered off-screen by MapLibre. */
object RadarSnapshot {
    fun create(
        context: Context, lat: Double, lon: Double, widthDp: Int, heightDp: Int, pixelRatio: Float,
        timeline: RadarTimeline?, frame: RadarFrame?,
    ): MapSnapshotter {
        val style = Style.Builder().fromUri(STYLE_URL)
        if (timeline != null && frame != null) {
            frame.rainViewerPath?.let { path ->
                style.withSource(RasterSource("rv", TileSet("2.2.0", RadarSources.rainViewerTileUrl(timeline.rainViewerHost, path)).apply { maxZoom = 7f }, 512))
                style.withLayer(RasterLayer("rv", "rv").withProperties(PropertyFactory.rasterOpacity(0.85f)))
            }
            frame.dwdTime?.let { t ->
                style.withSource(RasterSource("dwd", TileSet("2.2.0", RadarSources.dwdTileUrl(RadarSources.DWD_LAYER, t)).apply {
                    maxZoom = 10f
                    setBounds(1.4f, 45.6f, 18.8f, 56.3f)
                }, 512))
                style.withLayer(RasterLayer("dwd", "dwd").withProperties(PropertyFactory.rasterOpacity(0.85f), PropertyFactory.rasterResampling("linear")))
            }
        }
        val options = MapSnapshotter.Options(widthDp, heightDp)
            .withStyleBuilder(style)
            .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(6.4).build())
            .withPixelRatio(pixelRatio)
            .withLogo(false)
        return MapSnapshotter(context, options)
    }
}

package dev.nimbus.weather.ui.radar

import android.content.Context
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/** Static map + latest radar for the preview card, rendered off-screen by MapLibre. */
object RadarSnapshot {
    /** Radar frame(s) as style layers; [opacity] 0 loads the tiles without drawing (prefetch). */
    fun rasters(timeline: RadarTimeline, frames: List<Pair<Int, RadarFrame>>, opacity: Float): List<MapStyle.Raster> =
        frames.flatMap { (i, f) ->
            listOfNotNull(
                f.rainViewerPath?.let { MapStyle.Raster("rv$i", RadarSources.rainViewerTileUrl(timeline.rainViewerHost, it), 7, opacity) },
                f.dwdTime?.let {
                    MapStyle.Raster(
                        "dwd$i", RadarSources.dwdTileUrl(RadarSources.DWD_LAYER, it), 10, opacity,
                        minZoom = 3, bounds = listOf(1.4, 45.6, 18.8, 56.3),
                    )
                },
            )
        }

    fun create(context: Context, style: Style.Builder, lat: Double, lon: Double, widthDp: Int, heightDp: Int, pixelRatio: Float): MapSnapshotter {
        val options = MapSnapshotter.Options(widthDp, heightDp)
            .withStyleBuilder(style)
            .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(6.4).build())
            .withPixelRatio(pixelRatio)
            .withLogo(false)
        return MapSnapshotter(context, options)
    }
}

/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/RadarSnapshot.kt
 * Off-screen map snapshot with the latest radar for the preview card.
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

import android.content.Context
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/** The preview card's base map, rendered off-screen by MapLibre (the radar is drawn above it: [RadarPicture.still]). */
object RadarSnapshot {
    fun create(context: Context, style: Style.Builder, lat: Double, lon: Double, widthDp: Int, heightDp: Int, pixelRatio: Float): MapSnapshotter {
        val options = MapSnapshotter.Options(widthDp, heightDp)
            .withStyleBuilder(style)
            .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(6.4).build())
            .withPixelRatio(pixelRatio)
            .withLogo(false)
        return MapSnapshotter(context, options)
    }
}

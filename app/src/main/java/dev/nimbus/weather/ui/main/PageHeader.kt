/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/PageHeader.kt
 * The weather page's header: place, temperature, condition, station line – and the pin of my location.
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

package dev.nimbus.weather.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import dev.nimbus.weather.ui.components.shrinkToFit
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.ui.components.CardStatus
import dev.nimbus.weather.ui.components.WeatherIcon
import dev.nimbus.weather.ui.components.windiness
import androidx.compose.foundation.layout.width
import dev.nimbus.weather.util.Texts
import dev.nimbus.weather.util.Units

@Composable
internal fun PageHeader(
    data: WeatherData, progress: () -> Float, statusTop: androidx.compose.ui.unit.Dp, compact: Boolean = false,
    location: LocationMark? = null, onLocate: () -> Unit = {},
    /** Space above it: below the status bar and the top bar – none in the header pane (sideways). */
    top: androidx.compose.ui.unit.Dp = statusTop + HeaderTop,
    modelLabel: String? = null,
    onHeight: (Int) -> Unit = {},
) {
    val s = LocalSettings.current
    val c = data.current
    val today = data.daily.lastOrNull { it.date <= System.currentTimeMillis() } ?: data.daily.firstOrNull()
    val condition = stringResource(Texts.condition(c.condition, c.isDay))
    // Straight on the sky: pure white, large, with a dark halo as strong as the brightest sky
    // behind needs (Legibility) – the sky itself stays as it is
    val header = dev.nimbus.weather.ui.components.LocalHeaderStyle.current
    val TextShadow = header.shadow
    Column(
        Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) }.padding(top = top, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val locate = stringResource(R.string.location_locate)
        Row(
            if (location != null) Modifier.clickable(onClickLabel = locate, onClick = onLocate) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (data.place.isCurrentLocation) LocationPin(location)
            val nameSize = if (compact) 26.sp else 32.sp
            Text(
                data.place.name, Modifier.weight(1f, fill = false).alignByBaseline(),
                fontSize = nameSize, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
            )
            // the status dot after the name, as on the cards after their title – not on the pin
            if (location != null) LocationDot(location, nameSize)
        }
        dev.nimbus.weather.ui.places.TwinModelLine(modelLabel, androidx.compose.ui.text.TextStyle(shadow = TextShadow))
        Box(contentAlignment = Alignment.TopCenter) {
            // Collapsed line: "12° | Cloudy"
            Text(
                "${Units.tempFull(c.temperature, s.temperatureUnit)} | $condition",
                Modifier.graphicsLayer { alpha = ((progress() - 0.65f) / 0.35f).coerceIn(0f, 1f) },
                fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White,
                style = androidx.compose.ui.text.TextStyle(shadow = TextShadow),
            )
            Column(
                Modifier.graphicsLayer {
                    val p = progress()
                    alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                    translationY = -p * 60.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Combined symbol (sun/moon, clouds, rain/snow, wind), temperature with unit and the
                // day's max/min stacked like on a weather station display – shrunk as a whole
                // where it does not fit (a narrow phone, a large font), never cut off
                Row(Modifier.shrinkToFit(), verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(
                        c.condition, c.isDay, size = if (compact) 46.dp else 64.dp, wind = windiness(c.windSpeed, c.windGust),
                        description = condition,
                    )
                    Spacer(Modifier.width(8.dp))
                    dev.nimbus.weather.ui.components.BigTemperature(c.temperature, s.temperatureUnit, if (compact) 56.sp else 96.sp, shadow = TextShadow)
                    if (today != null) {
                        Spacer(Modifier.width(12.dp))
                        dev.nimbus.weather.ui.components.MaxMinStack(today.tempMax, today.tempMin, s.temperatureUnit, fontSize = 18.sp, shadow = TextShadow, labelColor = Color.White)
                    }
                }
                Text(condition, fontSize = if (compact) 18.sp else 21.sp, fontWeight = FontWeight.Medium, color = Color.White, style = androidx.compose.ui.text.TextStyle(shadow = TextShadow))
                if (c.stationName != null && c.stationDistanceKm != null) {
                    val explain = dev.nimbus.weather.ui.components.LocalExplainCase.current
                    val sources = nowSourcesText(c)
                    // small text: on a pill of its own glass, dark enough for the sky behind
                    Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(header.pill)
                            .clickable { explain(dev.nimbus.weather.ui.components.Term.STATION, sources) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // wrapped onto two lines (a narrow pane, a large font) the ⓘ keeps its room
                        // the station's name kept together (it moves to the second line as a whole, the
                        // distance stays with it), the lines close and the pill only as wide as they are
                        dev.nimbus.weather.ui.components.TightText(
                            stringResource(
                                R.string.measured_at_station, (c.stationNetwork ?: dev.nimbus.weather.data.model.StationNetwork.DWD).label,
                                keptTogether(c.stationName), Units.oneDecimal(c.stationDistanceKm),
                            ),
                            Modifier.weight(1f, fill = false).testTag("station-line"),
                            androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Color.White, textAlign = TextAlign.Center),
                        )
                        Icon(androidx.compose.material.icons.Icons.Outlined.Info, null, tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(12.dp))
                    }
                }
                // the device's location is off: "my location" cannot follow – one tap to its setting
                if (location?.off == true) {
                    Text(
                        stringResource(R.string.location_off),
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(header.pill)
                            .clickable(onClick = onLocate).padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Where the position of "my location" stands: [current] taken within
 * [dev.nimbus.weather.data.repo.Freshness.LOCATION_MS], [searching] being looked for, [off] the
 * device's location switched off.
 */
data class LocationMark(val current: Boolean, val searching: Boolean, val off: Boolean)

/** Where the position of "my location" stands at [now] (see [LocationMark]). */
internal fun locationMark(state: dev.nimbus.weather.ui.UiState, shelf: dev.nimbus.weather.data.repo.Shelf): LocationMark = LocationMark(
    // the position's record: out of date when its time is up or it is asked for anew
    current = shelf.position.state == dev.nimbus.weather.data.repo.RecordState.CURRENT,
    searching = state.locationStatus == dev.nimbus.weather.ui.LocationStatus.LOADING,
    off = state.locationOff,
)

/**
 * The pin of "my location"; while its position is looked for it breathes. Its status
 * ([LocationDot]) stands apart from it: on the pin the dot covered half the pin's point.
 */
@Composable
internal fun LocationPin(location: LocationMark?, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val pulse = if (location?.searching == true) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "locating")
        t.animateFloat(
            1f, 0.35f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "pin",
        )
    } else null
    val label = stringResource(
        when {
            location == null -> R.string.my_location
            location.searching -> R.string.location_searching
            location.current -> R.string.location_current
            else -> R.string.location_not_current
        },
    )
    Icon(
        Icons.Rounded.LocationOn, label, tint = Color.White,
        modifier = Modifier.padding(end = 4.dp).testTag("location-pin").size(size).graphicsLayer { alpha = pulse?.value ?: 1f },
    )
}

/**
 * The status dot of "my location", the one of the cards: green, the position is current; yellow,
 * it is older (the page shows the place last found). Behind the name (aligned by its baseline in
 * a [androidx.compose.foundation.layout.RowScope]) with its centre at the height of the capitals
 * of [fontSize] – like an index, not on the pin.
 */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.LocationDot(location: LocationMark, fontSize: androidx.compose.ui.unit.TextUnit) {
    val capPx = with(LocalDensity.current) { fontSize.toPx() * CAP_HEIGHT }
    dev.nimbus.weather.ui.components.StatusDot(
        if (location.current) CardStatus.FRESH else CardStatus.STALE,
        Modifier.padding(start = 6.dp).alignBy { it.measuredHeight / 2 + capPx.toInt() }.testTag("location-dot").clearAndSetSemantics { },
    )
}

/** Height of the capitals in the app's font (Roboto: 1456 of 2048 units per em). */
internal const val CAP_HEIGHT = 0.711f

/** [name] that does not break inside (spaces and hyphens kept): it wraps as a whole. */
internal fun keptTogether(name: String): String = name.replace(' ', '\u00A0').replace('-', '\u2011')

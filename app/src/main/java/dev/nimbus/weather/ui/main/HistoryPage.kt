/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/main/HistoryPage.kt
 * The look back: measured values of the past days compared with the forecast – the page with
 * its header, its sky in the day's parts and its cards.
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

import dev.nimbus.weather.ui.components.statusBarsStable
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.Condition
import dev.nimbus.weather.data.model.ForecastModel
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.remote.DaySummary
import dev.nimbus.weather.data.remote.HistoryDay
import dev.nimbus.weather.ui.PlaceState
import dev.nimbus.weather.ui.background.SkyScene
import dev.nimbus.weather.ui.background.WeatherBackground
import dev.nimbus.weather.ui.components.GlassCard
import dev.nimbus.weather.ui.theme.NimbusColors
import dev.nimbus.weather.util.TimeFormat

fun modelName(m: ForecastModel) = m.part?.name ?: when (m) {
    ForecastModel.DWD_ICON -> "DWD ICON"
    ForecastModel.ECMWF -> "ECMWF IFS"
    ForecastModel.METEO_FRANCE -> "Météo-France"
    else -> "Open-Meteo"
}

/**
 * One past day: [dayIndex] 0 = two days ago … HISTORY_DAYS − 1 = today so far.
 * Measured DWD station values are compared with what the model had predicted for the same hours.
 */
@Composable
fun HistoryPage(
    place: Place, state: PlaceState?, settings: Settings, dayIndex: Int, isActive: Boolean, onRetry: () -> Unit,
    onOpenRadarDay: (Long) -> Unit = {},
    /** For "my location": where its position stands – the dot behind the name, as on the weather page. */
    location: LocationMark? = null,
    /** The model, for a place in the list more than once ([dev.nimbus.weather.ui.places.PlaceTwins.label]). */
    modelLabel: String? = null,
) {
    val context = LocalContext.current
    val history = state?.history
    val day = history?.days?.getOrNull(dayIndex)
    val summary = remember(day) { day?.takeIf { it.hours.isNotEmpty() }?.let { DaySummary.of(it, history.fetchedAt) } }
    val tf = remember(history?.zone) { TimeFormat(history?.zone?.id ?: (state?.data?.timezone ?: "UTC"), DateFormat.is24HourFormat(context)) }
    // The day in parts (early … night), growing in the course of today
    val parts = remember(day, history?.fetchedAt) {
        if (day == null) emptyList() else dev.nimbus.weather.data.remote.DayParts.of(day, history.zone, history.fetchedAt)
    }
    // The sky shows the parts one after the other, every 5 s – the row of the table it shows is lit
    var shown by remember(parts) { androidx.compose.runtime.mutableIntStateOf(parts.lastIndex.coerceAtLeast(0)) }
    // (while the app is shown: behind the lock screen it went on every 5 s)
    dev.nimbus.weather.ui.components.LaunchedWhileShown(parts, isActive) {
        if (!isActive || parts.size < 2) return@LaunchedWhileShown
        while (true) {
            kotlinx.coroutines.delay(PART_SHOW_MS)
            shown = (shown + 1) % parts.size
        }
    }
    // Same sky as the main page at the current time (day/night, twilight, moon) – only the weather
    // is that of the shown day. A bright day sky at night made the glass cards pale.
    val sky = remember(summary, day?.date, state?.data) {
        val (season, autumn) = SkyScene.seasonOf(day?.date ?: java.time.LocalDate.now(), place.latitude < 0)
        val base = state?.data?.let { SkyScene.from(it) } ?: placeholderScene()
        base.copy(
            condition = summary?.condition ?: Condition.CLOUDY,
            wind = ((summary?.meanWind ?: 10.0) / 60.0).toFloat().coerceIn(0.08f, 1f),
            gustiness = 0f, pollen = 0f,
            season = season, autumnProgress = autumn, temperature = summary?.tempMax ?: 15.0,
        )
    }
    // each part in its weather and in the light of its time of day (the middle of the part)
    val skies = remember(sky, parts, day?.date) {
        parts.map { p ->
            val mid = day!!.date.atStartOfDay(history.zone).toInstant().toEpochMilli() + (p.part.from + p.part.to) * 1_800_000L
            SkyScene.atTime(sky.copy(condition = p.condition), mid, place.latitude, place.longitude)
        }.ifEmpty { listOf(sky) }
    }
    val scene = skies.getOrNull(shown) ?: skies.first()
    // Glass and header shade for the brightest sky of the round: they do not pulse every 5 s
    val cardFill = skies.maxBy { it.cardFill.alpha }.cardFill
    val headerStyle = dev.nimbus.weather.ui.components.HeaderStyle(skies.maxOf { it.headerHalo }, skies.maxBy { it.headerPill.alpha }.headerPill)
    val statusTop = WindowInsets.statusBarsStable.asPaddingValues().calculateTopPadding()
    val navBottom = dev.nimbus.weather.ui.components.navBarBottom()
    val title = stringResource(
        when (HISTORY_DAYS - 1 - dayIndex) {
            0 -> R.string.history_today
            1 -> R.string.history_yesterday
            else -> R.string.history_day_before
        },
    )

    Box(Modifier.fillMaxSize()) {
        // one weather into the next: clouds and rain fade over instead of switching
        androidx.compose.animation.Crossfade(scene, animationSpec = androidx.compose.animation.core.tween(1200), label = "sky") { sc ->
            WeatherBackground(sc, animate = isActive, motion = settings.animationsEnabled)
        }
        // the look-back's cards say whether they are current: yellow while loading or expired
        // and, for "my location", while its position is not current
        // the look-back's record (out of date while loading, past its time, or "my location" not confirmed)
        val status = if (LocalShelf.current.lookBackState(place.id) != dev.nimbus.weather.data.repo.RecordState.CURRENT)
            dev.nimbus.weather.ui.components.CardStatus.STALE else dev.nimbus.weather.ui.components.CardStatus.FRESH
        CompositionLocalProvider(
            LocalSettings provides settings, LocalTimeFormat provides tf,
            dev.nimbus.weather.ui.components.LocalCardFill provides cardFill,
            dev.nimbus.weather.ui.components.LocalHeaderStyle provides headerStyle,
            dev.nimbus.weather.ui.components.LocalCardStatus provides status,
        ) {
            val clipTop = with(androidx.compose.ui.platform.LocalDensity.current) { (statusTop + 52.dp).toPx() }
            // a phone held sideways: the header as a pane of its own on the left (as on the weather
            // page), the day's parts and the cards beside it; nothing under the camera's cut-out
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            val sideways = sideways(config.screenWidthDp, config.screenHeightDp)
            val paneWidth = if (sideways) headerPaneWidth(LocalContentWidth.current) else 0.dp
            val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
            val cutout = androidx.compose.foundation.layout.WindowInsets.displayCutout
                .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal).asPaddingValues()
            val cutStart = cutout.calculateStartPadding(dir)
            val cutEnd = cutout.calculateEndPadding(dir)
            if (sideways) {
                Box(
                    Modifier.width(cutStart + paneWidth).fillMaxHeight().fullscreenByDoubleTap()
                        .padding(start = cutStart + 16.dp, end = 16.dp, top = statusTop + HeaderTop),
                    contentAlignment = Alignment.Center,
                ) { HistoryHeader(place, title, day, summary, tf, emptyList(), shown, location, modelLabel) }
            }
            // Cards keep their title at the line below the top bar and slide away under it (GlassCard)
            val listTop = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            CompositionLocalProvider(dev.nimbus.weather.ui.components.LocalPinLine provides { listTop.floatValue + clipTop }) {
            LazyColumn(
                // Content scrolls away below the top bar instead of running under menu and radar button.
                Modifier.fillMaxSize().padding(start = cutStart + paneWidth, end = cutEnd).wrapContentWidth().widthIn(max = 760.dp)
                    .onGloballyPositioned { listTop.floatValue = it.positionInRoot().y }
                    .drawWithContent { clipRect(top = clipTop) { this@drawWithContent.drawContent() } },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusTop + HeaderTop, bottom = navBottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!sideways) item(key = "header") { Box(Modifier.fullscreenByDoubleTap()) { HistoryHeader(place, title, day, summary, tf, parts, shown, location, modelLabel) } }
                else if (parts.isNotEmpty()) item(key = "parts") {
                    DayPartsTable(parts, shown, androidx.compose.ui.text.TextStyle(shadow = dev.nimbus.weather.ui.components.LocalHeaderStyle.current.shadow))
                }
                when {
                    day == null && state?.historyError == true -> item(key = "error") { HistoryMessage(stringResource(R.string.history_error), onRetry) }
                    day == null -> item(key = "loading") {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.history_loading), color = NimbusColors.Secondary, fontSize = 14.sp)
                        }
                    }
                    summary == null -> item(key = "empty") { HistoryMessage(stringResource(R.string.history_empty), null) }
                    else -> {
                        item(key = "summary") { SummaryCard(summary, history, settings, tf) }
                        // Right after midnight there is only one hour – nothing to draw yet.
                        if (day.hours.size >= 2) item(key = "course") { DayCourseCard(day, summary, settings, tf, history, place) }
                        // The composites keep days of radar (DWD, KNMI, MET Norway): the whole day, in 5-minute steps
                        if (dev.nimbus.weather.ui.radar.RadarComposites.covered(place.latitude, place.longitude)) item(key = "radar") {
                            val start = day.date.atStartOfDay(history.zone).toInstant().toEpochMilli()
                            RadarDayCard(start, tf) { onOpenRadarDay(start) }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    place: Place, title: String, day: HistoryDay?, summary: DaySummary?, tf: TimeFormat,
    parts: List<dev.nimbus.weather.data.remote.DayPartWeather>, shown: Int,
    location: LocationMark? = null,
    modelLabel: String? = null,
) {
    val s = LocalSettings.current
    // On the open sky: white with a dark halo, as the weather page's header
    val halo = androidx.compose.ui.text.TextStyle(shadow = dev.nimbus.weather.ui.components.LocalHeaderStyle.current.shadow)
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (place.isCurrentLocation) LocationPin(location, size = 20.dp)
            Text(
                place.name, Modifier.weight(1f, fill = false).alignByBaseline(),
                fontSize = 26.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = halo,
            )
            // "my location": whether its position is current – as on the weather page
            if (location != null) LocationDot(location, 26.sp)
        }
        dev.nimbus.weather.ui.places.TwinModelLine(modelLabel, halo)
        // a low window (a phone held sideways): a smaller title, the cards get the height
        val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 500
        Text(
            title, fontSize = if (compact) 30.sp else 40.sp, fontWeight = FontWeight.Light, color = Color.White,
            lineHeight = if (compact) 34.sp else 46.sp, style = halo,
        )
        day?.let {
            val ms = it.date.atStartOfDay(tf.zone).toInstant().toEpochMilli() + 12 * 3600_000L
            Text(tf.weekdayLong(ms) + ", " + tf.dayMonth(ms).substringAfter('\u00A0'), fontSize = 19.sp, color = Color.White, style = halo)
        }
        if (summary != null) {
            Spacer(Modifier.height(8.dp))
            dev.nimbus.weather.ui.components.MaxMinStack(
                summary.tempMax, summary.tempMin, s.temperatureUnit, fontSize = 20.sp,
                shadow = halo.shadow, labelColor = Color.White,
            )
        }
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            DayPartsTable(parts, shown, halo)
        }
    }
}

@Composable
private fun HistoryMessage(text: String, onRetry: (() -> Unit)?) {
    GlassCard {
        Text(text, fontSize = 15.sp, color = Color.White)
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0x40FFFFFF), contentColor = Color.White)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/** How long the sky shows each part of the day. */
private const val PART_SHOW_MS = 5_000L

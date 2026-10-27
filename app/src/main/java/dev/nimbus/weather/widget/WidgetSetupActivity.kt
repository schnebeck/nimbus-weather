/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetSetupActivity.kt
 * Setting up a widget when it is placed (and later on a long press): its place, its parts, the
 * pollen types, the background – with the widget itself as preview.
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

package dev.nimbus.weather.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.nimbus.weather.NimbusApp
import dev.nimbus.weather.R
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.repo.LocationProvider
import dev.nimbus.weather.data.repo.Store
import dev.nimbus.weather.ui.main.pollenName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // left without "Done" on placing it: the launcher removes the widget again
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val store = (application as? NimbusApp)?.container?.store ?: Store(application)
        setContent {
            SystemTheme {
                WidgetSetup(id, store, WidgetConfigs(this)) {
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            }
        }
    }
}

/** The system's look, as the widget has it: the wallpaper's colours from Android 12 on, light or dark. */
@Composable
private fun SystemTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
    }
}

/** A new widget's choice: the defaults, with the pollen types the pollen card shows where some were picked there. */
internal fun firstConfig(settings: Settings): WidgetConfig =
    WidgetConfig(pollen = if (settings.pollenTypes.size < PollenType.entries.size) PollenType.entries.filter { it in settings.pollenTypes } else emptyList())

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetSetup(id: Int, store: Store, configs: WidgetConfigs, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // shown at once with the defaults; a new widget takes the pollen card's types as soon as the settings are read
    val known = remember { configs.get(id) }
    var config by remember { mutableStateOf(known ?: WidgetConfig()) }
    LaunchedEffect(Unit) {
        if (known == null) firstConfig(store.settings.first()).pollen.let { p -> if (config.pollen.isEmpty()) config = config.copy(pollen = p) }
    }
    val c = config
    val places by store.places.collectAsState(initial = emptyList())
    val settings by store.settings.collectAsState(initial = null)
    val located by produceState<String?>(null) { value = store.cachedWeather(LocationProvider.CURRENT_LOCATION_ID)?.place?.name }
    val lastName by produceState<String?>(null, places) { value = Widgets.placeOf(store, WidgetConfig(followApp = true))?.name }
    val weather by produceState<Pair<String?, WidgetWeather?>>(null to null, c.placeId, c.followApp, c.pollen, places) {
        val place = Widgets.placeOf(store, c)
        value = place?.id to place?.let { store.cachedWeather(it.id) }?.let { WidgetWeather.of(it, System.currentTimeMillis(), c.pollen) }
    }
    val size = remember { Widgets.sizeOf(AppWidgetManager.getInstance(context), id) }
    val pad = WindowInsets.safeDrawing.asPaddingValues()

    // below the status bar: what scrolls up ends there, never under the clock
    LazyColumn(
        Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).testTag("widget-setup"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.widget_setup_title), style = MaterialTheme.typography.headlineSmall) }
        item {
            // the widget itself, as the home screen will show it – a tap here leads nowhere
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val (placeId, w) = weather
                AndroidView(
                    factory = { ctx -> object : FrameLayout(ctx) { override fun onInterceptTouchEvent(ev: MotionEvent) = true } },
                    modifier = Modifier.size(size.widthDp.coerceAtMost(360).dp, size.heightDp.coerceIn(56, 260).dp),
                    update = { frame ->
                        val views = WidgetViews.build(frame.context, id, c, size, w, placeId, settings?.temperatureUnit ?: dev.nimbus.weather.data.model.TemperatureUnit.CELSIUS, System.currentTimeMillis(),
                            precip = settings?.precipitationUnit ?: dev.nimbus.weather.data.model.PrecipitationUnit.MM,
                        )
                        frame.removeAllViews()
                        runCatching { frame.addView(views.apply(frame.context, frame)) }
                    },
                )
            }
        }
        item { Heading(stringResource(R.string.widget_setup_place)) }
        item {
            Column {
                Choice(stringResource(R.string.widget_setup_follow_app), lastName, c.followApp) { config = c.copy(followApp = true, placeId = null) }
                Text(
                    stringResource(R.string.widget_setup_follow_app_hint), Modifier.padding(start = 48.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Choice(stringResource(R.string.my_location), located, !c.followApp && c.placeId == null) { config = c.copy(followApp = false, placeId = null) }
                Text(
                    stringResource(R.string.widget_setup_my_location_hint), Modifier.padding(start = 48.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                places.forEach { p -> Choice(p.name, p.subtitle.ifEmpty { null }, !c.followApp && c.placeId == p.id) { config = c.copy(followApp = false, placeId = p.id) } }
            }
        }
        item { Heading(stringResource(R.string.widget_setup_parts)) }
        item {
            Column {
                Toggle(stringResource(R.string.widget_part_clock), stringResource(R.string.widget_part_clock_desc), c.clock, "clock") { config = c.copy(clock = it) }
                Toggle(stringResource(R.string.widget_part_date), stringResource(R.string.widget_part_date_desc), c.date, "date") { config = c.copy(date = it) }
                if (c.date) {
                    // the two forms of the date, each shown with today's
                    val locale = LocalContext.current.resources.configuration.locales[0]
                    val today = remember { System.currentTimeMillis() }
                    fun sample(skeleton: String) = android.text.format.DateFormat.format(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), today).toString()
                    val written = sample("EEEdMMMM")
                    val figures = sample("EEEddMMyyyy") + "  " + stringResource(R.string.widget_week, WidgetViews.weekOf(today, locale))
                    Column(Modifier.padding(start = 8.dp).testTag("date-style")) {
                        Choice(stringResource(R.string.widget_date_written), written, !c.dateWithWeek) { config = c.copy(dateWithWeek = false) }
                        Choice(stringResource(R.string.widget_date_figures), figures, c.dateWithWeek) { config = c.copy(dateWithWeek = true) }
                    }
                }
                Toggle(stringResource(R.string.widget_part_alarm), null, c.alarm && c.date, "alarm", enabled = c.date) { config = c.copy(alarm = it) }
                Toggle(stringResource(R.string.widget_part_weather), stringResource(R.string.widget_part_weather_desc), c.weather, "weather") { config = c.copy(weather = it) }
                Toggle(stringResource(R.string.widget_part_rain), stringResource(R.string.widget_part_rain_desc), c.rain, "rain") { config = c.copy(rain = it) }
            }
        }
        item { Heading(stringResource(R.string.widget_setup_pollen)) }
        item {
            Column {
                Text(stringResource(R.string.widget_setup_pollen_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PollenType.entries.forEach { t ->
                        val on = t in c.pollen
                        FilterChip(
                            selected = on, onClick = { config = c.copy(pollen = PollenType.entries.filter { it in c.pollen != (it == t) }) },
                            label = { Text(stringResource(pollenName(t))) }, modifier = Modifier.testTag("pollen-${t.key}"),
                        )
                    }
                }
            }
        }
        item {
            Column {
                Heading(stringResource(R.string.widget_setup_opacity, (c.opacity * 100).toInt()))
                Slider(c.opacity, { config = c.copy(opacity = it) }, valueRange = 0f..1f, steps = 9)
            }
        }
        item { Text(stringResource(R.string.widget_setup_updates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Button(
                {
                    scope.launch {
                        configs.put(id, c)
                        finishSetup(context, id)
                        onDone()
                    }
                },
                Modifier.fillMaxWidth().testTag("widget-done"),
            ) { Text(stringResource(R.string.widget_setup_done)) }
        }
    }
}

/** The widget shown with its choice at once, then fetched; the half-hourly fetch set going. */
internal suspend fun finishSetup(context: Context, id: Int) {
    Widgets.show(context, listOf(id))
    Widgets.schedule(context)
    Widgets.fetchNow(context)
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Choice(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, on: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled) { onChange(!on) }.padding(vertical = 6.dp).testTag("part-$tag"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange, enabled = enabled)
    }
}

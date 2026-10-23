/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/widget/WidgetConfig.kt
 * What each widget on the home screen shows: its place and its parts, kept per widget.
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

import android.content.Context
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.remote.JsonCodec
import kotlinx.serialization.Serializable

/**
 * One widget's choice: [placeId] null is "my location" – where the app found the phone last; the
 * widget itself never asks for the position (that would take the location in the background).
 * Each part can be switched off; [pollen] empty: no pollen line. [opacity] of the background, 0..1.
 * [dateWithWeek]: the date in figures with the calendar week ("01.10.2026 KW 40") instead of
 * weekday and month written out.
 */
@Serializable
data class WidgetConfig(
    val placeId: String? = null,
    val clock: Boolean = true,
    val date: Boolean = true,
    val dateWithWeek: Boolean = false,
    val alarm: Boolean = true,
    val weather: Boolean = true,
    val rain: Boolean = true,
    val pollen: List<PollenType> = emptyList(),
    val opacity: Float = 0.9f,
)

/** The widgets' choices, by their id (kept apart from the app's settings: they go with the widget). */
class WidgetConfigs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun get(id: Int): WidgetConfig? = prefs.getString(key(id), null)?.let {
        runCatching { JsonCodec.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull()
    }

    /** A widget placed without setup (the system may skip it): the defaults. */
    fun of(id: Int): WidgetConfig = get(id) ?: WidgetConfig()

    fun put(id: Int, config: WidgetConfig) {
        prefs.edit().putString(key(id), JsonCodec.encodeToString(WidgetConfig.serializer(), config)).apply()
    }

    fun remove(id: Int) {
        prefs.edit().remove(key(id)).apply()
    }

    private fun key(id: Int) = "w$id"
}

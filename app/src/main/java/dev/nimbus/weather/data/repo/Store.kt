/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/Store.kt
 * Persistent settings, saved places and the last forecast of each place.
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

package dev.nimbus.weather.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.nimbus.weather.data.model.Place
import dev.nimbus.weather.data.model.Settings
import dev.nimbus.weather.data.model.WeatherData
import dev.nimbus.weather.data.remote.JsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "nimbus")

/** Persists settings, saved places and the last successful forecast per place. */
class Store(private val context: Context) {
    private val settingsKey = stringPreferencesKey("settings")
    private val placesKey = stringPreferencesKey("places")
    private val placeList = ListSerializer(Place.serializer())

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        p[settingsKey]?.let { runCatching { JsonCodec.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings.forLocale(java.util.Locale.getDefault())
    }

    val places: Flow<List<Place>> = context.dataStore.data.map { p ->
        p[placesKey]?.let { runCatching { JsonCodec.decodeFromString(placeList, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = p[settingsKey]?.let { runCatching { JsonCodec.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings.forLocale(java.util.Locale.getDefault())
            p[settingsKey] = JsonCodec.encodeToString(Settings.serializer(), transform(cur))
        }
    }

    suspend fun updatePlaces(transform: (List<Place>) -> List<Place>) {
        context.dataStore.edit { p ->
            val cur = p[placesKey]?.let { runCatching { JsonCodec.decodeFromString(placeList, it) }.getOrNull() } ?: emptyList()
            p[placesKey] = JsonCodec.encodeToString(placeList, transform(cur))
        }
    }

    private fun cacheFile(placeId: String) =
        File(context.filesDir, "$CACHE_DIR/" + cacheFileName(placeId))

    suspend fun cachedWeather(placeId: String): WeatherData? = withContext(Dispatchers.IO) {
        val f = cacheFile(placeId)
        if (!f.exists()) null else runCatching { JsonCodec.decodeFromString(WeatherData.serializer(), f.readText()) }.getOrNull()
    }

    suspend fun cacheWeather(data: WeatherData) = withContext(Dispatchers.IO) {
        val f = cacheFile(data.place.id)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(JsonCodec.encodeToString(WeatherData.serializer(), data))
        tmp.renameTo(f)
    }

    suspend fun deleteCache(placeId: String) = withContext(Dispatchers.IO) { cacheFile(placeId).delete() }

    companion object {
        /** Folder (in the app's files) of the last weather of each place – shown at once on start, offline too. */
        const val CACHE_DIR = "weather_cache"

        /** File name of the last weather of [placeId] in [CACHE_DIR]. */
        fun cacheFileName(placeId: String) = placeId.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json"
    }
}

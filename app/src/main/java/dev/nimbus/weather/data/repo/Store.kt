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
        p[settingsKey]?.let { runCatching { JsonCodec.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings()
    }

    val places: Flow<List<Place>> = context.dataStore.data.map { p ->
        p[placesKey]?.let { runCatching { JsonCodec.decodeFromString(placeList, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = p[settingsKey]?.let { runCatching { JsonCodec.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings()
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
        File(context.filesDir, "weather_cache/" + placeId.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

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
}

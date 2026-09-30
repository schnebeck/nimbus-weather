/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/repo/LocationProvider.kt
 * Device location from the platform LocationManager, without Play Services.
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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dev.nimbus.weather.data.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import dev.nimbus.weather.data.remote.getJson
import dev.nimbus.weather.data.remote.o
import dev.nimbus.weather.data.remote.obj
import dev.nimbus.weather.data.remote.s
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale
import kotlin.coroutines.resume

/** Device location without Google Play Services, using the platform LocationManager; place names from the platform geocoder or OpenStreetMap. */
class LocationProvider(private val context: Context, private val http: okhttp3.OkHttpClient? = null) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val lastKnown = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        // For the weather a position from a few minutes ago is as good as a new one.
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 30 * 60_000L) return lastKnown
        // Network (cell/Wi-Fi) or the fused provider first: a few hundred metres are plenty for a
        // weather forecast and cost almost no battery. GPS only when they give nothing (e.g. no
        // network location service on the device), and then for a limited time.
        val coarse = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (LocationManager.NETWORK_PROVIDER in providers) add(LocationManager.NETWORK_PROVIDER)
        }
        suspend fun firstFix(list: List<String>, timeoutMs: Long): Location? = if (list.isEmpty()) null else withTimeoutOrNull(timeoutMs) {
            channelFlow {
                list.forEach { provider -> launch { singleFix(lm, provider)?.let { send(it) } } }
            }.firstOrNull()
        }
        val fresh = firstFix(coarse, 6_000L)
            ?: firstFix(listOf(LocationManager.GPS_PROVIDER).filter { it in providers }, 12_000L)
        return fresh ?: lastKnown
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleFix(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && provider != LocationManager.GPS_PROVIDER) {
                // Balanced accuracy: Wi-Fi and cell towers, the GPS stays off.
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val request = android.location.LocationRequest.Builder(0L)
                    .setQuality(android.location.LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                    .build()
                lm.getCurrentLocation(provider, request, signal, ContextCompat.getMainExecutor(context)) { if (cont.isActive) cont.resume(it) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { if (cont.isActive) cont.resume(it) }
            } else {
                val listener = android.location.LocationListener { if (cont.isActive) cont.resume(it) }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, context.mainLooper)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    suspend fun toPlace(location: Location, fallbackName: String): Place {
        val lat = location.latitude
        val lon = location.longitude
        val address = withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    withTimeoutOrNull(6_000L) {
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<android.location.Address>) { cont.resume(addresses.firstOrNull()) }
                                override fun onError(errorMessage: String?) { cont.resume(null) }
                            })
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                }
            }.getOrNull()
        }
        val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea
        if (name != null) {
            return Place(
                id = CURRENT_LOCATION_ID, name = name, region = address?.adminArea, country = address?.countryName,
                countryCode = address?.countryCode, latitude = lat, longitude = lon, isCurrentLocation = true,
            )
        }
        // No platform geocoder (devices without Google services) or it failed: OpenStreetMap.
        osmReverse(lat, lon)?.let { return it }
        return Place(id = CURRENT_LOCATION_ID, name = fallbackName, latitude = lat, longitude = lon, isCurrentLocation = true)
    }

    /**
     * Reverse geocoding with Nominatim (OpenStreetMap). Only called when the location changed and
     * the platform geocoder gave nothing, well within Nominatim's usage policy (max. 1 request/s).
     */
    private suspend fun osmReverse(lat: Double, lon: Double): Place? {
        val client = http ?: return null
        val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", dev.nimbus.weather.data.remote.OpenMeteoSource.fmt(lat))
            .addQueryParameter("lon", dev.nimbus.weather.data.remote.OpenMeteoSource.fmt(lon))
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("zoom", "10")
            .addQueryParameter("accept-language", Locale.getDefault().language)
            .build().toString()
        return runCatching {
            withTimeoutOrNull(6_000L) { client.getJson(url) }?.let { parseNominatim(it, lat, lon) }
        }.getOrNull()
    }

    companion object {
        /** Place from a Nominatim reverse answer: town, city or village, else the county. */
        fun parseNominatim(root: kotlinx.serialization.json.JsonElement, lat: Double, lon: Double): Place? {
            val o = root.obj() ?: return null
            val a = o.o("address") ?: return null
            val name = listOf("city", "town", "village", "municipality", "suburb", "county").firstNotNullOfOrNull { a.s(it) }
                ?: o.s("name")?.takeIf { it.isNotBlank() } ?: return null
            return Place(
                id = CURRENT_LOCATION_ID, name = name, region = a.s("state"), country = a.s("country"),
                countryCode = a.s("country_code")?.uppercase(), latitude = lat, longitude = lon, isCurrentLocation = true,
            )
        }

        const val CURRENT_LOCATION_ID = "current-location"
    }
}

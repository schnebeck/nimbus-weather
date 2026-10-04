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
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
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
open class LocationProvider(private val context: Context, private val http: okhttp3.OkHttpClient? = null) {

    open fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Whether the device's location is switched on at all (no position can come otherwise). */
    open fun enabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching { androidx.core.location.LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(true)
    }

    /**
     * The device's position and when it was taken ([Locate.Found.at], wall clock) – a fresh one
     * if any can be had (see [Locate.best]), else the newest the system knows (perhaps old), null
     * when there is none at all.
     */
    @SuppressLint("MissingPermission")
    open suspend fun currentLocation(
        /** Asked for anew (reload): a new position only – not the system's last one, however recent. */
        fresh: Boolean = false,
    ): Locate.Found<Location>? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fun on(p: String) = runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER).filter(::on)
        val now = System.currentTimeMillis()
        val lastKnown = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .map { Locate.Found(it, now - ageMs(it)) }
            .maxByOrNull { it.at }
        // Network (cell/Wi-Fi) or the fused provider first: a few hundred metres are plenty for a
        // weather forecast and cost almost no battery; the GPS joins when they stay silent.
        val coarse = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && on(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
            if (LocationManager.NETWORK_PROVIDER in providers) add(LocationManager.NETWORK_PROVIDER)
        }.map { p -> suspend { fix(lm, p, fresh) } }
        val gps = if (LocationManager.GPS_PROVIDER in providers) suspend { fix(lm, LocationManager.GPS_PROVIDER, fresh) } else null
        return Locate.best(lastKnown, now, coarse, gps, recentMs = if (fresh) 0L else Locate.RECENT_MS) { System.currentTimeMillis() }
    }

    /** How old [l] is – by the device's uptime clock, the GPS' wall clock time may be off. */
    private fun ageMs(l: Location): Long =
        ((android.os.SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)

    /**
     * A fix from [provider]; asked for anew ([fresh]) only one of now: the system answers with one it
     * took up to half a minute ago – that made the reload's check a matter of milliseconds.
     */
    private suspend fun fix(lm: LocationManager, provider: String, fresh: Boolean): Location? =
        if (fresh) freshFix(lm, provider) else singleFix(lm, provider, fresh)

    /**
     * Asked for anew (reload): the provider runs until a fix of now arrives, then it stops. Asking
     * for "the current location" instead got the same stored one for half a minute – passed over
     * as too old, asked again and again: a repeated reload waited 30–40 s.
     */
    @SuppressLint("MissingPermission")
    private suspend fun freshFix(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                if (ageMs(location) < FRESH_FIX_MS && cont.isActive) { lm.removeUpdates(this); cont.resume(location) }
            }
            override fun onProviderDisabled(provider: String) {
                if (cont.isActive) { lm.removeUpdates(this); cont.resume(null) }
            }
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("only called before Android 10")
            @Suppress("DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
        cont.invokeOnCancellation { lm.removeUpdates(listener) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val request = android.location.LocationRequest.Builder(0L)
                    .setQuality(
                        if (provider == LocationManager.GPS_PROVIDER) android.location.LocationRequest.QUALITY_HIGH_ACCURACY
                        else android.location.LocationRequest.QUALITY_BALANCED_POWER_ACCURACY,
                    )
                    .build()
                lm.requestLocationUpdates(provider, request, ContextCompat.getMainExecutor(context), listener)
            } else {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleFix(lm: LocationManager, provider: String, fresh: Boolean): Location? = suspendCancellableCoroutine { cont ->
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Balanced accuracy: Wi-Fi and cell towers, the GPS stays off – the GPS itself at its best
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val request = android.location.LocationRequest.Builder(0L)
                    .setQuality(
                        if (provider == LocationManager.GPS_PROVIDER) android.location.LocationRequest.QUALITY_HIGH_ACCURACY
                        else android.location.LocationRequest.QUALITY_BALANCED_POWER_ACCURACY,
                    )
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

    open suspend fun toPlace(location: Location, fallbackName: String): Place {
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
        /** Asked for anew (reload): a fix at most this old counts. */
        const val FRESH_FIX_MS = 10_000L

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

/**
 * How the position is looked for. A position the system knows from the last [RECENT_MS] is taken
 * as it is; anything older may lie far behind (it showed Hannover in Bad Harzburg). Then the
 * coarse providers (cell, Wi-Fi) are asked, and if they give nothing within [GPS_AFTER_MS] – on
 * an EDGE network the cell lookup hardly gets through – the GPS joins, for up to [TIMEOUT_MS] in
 * all (a GPS without help from the network needs half a minute and more). Without any new
 * position the old one comes back with its time: the app shows it as not current.
 */
object Locate {
    const val RECENT_MS = 2 * 60_000L
    const val GPS_AFTER_MS = 5_000L
    const val TIMEOUT_MS = 60_000L
    /** Pause before the GPS is asked again after a request without a position. */
    private const val GPS_AGAIN_MS = 1_000L

    /** [value] taken at [at] (wall clock). */
    data class Found<out T>(val value: T, val at: Long)

    suspend fun <T : Any> best(
        last: Found<T>?,
        now: Long,
        coarse: List<suspend () -> T?>,
        gps: (suspend () -> T?)?,
        /** A position the system knows from this long ago is taken as it is (0: always ask anew). */
        recentMs: Long = RECENT_MS,
        clock: () -> Long,
    ): Found<T>? {
        if (last != null && now - last.at < recentMs) return last
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            channelFlow {
                val asked = coarse.map { f -> launch { f()?.let { send(it) } } }
                if (gps != null) launch {
                    withTimeoutOrNull(GPS_AFTER_MS) { asked.joinAll() }
                    while (true) {
                        gps()?.let { send(it); return@launch }
                        delay(GPS_AGAIN_MS)
                    }
                }
            }.firstOrNull()
        }
        return fresh?.let { Found(it, clock()) } ?: last
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/FreshFixTest.kt
 * The reload's position against the platform's location service: the service answers with the
 * fix it has stored (seconds or half a minute old) – the search takes the next new one as soon as
 * it comes, instead of asking again and again for half a minute.
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

package dev.nimbus.weather

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import dev.nimbus.weather.data.repo.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FreshFixTest {
    private fun fix(lat: Double, ageMs: Long) = Location(LocationManager.NETWORK_PROVIDER).apply {
        latitude = lat; longitude = 9.73; accuracy = 20f
        time = System.currentTimeMillis() - ageMs
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageMs * 1_000_000L
    }

    /**
     * Pulled to reload a few times in a row: the location service still has the fix of 20 s ago
     * and hands it out again – the search runs on and takes the new fix when it comes, at once.
     */
    @Test fun aRepeatedReloadTakesTheNextNewFix() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadow = shadowOf(lm)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.simulateLocation(fix(52.30, ageMs = 20_000))          // the stored one

        val search = CoroutineScope(Dispatchers.Main.immediate).async { LocationProvider(app).currentLocation(fresh = true) }
        val looper = shadowOf(Looper.getMainLooper())
        repeat(10) { looper.idleFor(Duration.ofMillis(100)) }
        // the stored fix again, as the service does: passed over
        shadow.simulateLocation(fix(52.30, ageMs = 21_000))
        repeat(10) { looper.idleFor(Duration.ofMillis(100)) }
        assertTrue("took the stored fix", !search.isCompleted)
        // a new one: taken at once
        shadow.simulateLocation(fix(52.38, ageMs = 0))
        repeat(5) { looper.idleFor(Duration.ofMillis(100)) }
        assertTrue("the new fix was not taken", search.isCompleted)
        assertEquals(52.38, search.getCompleted()!!.value.latitude, 1e-6)
    }
}

/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/NoBackgroundWorkTest.kt
 * "Nimbus [hat] keine Eigenschaften, für die es überhaupt bei ausgeschaltetem Bildschirm noch
 * weiterarbeiten sollte. Es muss beim reaktivieren eh alle Daten aktualisieren": no work in the
 * background, the radar resting while not shown.
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

import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.nimbus.weather.data.repo.BackgroundWork
import dev.nimbus.weather.ui.radar.RadarFrame
import dev.nimbus.weather.ui.radar.RadarPlayer
import dev.nimbus.weather.ui.radar.RadarTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class NoBackgroundWorkTest {
    /** Stands in for the workers of earlier versions (scheduled under their names). */
    class Former(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork() = Result.success()
    }

    /** The hourly weather and the 15-minute radar work an earlier version scheduled: called off at the start. */
    @Test fun formerWorkIsCalledOff() {
        val ctx = RuntimeEnvironment.getApplication()
        WorkManager.initialize(ctx, Configuration.Builder().setExecutor(Executors.newSingleThreadExecutor()).build())
        val wm = WorkManager.getInstance(ctx)
        for (name in BackgroundWork.formerNames) {
            wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<Former>(1, TimeUnit.HOURS).build()).result.get()
        }
        BackgroundWork.stopAll(ctx)
        for (name in BackgroundWork.formerNames) {
            val states = wm.getWorkInfosForUniqueWork(name).get().map { it.state }
            assertTrue("$name: $states", states.all { it == WorkInfo.State.CANCELLED })
        }
        assertEquals(listOf("hourly-refresh", "radar-prefetch"), BackgroundWork.formerNames)
    }

    /** The workers themselves are gone – nothing left to schedule. */
    @Test fun noWorkersLeft() {
        for (name in listOf("dev.nimbus.weather.data.repo.RefreshWorker", "dev.nimbus.weather.data.repo.RadarWorker")) {
            assertTrue(name, runCatching { Class.forName(name) }.isFailure)
        }
    }

    private val offline = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the test") }.build()
    private val step = 5 * 60_000L
    private val latest = 1_791_219_000_000L / step * step

    /** The radar stopped (the phone locked): no step is cut while it is not shown – on its start again, all. */
    @Test fun theRadarRestsWhileNotShown() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = RadarPlayer(scope, offline, maxSide = 128)
            val tl = RadarTimeline((-24..0).map { k -> RadarFrame(latest + k * step, false, null, null) }, 24, "")
            p.stop()
            p.setTimeline(tl)
            p.setView(59.9, 60.9, 4.3, 6.3, 1080)
            Thread.sleep(2_000)
            assertEquals("steps cut while stopped", 0, tl.frames.indices.count { p.isLoaded(it) })
            p.start()
            val until = System.currentTimeMillis() + 20_000
            while (!tl.frames.indices.all { p.isLoaded(it) }) { assertTrue("not all steps after the start", System.currentTimeMillis() < until); Thread.sleep(50) }
        } finally { scope.cancel() }
    }
}

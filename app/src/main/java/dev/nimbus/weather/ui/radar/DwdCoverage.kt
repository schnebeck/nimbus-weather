/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/DwdCoverage.kt
 * Where the DWD radar composite measures – RainViewer is shown only outside of it.
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

package dev.nimbus.weather.ui.radar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * DWD and RainViewer stacked, each at 85 %, show the same rain over Germany twice as opaque as
 * the forecast (DWD only). The area the DWD composite covers - every cell its newest step has
 * data for ([WnStep.reach]) - is kept on disk; inside it RainViewer pixels are dropped.
 */
internal object DwdCoverage {
    // Lat/lon grid over the DWD bounds, 0.025 degrees per cell
    const val W = 696
    const val H = 428
    const val LON0 = 1.4
    const val LON1 = 18.8
    const val LAT0 = 45.6
    const val LAT1 = 56.3

    /**
     * A radar out of service leaves a hole without data in the composite: the mask is renewed
     * every hour, so KNMI and RainViewer fill the hole.
     */
    const val RENEW_MS = 3_600_000L
    private const val FILE = "dwd_coverage.bin"

    @Volatile private var mask: BooleanArray? = null
    /** The time of the analysis behind [mask]. */
    @Volatile private var takenAt = 0L
    private val mutex = Mutex()
    @Volatile internal var clock: () -> Long = System::currentTimeMillis

    /** Inside the DWD radar area (false while the mask is not loaded). */
    fun covers(lat: Double, lon: Double): Boolean {
        val m = mask ?: return false
        val x = ((lon - LON0) / (LON1 - LON0) * W).toInt()
        val y = ((LAT1 - lat) / (LAT1 - LAT0) * H).toInt()
        if (x !in 0 until W || y !in 0 until H) return false
        return m[y * W + x]
    }

    val ready: Boolean get() = mask != null

    /**
     * The mask loaded or renewed - and joined to the widest reach seen ([DwdReach]): from the
     * device first, then from the newest analysis once the one behind it is [RENEW_MS] old. When
     * that cannot be had, the mask in use stays.
     */
    suspend fun ensure(http: OkHttpClient) = mutex.withLock {
        if (mask == null) kept()?.let { (at, m) -> take(m, at) }
        if (clock() - takenAt >= RENEW_MS) newest(http)?.let { (at, m) ->
            take(m, at)
            withContext(Dispatchers.IO) { MaskFile.write(file(), m, at) }
        }
        mask?.let { DwdReach.join(it) }
        Unit
    }

    private fun take(m: BooleanArray, at: Long) {
        mask = m
        takenAt = at
    }

    private fun file(): File? = RadarComposites.dir?.let { File(it, FILE) }

    /** The mask on the device with the time of its analysis. */
    private suspend fun kept(): Pair<Long, BooleanArray>? = withContext(Dispatchers.IO) {
        val f = file()
        MaskFile.read(f, W * H)?.let { (f?.lastModified() ?: 0L) to it }
    }

    /** The reach of the newest analysis, with its time; null if it cannot be had. */
    private suspend fun newest(http: OkHttpClient): Pair<Long, BooleanArray>? {
        val time = RadarLatest.known(DwdRadar)?.takeIf { clock() - it < RENEW_MS } ?: RadarLatest.check(http, DwdRadar) ?: return null
        val step = try {
            DwdFiles.step(http, RadarFrame(time, false, null, null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return time to withContext(Dispatchers.Default) { step.reach() }
    }

    /** For tests: the mask as given, or none. */
    internal fun set(m: BooleanArray?, at: Long = 0L) {
        mask = m
        takenAt = at
    }
}

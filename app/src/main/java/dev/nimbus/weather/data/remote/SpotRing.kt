/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/SpotRing.kt
 * The radar's past hours over a place kept on the device: only the hours not kept are asked for.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Fable 5.2 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Fable 5.2 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.nimbus.weather.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

/**
 * The radar's hours over a place, each by its end: [sums] the hour's precipitation as the radar
 * adjusted to the gauges gives it (mm), [echoes] the strongest 5-minute step of an hour that has
 * all twelve (mm/h). Neither changes once it is there.
 */
@Serializable
data class SpotHours(val sums: Map<Long, Double> = emptyMap(), val echoes: Map<Long, Double> = emptyMap()) {
    /** The hours ending at [from] or after. */
    fun since(from: Long): SpotHours = SpotHours(sums.filterKeys { it >= from }, echoes.filterKeys { it >= from })
}

/**
 * A ring of the last days' [SpotHours] per place in [dir], one file for each position (to about
 * 100 m): every answer of the radar's service is added, the hours older than the look-back
 * reaches dropped. An hour kept is not asked for again - of three days, the hours since the last
 * look remain.
 */
class SpotRing(private val dir: File?) {
    /** One answer is added at a time: two arriving together both stay. */
    private val writing = Mutex()

    private fun file(lat: Double, lon: Double): File? = dir?.let { File(it, String.format(Locale.ROOT, "spot_%.3f_%.3f.json", lat, lon)) }

    /** The hours kept for the place; none kept: empty. */
    suspend fun read(lat: Double, lon: Double): SpotHours = withContext(Dispatchers.IO) {
        runCatching { file(lat, lon)?.takeIf { it.exists() }?.readText()?.let { JsonCodec.decodeFromString(SpotHours.serializer(), it) } }.getOrNull() ?: SpotHours()
    }

    /** Adds [hours] to those kept for the place; of all of them the ones ending at [from] or after stay. */
    suspend fun add(lat: Double, lon: Double, hours: SpotHours, from: Long) = writing.withLock {
        val f = file(lat, lon) ?: return@withLock
        val kept = read(lat, lon)
        val all = SpotHours(kept.sums + hours.sums, kept.echoes + hours.echoes).since(from)
        withContext(Dispatchers.IO) {
            runCatching {
                f.parentFile?.mkdirs()
                f.writeText(JsonCodec.encodeToString(SpotHours.serializer(), all))
            }
        }
        Unit
    }

    companion object {
        const val HOUR_MS = 3_600_000L
        private const val STEP_MS = 5 * 60_000L
        /** The echoes are asked for in blocks of the day this long: a day at once took the service too long. */
        const val BLOCK_MS = 6 * HOUR_MS

        /** The full hours ending from [from] to [to] that are not among [known]. */
        fun missing(known: Set<Long>, from: Long, to: Long): List<Long> =
            ((from + HOUR_MS - 1) / HOUR_MS * HOUR_MS..to step HOUR_MS).filter { it !in known }

        /**
         * The stretches of 5-minute steps to ask for, so that every full hour ending from [from]
         * to [to] not among [known] gets its twelve: of each block of [BLOCK_MS] that lacks an
         * hour, from the first step of its first hour lacking to its end, or to [to]. A block all
         * known is not asked for.
         */
        fun stretches(known: Set<Long>, from: Long, to: Long): List<LongRange> =
            missing(known, from, to).groupBy { (it - 1) / BLOCK_MS }.map { (block, hours) ->
                (hours.first() - HOUR_MS + STEP_MS)..minOf((block + 1) * BLOCK_MS, to)
            }
    }
}

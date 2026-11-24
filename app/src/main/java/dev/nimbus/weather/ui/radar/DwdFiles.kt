/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/ui/radar/DwdFiles.kt
 * The DWD's radar composite from the files of its open data server.
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

package dev.nimbus.weather.ui.radar

import dev.nimbus.weather.data.remote.getFilePart
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.lang.ref.SoftReference
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Every 5 minutes the DWD puts one archive (tar) of the WN composite on its open data server:
 * the analysis of that time first, then the 24 steps of its nowcast, each an HDF5 file
 * ([WnStep]). The server hands out stored files - it computes nothing per request - and keeps
 * them for two days.
 *
 * An analysis is fetched as the beginning of its archive alone: as many bytes as the last
 * analysis had and a little more, the rest in a second request where that was too few. A step of
 * a nowcast needs its archive whole; it is fetched once for all its steps and kept while memory
 * allows. A step the server does not have is null; a failing connection is an exception.
 */
internal object DwdFiles {
    @Volatile var base = "https://opendata.dwd.de/weather/radar/composite/wn"
    @Volatile var clock: () -> Long = System::currentTimeMillis

    /** The files are kept two days - asked for a little less long: a file about to go is not counted on. */
    const val KEPT_MS = 47 * 3_600_000L
    /** The archive of the newest analysis, under the names the server gives it. */
    private val NEWEST = listOf("composite_wn__LATEST.tar", "composite_wn_LATEST.tar")
    private const val FIRST_GUESS = 48 * 1024
    private const val PART_TIMEOUT_S = 15L
    private const val ARCHIVE_TIMEOUT_S = 40L
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC)
    private val member = Regex("""composite_wn_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})_(\d{3})-hd5""")

    /** How large the last analysis was: the next one is asked for with as many bytes and a margin. */
    @Volatile private var analysisSize = FIRST_GUESS
    /** The archive fetched whole last: the nowcast's steps all come from it. */
    @Volatile private var archive: Pair<Long, SoftReference<ByteArray>>? = null
    /** The step read last: the coverage and the picture ask for the same analysis. */
    @Volatile private var last: Pair<String, SoftReference<WnStep>>? = null
    private val archives = InFlight<Long, ByteArray?>()
    private val steps = InFlight<String, WnStep?>()

    /** Whether the server still has the archive of the analysis at [issue]. */
    fun has(issue: Long): Boolean = clock() - issue < KEPT_MS

    fun url(issue: Long): String = "$base/composite_wn_${stamp.format(Instant.ofEpochMilli(issue))}.tar"

    /** The time of the newest analysis: named by the first file of the newest archive, of which the head is asked for. */
    suspend fun latest(http: OkHttpClient): Long? {
        for (name in NEWEST) {
            val head = http.getFilePart("$base/$name", 0, Tar.BLOCK, PART_TIMEOUT_S) ?: continue
            return Tar.member(head)?.let { time(it.name) }?.first
        }
        return null
    }

    /** The step [frame]: an analysis, or the step of the nowcast computed from [RadarFrame.issue]. */
    suspend fun step(http: OkHttpClient, frame: RadarFrame): WnStep? {
        val issue = if (frame.isForecast) frame.issue ?: return null else frame.time
        val key = "$issue/${frame.time}"
        last?.takeIf { it.first == key }?.second?.get()?.let { return it }
        return steps.get(key) {
            val file = if (frame.isForecast) forecast(http, issue, frame.time) else analysis(http, issue)
            file?.let { f -> withContext(RadarDecode.decoding) { WnStep.read(f) } }?.also { last = key to SoftReference(it) }
        }
    }

    /** The file of the analysis at [issue]: the first of its archive. */
    private suspend fun analysis(http: OkHttpClient, issue: Long): ByteArray? {
        kept(issue)?.let { whole -> return Tar.member(whole)?.takeIf { it.end <= whole.size }?.let { Tar.bytes(whole, it) } }
        val begin = http.getFilePart(url(issue), 0, Tar.BLOCK + analysisSize, PART_TIMEOUT_S) ?: return null
        val m = Tar.member(begin) ?: return null
        analysisSize = m.size + m.size / 8 + 4096
        if (m.end <= begin.size) return Tar.bytes(begin, m)
        val rest = http.getFilePart(url(issue), begin.size, m.end - begin.size, PART_TIMEOUT_S) ?: return null
        return (begin + rest).takeIf { it.size >= m.end }?.let { Tar.bytes(it, m) }
    }

    /** The file of the nowcast's step at [time] out of the archive of [issue]. */
    private suspend fun forecast(http: OkHttpClient, issue: Long, time: Long): ByteArray? {
        val whole = kept(issue) ?: archives.get(issue) {
            http.getFilePart(url(issue), 0, null, ARCHIVE_TIMEOUT_S)?.also { archive = issue to SoftReference(it) }
        } ?: return null
        return Tar.members(whole).firstOrNull { time(it.name) == issue to time }?.let { Tar.bytes(whole, it) }
    }

    private fun kept(issue: Long): ByteArray? = archive?.takeIf { it.first == issue }?.second?.get()

    /** The analysis a file's name tells and the time its step is for; null: not one of the composite's files. */
    fun time(name: String): Pair<Long, Long>? {
        val g = member.find(name)?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
        val issue = runCatching { ZonedDateTime.of(g[0], g[1], g[2], g[3], g[4], 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() ?: return null
        return issue to issue + g[5] * 60_000L
    }

    /** For tests: nothing kept. */
    fun reset() {
        analysisSize = FIRST_GUESS
        archive = null
        last = null
    }
}

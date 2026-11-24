/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/DwdFileServer.kt
 * The open data server's radar archives for tests: answered from memory, every request noted.
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


package dev.nimbus.weather

import dev.nimbus.weather.ui.radar.DwdFiles
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Answers the open data server's requests from [archives] (file name to bytes), a part where a
 * part is asked for, and notes each as "name" or "name bytes=a-b" in [asked]; any other host is
 * noted as "map" and fails.
 */
internal class DwdFileServer {
    val archives = HashMap<String, ByteArray>()
    val asked = CopyOnWriteArrayList<String>()
    /** The connection fails. */
    @Volatile var down = false
    /** Every answer takes this long. */
    @Volatile var delayMs = 0L

    val http: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        if (request.url.host != "opendata.dwd.de") {
            asked += "map"
            throw IOException("no map service in the test")
        }
        val name = request.url.pathSegments.last()
        val range = request.header("Range")
        asked += if (range == null) name else "$name $range"
        if (delayMs > 0) Thread.sleep(delayMs)
        if (down) throw IOException("the server is down in the test")
        val bytes = archives[name] ?: return@addInterceptor answer(request, 404, ByteArray(0))
        if (range == null) return@addInterceptor answer(request, 200, bytes)
        val (from, to) = range.removePrefix("bytes=").split('-').map { it.toInt() }
        if (from >= bytes.size) answer(request, 416, ByteArray(0)) else answer(request, 206, bytes.copyOfRange(from, minOf(to + 1, bytes.size)))
    }.build()

    private fun answer(request: Request, code: Int, body: ByteArray): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("").body(body.toResponseBody()).build()

    /** The archive of the analysis at [issue]: [file] as the analysis and as each step of its nowcast. */
    fun put(issue: Long, file: ByteArray, newest: Boolean = false) {
        val day = DwdFiles.url(issue).substringAfterLast("composite_wn_").removeSuffix(".tar")
        val archive = tarOf((0..120 step 5).map { "composite_wn_${day}_%03d-hd5".format(it) to file })
        archives[DwdFiles.url(issue).substringAfterLast('/')] = archive
        if (newest) archives["composite_wn__LATEST.tar"] = archive
    }
}

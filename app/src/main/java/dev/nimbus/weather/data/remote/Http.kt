/*
 * Nimbus - app/src/main/java/dev/nimbus/weather/data/remote/Http.kt
 * HTTP and JSON helpers shared by all data sources.
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

package dev.nimbus.weather.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpException(val code: Int, message: String) : IOException(message)

val JsonCodec = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

const val USER_AGENT = "Nimbus-Weather (Android; https://github.com/schnebeck/nimbus-weather)"

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/**
 * GET + JSON parse. The body is read and parsed on the IO dispatcher: [await] resumes on the
 * caller's dispatcher after the *headers* arrived, and reading a large body there would do
 * network I/O on the main thread.
 */
suspend fun OkHttpClient.getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
    val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
        headers.forEach { (k, v) -> header(k, v) }
        if (freshData()) cacheControl(AskAgain)
    }.build()
    return withContext(Dispatchers.IO) {
        newCall(req).await().use { resp ->
            val body = resp.body.string()
            if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code} for ${req.url.host}: ${body.take(200)}")
            JsonCodec.parseToJsonElement(body)
        }
    }
}

/** Body as text, read on the IO dispatcher (see [getJson]). */
suspend fun OkHttpClient.getText(req: Request): String {
    val r = if (freshData()) req.newBuilder().cacheControl(AskAgain).build() else req
    return withContext(Dispatchers.IO) { newCall(r).await().use { it.body.string() } }
}

/**
 * Data asked for anew (the forced reload): in a coroutine with this element no answer comes from a
 * store – the HTTP cache asks the server again ([AskAgain]), the sources skip their own stores,
 * data of a moment and lists alike (stations, bathing waters, the tide fit); a stored one only
 * stands in where the new answer fails.
 */
object FreshData : kotlin.coroutines.CoroutineContext.Element {
    object Key : kotlin.coroutines.CoroutineContext.Key<FreshData>
    override val key: kotlin.coroutines.CoroutineContext.Key<*> get() = Key
}

/** Whether the data are asked for anew here ([FreshData]). */
suspend fun freshData(): Boolean = kotlin.coroutines.coroutineContext[FreshData.Key] != null

/**
 * Notes for one extra source whether its answer holds a stored value standing in for a new one
 * that failed ([standIn]) – its card then stays yellow and is tried again, not shown as current.
 */
class StandIns : kotlin.coroutines.CoroutineContext.Element {
    @Volatile var used = false
    object Key : kotlin.coroutines.CoroutineContext.Key<StandIns>
    override val key: kotlin.coroutines.CoroutineContext.Key<*> get() = Key
}

/** A source answers with a stored value in place of a new one that failed (see [StandIns]). */
suspend fun standIn() { kotlin.coroutines.coroutineContext[StandIns.Key]?.used = true }

/** The server asked again – a stored answer only if it says it is still the same. */
val AskAgain: okhttp3.CacheControl = okhttp3.CacheControl.Builder().noCache().build()

// ---- tolerant JSON accessors -------------------------------------------------

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonObject.o(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.a(key: String): JsonArray? = this[key] as? JsonArray

fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.let { if (it is JsonNull) null else it.doubleOrNull ?: it.booleanOrNull?.let { b -> if (b) 1.0 else 0.0 } }
fun JsonElement?.lng(): Long? = (this as? JsonPrimitive)?.let { if (it is JsonNull) null else it.longOrNull ?: it.doubleOrNull?.toLong() }
fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.let { if (it is JsonNull || !it.isString) null else it.content }
fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.let { if (it is JsonNull) null else it.booleanOrNull ?: it.doubleOrNull?.let { d -> d != 0.0 } }

fun JsonObject.d(key: String): Double? = this[key].dbl()
fun JsonObject.l(key: String): Long? = this[key].lng()
fun JsonObject.s(key: String): String? = this[key].str()

fun JsonObject.doubles(key: String): List<Double?> = a(key)?.map { it.dbl() } ?: emptyList()
fun JsonObject.longs(key: String): List<Long?> = a(key)?.map { it.lng() } ?: emptyList()
fun <T> List<T>.at(i: Int): T? = getOrNull(i)

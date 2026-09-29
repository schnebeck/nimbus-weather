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

const val USER_AGENT = "Nimbus-Weather/1.0 (Android; private non-commercial app)"

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
suspend fun OkHttpClient.getText(req: Request): String = withContext(Dispatchers.IO) {
    newCall(req).await().use { it.body.string() }
}

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

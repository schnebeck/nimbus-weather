package dev.nimbus.weather

import dev.nimbus.weather.data.remote.JsonCodec
import kotlinx.serialization.json.JsonElement

object Fixtures {
    fun text(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    fun json(name: String): JsonElement = JsonCodec.parseToJsonElement(text(name))
}

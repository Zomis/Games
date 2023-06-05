package net.zomis.games.compose.common

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

class TestPlatform : PlatformTools {
    private val mapper = jacksonObjectMapper()

    override fun runOnUiThread(block: () -> Unit) { block.invoke() }

    override fun toJson(value: Any): String {
        return mapper.writeValueAsString(value)
    }

    override fun <T : Any> fromJson(json: Any, type: KClass<T>): T {
        println("Json convert: $json (of type ${json::class} to $type")
        if (json::class == type) {
            println("Json quick return")
            return json as T
        }
        println("Json slow return")
        return mapper.convertValue(json, type.java)
    }
}
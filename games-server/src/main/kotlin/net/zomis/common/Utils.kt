package net.zomis.common

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal

suspend fun <A, B> List<A>.pmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async(Dispatchers.Default) { f(it) } }.map { it.await() }
}

private val mapper = jacksonObjectMapper()
fun convertToDBFormat(obj: Any): Any? {
    return when (obj) {
        is Int -> obj
        is String -> obj
        is Boolean -> obj
        is Unit -> null
        else -> {
            val value: Any = mapper.convertValue(obj, object: TypeReference<Map<String, Any>>() {})
            value
        }
    }
}

fun convertFromDBFormat(obj: Any?): Any? {
    return when (obj) {
        null -> null
        is BigDecimal -> obj.toInt()
        is Map<*, *> -> {
            val map = obj as Map<String, *>
            map.mapValues { convertFromDBFormat(it.value) }
        }
        else -> throw UnsupportedOperationException("Unable to handle " + obj.javaClass)
    }
}

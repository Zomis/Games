package net.zomis.common

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

suspend fun <A, B> List<A>.pmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async(Dispatchers.Default) { f(it) } }.map { it.await() }
}

private val mapper = jacksonObjectMapper()
fun convertToDBFormat(obj: Any): Any? {
    return when (obj) {
        is Int -> return obj
        is String -> return obj
        is Boolean -> return obj
        is Unit -> return null
        else -> {
            val value: Any = mapper.convertValue(obj, object: TypeReference<Map<String, Any>>() {})
            value
        }
    }
}

package net.zomis.common

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

object Utils
private val logger = KLoggers.logger(Utils)
suspend fun <A, B> List<A>.pmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map {
        async(Dispatchers.Default) {
            try {
                f(it)
            } catch (e: Exception) {
                logger.error(e) { "Error in bmap" }
                throw e
            }
        }
    }.map { it.await() }
}
suspend fun <A, B> List<A>.bmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map {
        runBlocking {
            try {
                f(it)
            } catch (e: Exception) {
                logger.error(e) { "Error in bmap" }
                throw e
            }
        }
    }
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
        is List<*> -> obj.map { convertFromDBFormat(it) }
        is String -> obj
        else -> throw UnsupportedOperationException("Unable to handle " + obj.javaClass)
    }
}

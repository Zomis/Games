package net.zomis.common

import klog.KLoggers
import kotlinx.coroutines.*
import net.zomis.games.server2.JacksonTools
import java.math.BigDecimal

object Utils
private val logger = KLoggers.logger(Utils)
suspend fun <A, B> List<A>.pmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map {
        async(Dispatchers.Default + CoroutineName("pmap for $it")) {
            try {
                f(it)
            } catch (e: Exception) {
                logger.error(e) { "Error in pmap" }
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

fun convertToDBFormat(obj: Any): Any? {
    return when (obj) {
        is Int -> obj
        is String -> obj
        is Boolean -> obj
        is Unit -> null
        is Enum<*> -> obj.name
        else -> {
            val value: Any = JacksonTools.convertValueToMap(obj)
            value
        }
    }
}

fun convertFromDBFormat(obj: Any?): Any? {
    return when (obj) {
        null -> null
        is BigDecimal -> obj.toInt()
        is Boolean -> obj
        is Map<*, *> -> {
            val map = obj as Map<String, *>
            map.mapValues { convertFromDBFormat(it.value) }
        }
        is List<*> -> obj.map { convertFromDBFormat(it) }
        is String -> obj
        else -> throw UnsupportedOperationException("Unable to handle " + obj.javaClass)
    }
}

fun String.substr(index: Int, length: Int): String {
    if (index < 0) return substr(this.length + index, length)
    if (length < 0) return substr(index, this.length + length)
    if (index > this.length) return ""
    var endIndex = index + length
    endIndex = minOf(endIndex, this.length)
    return this.substring(index, endIndex)
}

fun String.substr(index: Int): String {
    return if (index >= 0)
        this.substr(index, this.length - index)
    else this.substr(index, -index)
}

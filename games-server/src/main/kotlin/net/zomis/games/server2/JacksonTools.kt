package net.zomis.games.server2

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ser.PropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.games.impl.HanabiConfig
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties

object JacksonTools {

    private val mapper = jacksonObjectMapper()

    fun <T> readValue(value: String, targetClass: Class<T>): T {
        if (targetClass == Unit.javaClass) return Unit as T
        return mapper.readValue(value, targetClass)
    }

    fun convertValueToMap(obj: Any): Map<String, Any> {
        val properties = obj::class.declaredMemberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { it.name }.toSet()
        val filters = SimpleFilterProvider()
            .addFilter("ignoreInternal", SimpleBeanPropertyFilter.filterOutAllExcept(properties))
        val m = mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.PUBLIC_ONLY)
        return m.convertValue(obj, object: TypeReference<Map<String, Any>>() {})
    }

}

fun main() {
    val config = ServerGames.setup("Hanabi")!!.getDefaultConfig()
    println(config::class.declaredMemberProperties)
    println(config::class.java.fields.toList())
    println(JacksonTools.convertValueToMap(config))

}
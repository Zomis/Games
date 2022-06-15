package net.zomis.games.server2

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.impl.GameSetupImpl
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

    fun config(configs: GameConfigs, json: JsonNode): GameConfigs {
        if (configs.isOldStyle()) {
            if (configs.configs.isNotEmpty()) {
                configs.set("", mapper.convertValue(json, configs.configs.single().clazz.java))
            }
        } else {
            json.fieldNames().forEach { key ->
                val converted = mapper.convertValue(json[key], configs.configs.first { it.key == key }.clazz.java)
                configs.set(key, converted)
            }
        }
        return configs
    }

    fun configFromString(setup: GameSetupImpl<Any>, json: String): GameConfigs {
        val configs = setup.configs()
        return config(configs, mapper.readTree(json))
    }

}

package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JacksonTools {

    private val mapper = jacksonObjectMapper()

    fun <T> readValue(value: String, targetClass: Class<T>): T {
        if (targetClass == Unit.javaClass) return Unit as T
        return mapper.readValue(value, targetClass)
    }

}
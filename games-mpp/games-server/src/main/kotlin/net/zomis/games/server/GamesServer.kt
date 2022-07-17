package net.zomis.games.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

object GamesServer {
    private val mapper = jacksonObjectMapper()
    val actionConverter: (KClass<*>, Any) -> Any = { clazz, serialized ->
        mapper.convertValue(serialized, clazz.java)
    }
}

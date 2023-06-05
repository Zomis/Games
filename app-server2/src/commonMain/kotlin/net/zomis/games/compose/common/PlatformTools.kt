package net.zomis.games.compose.common

import kotlin.reflect.KClass

interface PlatformTools {

    fun runOnUiThread(block: () -> Unit)
    fun toJson(value: Any): String
    fun <T: Any> fromJson(json: Any, type: KClass<T>): T

}

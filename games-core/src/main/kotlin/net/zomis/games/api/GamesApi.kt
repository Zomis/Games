package net.zomis.games.api

import net.zomis.games.dsl.GameCreator
import kotlin.reflect.KClass

object GamesApi {

    fun <T : Any> gameCreator(clazz: KClass<T>): GameCreator<T> = GameCreator(clazz)

}

package net.zomis.games.api

import net.zomis.games.context.ContextHolder
import net.zomis.games.context.GameCreatorContext
import net.zomis.games.context.GameCreatorContextScope
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.GameSpec
import kotlin.reflect.KClass

object GamesApi {

    fun <T : Any> gameCreator(clazz: KClass<T>): GameCreator<T> = GameCreator(clazz)
    fun <T : ContextHolder> gameContext(name: String, clazz: KClass<T>, function: GameCreatorContextScope<T>.() -> Unit)
        = GameSpec(name, GameCreatorContext(name, function).toDsl())

}

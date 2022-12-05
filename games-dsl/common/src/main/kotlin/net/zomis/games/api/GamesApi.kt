package net.zomis.games.api

import net.zomis.games.cards.CardZone
import net.zomis.games.components.grids.ExpandableGrid
import net.zomis.games.components.grids.Grid
import net.zomis.games.components.grids.GridImpl
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.GameCreatorContext
import net.zomis.games.context.GameCreatorContextScope
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.GameSpec
import kotlin.reflect.KClass

object GamesApi {

    fun <T : Any> gameCreator(clazz: KClass<T>): GameCreator<T> = GameCreator(clazz)
    fun <T : ContextHolder> gameContext(name: String, clazz: KClass<T>, function: GameCreatorContextScope<T>.() -> Unit)
        = GameCreatorContext(name, function).toGameSpec()

    val components get() = GamesComponents
}
val Games.components get() = GamesComponents

object GamesComponents {

    fun <T> grid(x: Int, y: Int, init: (x: Int, y: Int) -> T): Grid<T> = GridImpl(x, y, init)
    fun <T> expandableGrid(chunkSize: Int = 16): ExpandableGrid<T>
            = ExpandableGrid(chunkSize = chunkSize)

    fun <T> cardZone(list: MutableList<T> = mutableListOf()): CardZone<T> {
        return CardZone(list)
    }

}

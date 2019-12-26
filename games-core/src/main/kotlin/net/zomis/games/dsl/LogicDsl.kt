package net.zomis.games.dsl

import net.zomis.games.dsl.impl.GameLogicActionType2D

typealias ActionLogic2D<T, P> = GameLogicActionType2D<T, P>.() -> Unit

interface GameLogic<T : Any> {

    fun <P : Any> action2D(name: String, grid: GridDsl<T, P>, logic: ActionLogic2D<T, P>)

}
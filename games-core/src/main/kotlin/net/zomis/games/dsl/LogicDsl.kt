package net.zomis.games.dsl

import net.zomis.games.dsl.impl.GameLogicContext2D

typealias ActionLogic2D<T, P> = GameLogicContext2D<T, P>.() -> Unit

interface GameLogic<T> {

    fun <P> action2D(name: String, logic: ActionLogic2D<T, P>)

}
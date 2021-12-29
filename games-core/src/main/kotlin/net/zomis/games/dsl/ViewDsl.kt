package net.zomis.games.dsl

import net.zomis.games.common.PlayerIndex
import kotlin.reflect.KClass

typealias GameViewOnRequestFunction<T> = GameViewOnRequestScope<T>.(request: Map<String, Any>) -> Any
interface GameViewOnRequestScope<T: Any>: ViewScope<T>

interface Viewable {
    fun toView(viewer: PlayerIndex): Any?
}

interface ViewScope<T: Any> {
    fun actions(): ActionsView<T>
    fun actionsChosen(): ActionsChosenView<T>
    fun <A: Any> action(actionType: ActionType<T, A>): ActionView<T, A>
    val game: T
    val viewer: PlayerIndex
}

interface ActionView<T: Any, A: Any> {
    fun anyAvailable(): Boolean
    fun <E: Any> nextSteps(clazz: KClass<E>): List<E>
    fun choose(next: Any): ActionView<T, A>
    fun options(): List<A>
}

interface ActionsView<T: Any> {
    fun <E: Any> nextSteps(clazz: KClass<E>): List<E>
}
data class ActionPlayerChoice(val actionType: String, val chosen: List<Any>)
interface ActionsChosenView<T: Any>: ActionsView<T> {
    fun chosen(): ActionPlayerChoice?
}

@Deprecated("use other view system instead")
interface GameView<T: Any> : ViewScope<T> {
    fun result(): Map<String, Any?>
    fun currentPlayer(function: (T) -> Int)
    fun value(key: String, value: (T) -> Any?)
    fun eliminations()
    fun onRequest(requestName: String, function: GameViewOnRequestFunction<T>)
}

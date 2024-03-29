package net.zomis.games.dsl

import net.zomis.games.api.UsageScope
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.impl.GameMarker
import kotlin.reflect.KClass

interface Viewable {
    fun toView(viewer: PlayerIndex): Any?
}
object HiddenValue

@GameMarker
interface ViewScope<T: Any> : UsageScope {
    fun actions(): ActionsView<T>
    fun actionsChosen(): ActionsChosenView<T>
    @Deprecated("use actionRaw or chosenActions instead")
    fun <A: Any> action(actionType: ActionType<T, A>): ActionView<T, A>
    fun <A: Any> actionRaw(actionType: ActionType<T, A>): ActionView<T, A>
    fun <A: Any> chosenActions(actionType: ActionType<T, A>): ActionView<T, A>
    val game: T
    val viewer: PlayerIndex
}

interface ActionView<T: Any, A: Any> {
    fun anyAvailable(): Boolean
    fun <E: Any> nextSteps(clazz: KClass<E>): List<E>
    fun nextStepsAll(): Map<Any, Any>
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

package net.zomis.games.dsl.impl

import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.ActionView
import net.zomis.games.dsl.ActionsView
import kotlin.reflect.KClass

class ActionsViewImpl<T: Any>(private val game: Game<T>, private val viewer: PlayerViewer) : ActionsView<T> {
    override fun <E : Any> nextSteps(clazz: KClass<E>): List<E> {
        TODO("Not yet implemented")
    }
}

class ActionViewImpl<T: Any, A: Any>(
    private val game: Game<T>,
    private val actionType: ActionType<T, A>,
    private val viewer: PlayerViewer,
    private val chosen: List<Any> = emptyList()
) : ActionView<T, A> {
    private val actionEntry = game.actions.type(actionType)

    override fun anyAvailable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun <E : Any> nextSteps(clazz: KClass<E>): List<E> {
        if (actionEntry == null) return emptyList()

        TODO("Not yet implemented")
    }

    override fun choose(next: Any): ActionView<T, A> {
        return ActionViewImpl(game, actionType, viewer, chosen + next)
    }

    override fun options(): List<A> {
        TODO("Not yet implemented")
    }

}

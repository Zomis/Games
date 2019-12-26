package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameSetupImpl<T : Any>(gameSpec: GameSpec<T>) {

    private val context = GameDslContext<T>()
    init {
        gameSpec(context)
        context.modelDsl(context.model)
    }

    fun configClass(): KClass<*> = context.configClass
    fun getDefaultConfig(): Any? = context.model.config()

    fun createGame(config: Any?): GameImpl<T> {
        return GameImpl(context, config)
    }

}

class GameImpl<T : Any>(val setupContext: GameDslContext<T>, config: Any?) {

    val model = setupContext.model.factory(config)
    private val logic = GameLogicContext(model)
    init {
        setupContext.logicDsl(logic)
    }

    fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val view = GameViewContext(model, playerIndex)
        setupContext.viewDsl(view)
        return view.result()
    }

    fun availableActionTypes(): Set<String> = logic.actions.keys.toSet()
    fun <A : Any> availableActions(actionType: String, playerIndex: Int): Iterable<Actionable<T, A>> {
        val logicContext = logic.actions[actionType] as GameLogicActionType<T, A>

        return logicContext.availableActions(playerIndex, model)
    }

    fun <A : Any> actionIsAllowed(actionType: String, action: Actionable<T, A>): Boolean {
        val logicContext = logic.actions[actionType] as GameLogicActionType<T, A>

        return logicContext.actionAllowed(action)
    }

    fun <A : Any> performAction(actionType: String, action: Actionable<T, A>): Boolean {
        val logicContext = logic.actions[actionType] as GameLogicActionType<T, A>

        if (logicContext.actionAllowed(action)) {
            logicContext.performAction(action)
            return true
        }
        return false
    }

    fun <A : Any> actionType(actionType: String): GameLogicActionType<T, A> {
        return logic.actions[actionType] as GameLogicActionType<T, A>
    }

}
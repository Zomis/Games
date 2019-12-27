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

    fun availableActionTypes(): Set<String> = logic.actions.keys.map { it.name }.toSet()

    fun <A : Any> action(actionType: ActionType<A>): GameLogicActionType<T, A> {
        return logic.actions[actionType] as GameLogicActionType<T, A>
    }

    fun <A : Any> actionType(actionType: String): GameLogicActionType<T, A>? {
        return logic.actions.entries.find { it.key.name == actionType }?.value as GameLogicActionType<T, A>?
    }

}
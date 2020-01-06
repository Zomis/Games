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

class GameImpl<T : Any>(private val setupContext: GameDslContext<T>, config: Any?) {

    val model = setupContext.model.factory(config)
    val replayState = ReplayState()
    private val logic = GameLogicContext(model, replayState)
    init {
        setupContext.logicDsl(logic)
    }
    val actions = ActionsImpl(model, logic, replayState)

    fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val view = GameViewContext(model, playerIndex, replayState)
        setupContext.viewDsl(view)
        return view.result()
    }

    fun isGameOver(): Boolean {
        return getWinner() != null
    }

    fun getWinner(): PlayerIndex {
        return logic.winner(model)
    }

}
package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminations
import net.zomis.games.WinResult
import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameSetupImpl<T : Any>(gameSpec: GameSpec<T>) {

    private val context = GameDslContext<T>()
    init {
        gameSpec(context)
        context.modelDsl(context.model)
    }

    val playersCount: IntRange = context.model.playerCount

    fun configClass(): KClass<*> = context.configClass
    fun getDefaultConfig(): Any = if (configClass() == Unit::class) Unit else context.model.config()

    fun createGame(playerCount: Int, config: Any): GameImpl<T> {
        return GameImpl(context, playerCount, config)
    }

}

class GameImpl<T : Any>(private val setupContext: GameDslContext<T>, override val playerCount: Int,
        override val config: Any): GameFactoryScope<Any> {

    override val eliminationCallback = PlayerEliminations(playerCount)
    val model = setupContext.model.factory(this, config)
    private val replayState = ReplayState()
    private val logic = GameLogicContext(model, replayState)
    init {
        setupContext.logicDsl(logic)
    }
    val actions = ActionsImpl(model, logic, replayState)

    fun copy(copier: (source: T, destination: T) -> Unit): GameImpl<T> {
        val copy = GameImpl(setupContext, playerCount, config)
        copier(this.model, copy.model)
        return copy
    }

    fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val view = GameViewContext(model, eliminationCallback, playerIndex, replayState)
        setupContext.viewDsl(view)
        return view.result()
    }

    fun isGameOver(): Boolean {
        return eliminationCallback.isGameOver()
    }

    fun stateCheck() {
        val winner = logic.winner(model)
        if (winner != null) {
            if (winner < 0) {
                eliminationCallback.eliminateRemaining(WinResult.DRAW)
                return
            }
            eliminationCallback.result(winner, WinResult.WIN)
            eliminationCallback.eliminateRemaining(WinResult.LOSS)
        }
    }

}
package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminations
import net.zomis.games.WinResult
import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameControllerContext<T : Any>(
    override val game: GameImpl<T>, override val playerIndex: Int
): GameControllerScope<T> {
    override val model: T get() = game.model
}
interface GameControllerScope<T : Any> {
    val game: GameImpl<T>
    val model: T
    val playerIndex: Int
}
typealias GameController<T> = (GameControllerScope<T>) -> Actionable<T, Any>?

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
        return GameImpl(context, playerCount, config, StateKeeper())
    }

    fun createGameWithState(playerCount: Int, config: Any, stateKeeper: StateKeeper): GameImpl<T> {
        return GameImpl(context, playerCount, config, stateKeeper)
    }

}

class GameImpl<T : Any>(private val setupContext: GameDslContext<T>, override val playerCount: Int,
        override val config: Any, val stateKeeper: StateKeeper): GameFactoryScope<Any> {

    override val eliminationCallback = PlayerEliminations(playerCount)
    val model = setupContext.model.factory(this, config)
    private val replayState = ReplayState(stateKeeper, eliminationCallback)
    private val logic = GameLogicContext(model, replayState)
    init {
        setupContext.model.onStart(replayState, model)
        setupContext.logicDsl(logic)
    }
    val actions = ActionsImpl(model, logic, replayState)

    fun copy(copier: (source: T, destination: T) -> Unit): GameImpl<T> {
        val copy = GameImpl(setupContext, playerCount, config, stateKeeper)
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

    fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        val view = GameViewContext(model, eliminationCallback, playerIndex, replayState)
        setupContext.viewDsl(view)
        return view.request(playerIndex, key, params)
    }

}
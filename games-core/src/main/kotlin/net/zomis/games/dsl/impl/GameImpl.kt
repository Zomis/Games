package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminations
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameFactoryScope
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.Viewable
import kotlin.reflect.KClass

class GameControllerContext<T : Any>(
    override val game: GameImpl<T>, override val playerIndex: Int
): GameControllerScope<T> {
    override val model: T get() = game.model
    fun view(): Map<String, Any?> = game.view(playerIndex)
}
interface GameControllerScope<T : Any> {
    val game: GameImpl<T>
    val model: T
    val playerIndex: Int
}
typealias GameController<T> = (GameControllerScope<T>) -> Actionable<T, Any>?

class GameSetupImpl<T : Any>(gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    private val context = GameDslContext<T>()
    init {
        gameSpec(context)
        context.modelDsl(context.model)
    }

    val playersCount: IntRange = context.model.playerCount

    fun configClass(): KClass<*> = context.configClass
    fun getDefaultConfig(): Any = if (configClass() == Unit::class) Unit else context.model.config()

    fun createGame(playerCount: Int, config: Any): GameImpl<T>
        = this.createGameWithState(playerCount, config, StateKeeper())

    fun createGameWithState(playerCount: Int, config: Any, stateKeeper: StateKeeper): GameImpl<T> {
        if (playerCount !in playersCount) {
            throw IllegalArgumentException("Invalid number of players: $playerCount, expected $playersCount")
        }
        return GameImpl(context, playerCount, config, stateKeeper)
    }

}

class GameImpl<T : Any>(private val setupContext: GameDslContext<T>, override val playerCount: Int,
        override val config: Any, val stateKeeper: StateKeeper): GameFactoryScope<Any> {

    override val eliminationCallback = PlayerEliminations(playerCount)
    val model = setupContext.model.factory(this, config)
    private val replayState = ReplayState(stateKeeper, eliminationCallback)
    private val rules = GameRulesContext(model, replayState, eliminationCallback)
    init {
        setupContext.model.onStart(replayState, model)
        setupContext.rulesDsl?.invoke(rules)
    }
    val actions = ActionsImpl(model, rules, replayState)

    fun copy(copier: (source: T, destination: T) -> Unit): GameImpl<T> {
        val copy = GameImpl(setupContext, playerCount, config, stateKeeper)
        copier(this.model, copy.model)
        return copy
    }

    fun view(playerIndex: PlayerIndex): Map<String, Any?> {
        val view = GameViewContext(model, eliminationCallback, playerIndex, replayState)
        if (model is Viewable) {
            val map = model.toView(playerIndex) as Map<String, Any?>
            map.forEach { entry -> view.value(entry.key) { entry.value } }
        }
        setupContext.viewDsl?.invoke(view)
        rules.view(view)
        return view.result()
    }

    fun isGameOver(): Boolean {
        return eliminationCallback.isGameOver()
    }

    fun viewRequest(playerIndex: PlayerIndex, key: String, params: Map<String, Any>): Any? {
        val view = GameViewContext(model, eliminationCallback, playerIndex, replayState)
        setupContext.viewDsl?.invoke(view)
        return view.request(playerIndex, key, params)
    }

}
package net.zomis.games.dsl.impl

import net.zomis.games.dsl.Action2D
import net.zomis.games.dsl.ActionLogic2D
import net.zomis.games.dsl.GameSpec
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

    fun view(): Map<String, Any?> {
        val view = GameViewContext(model)
        setupContext.viewDsl(view)
        return view.result()
    }

    fun availableActionTypes(): Set<String> = logic.actions.keys.toSet()
    fun <P> availableActions(actionType: String, playerIndex: Int): List<Action2D<T, P>> {
        val logic2dContext = logic.actions[actionType] as GameLogicContext2D<T, P>

        return (0 until logic2dContext.size.second).flatMap {y ->
            (0 until logic2dContext.size.first).mapNotNull { x ->
                val target = logic2dContext.getter(x, y) ?: return@mapNotNull null
                val action = Action2D(model, playerIndex, x, y, target)
                val allowed = logic2dContext.allowedCheck(action)
                return@mapNotNull if (allowed) action else null
            }
        }
    }

    fun <P> actionIsAllowed(actionType: String, action: Action2D<T, P>): Boolean {
        val logic2dContext = logic.actions[actionType] as GameLogicContext2D<T, P>

        return logic2dContext.allowedCheck(action)
    }

    fun <P> performAction(actionType: String, action: Action2D<T, P>): Boolean {
        val logic2dContext = logic.actions[actionType] as GameLogicContext2D<T, P>

        if (logic2dContext.allowedCheck(action)) {
            logic2dContext.effect(action)
            return true
        }
        return false
    }

}
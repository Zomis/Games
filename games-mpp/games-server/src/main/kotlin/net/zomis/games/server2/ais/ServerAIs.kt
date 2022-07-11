package net.zomis.games.server2.ais

import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.Game
import net.zomis.games.server2.Client
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.ServerGame
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object ServerAIs {
    private val logger = KLoggers.logger(this)

    fun <T: Any> randomActionable(game: Game<T>, playerIndex: Int): Actionable<T, Any>? {
        val actionTypes = game.actions.types().filter { it.availableActions(playerIndex, null).any() }
        if (actionTypes.isEmpty()) {
            return null
        }
        val actionType = actionTypes.random()
        if (!actionType.isComplex()) {
            return actionType.availableActions(playerIndex, null).shuffled().random()
        }

        val chosen = mutableListOf<Any>()
        while (true) {
            val next = actionType.withChosen(playerIndex, chosen)
            val options = next.nextOptions().toList()
            val parameters = next.parameters().toList()
            if (options.isEmpty() && parameters.isEmpty()) {
                check(chosen.isNotEmpty())
                // This path didn't go anywhere, choose another path.
                chosen.clear()
                continue
            }
            val random = (0 until (options.size + parameters.size)).random()
            if (random >= options.size) {
                val actionable = actionType.createAction(playerIndex, parameters[random - options.size].parameter)
                if (actionType.isAllowed(actionable)) {
                    return actionable
                } else {
                    println("Reset after $playerIndex ${actionType.name}: $chosen")
                    chosen.clear()
                }
            } else {
                chosen.add(options[random].choiceValue)
            }
        }
    }

    fun randomAction(game: ServerGame, client: Client, index: Int): PlayerGameMoveRequest? {
        val controller = game.obj!!
        val actionable = randomActionable(controller, index)
        return actionable?.let {
            PlayerGameMoveRequest(client, game, it.playerIndex, it.actionType, it.parameter, false)
        }
    }

    fun register(events: EventSystem, executor: ScheduledExecutorService) {
        events.listen("ServerAIs Delayed move", DelayedAIMoves::class, {true}, {
            executor.schedule({
                try {
                    events.execute(it.move)
                } catch (e: Exception) {
                    logger.error(e, "Unable to call AI")
                }
            }, 1000, TimeUnit.MILLISECONDS)
        })
    }

}

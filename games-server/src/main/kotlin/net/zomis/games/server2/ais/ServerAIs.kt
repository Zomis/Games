package net.zomis.games.server2.ais

import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.ServerGame
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ServerAIs(private val dslGameTypes: Set<String>) {
    private val logger = KLoggers.logger(this)

    fun isDSLGameType(gameType: String) = dslGameTypes.contains(gameType)

    fun randomAction(game: ServerGame, index: Int): List<PlayerGameMoveRequest> {
        val controller = game.obj as GameImpl<Any>
        val actionTypes = controller.actions.types()
        val actions = actionTypes.flatMap {actionType ->
            actionType.availableActions(index)
        }
        if (actions.isEmpty()) {
            return listOf()
        }
        val chosenAction = actions.random().let {
            return@let PlayerGameMoveRequest(game, it.playerIndex, it.actionType, it.parameter)
        }
        return listOf(chosenAction)
    }

    fun register(events: EventSystem, executor: ScheduledExecutorService) {
        events.listen("ServerAIs Delayed move", DelayedAIMoves::class, {true}, {
            executor.schedule({
                it.moves.forEach {
                    try {
                        events.execute(it)
                    } catch (e: Exception) {
                        logger.error(e, "Unable to call AI")
                    }
                }
            }, 1000, TimeUnit.MILLISECONDS)
        })
        events.listen("register AI Random for DSL Game", GameTypeRegisterEvent::class, { isDSLGameType(it.gameType) }, {event ->
            ServerAI(event.gameType, "#AI_Random_" + event.gameType) { game, index ->
                return@ServerAI randomAction(game, index)
            }.register(events)
        })
        ServerAlphaBetaAIs().setup(events)
        ServerScoringAIs().setup(events)
    }

}

package net.zomis.games.server2.ais

import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.ServerGame
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ServerAIs(private val aiRepository: AIRepository, private val dslGameTypes: Set<String>) {
    private val logger = KLoggers.logger(this)

    fun isDSLGameType(gameType: String) = dslGameTypes.contains(gameType)

    fun <T: Any> randomActionable(game: GameImpl<T>, playerIndex: Int): Actionable<T, Any>? {
        val actionTypes = game.actions.types()
        val actions = actionTypes.flatMap {actionType ->
            actionType.availableActions(playerIndex, null)
        }
        if (actions.isEmpty()) {
            return null
        }
        return actions.random()
    }

    fun randomAction(game: ServerGame, index: Int): PlayerGameMoveRequest? {
        val controller = game.obj!!.game
        val actionable = randomActionable(controller, index)
        return actionable?.let {
            PlayerGameMoveRequest(game, it.playerIndex, it.actionType, it.parameter, false)
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
        events.listen("register AI Random for DSL Game", GameTypeRegisterEvent::class, { isDSLGameType(it.gameType) }, {event ->
            if (event.gameSpec.name == "Set") {
                // Do not add Random AI for Set game because of it playing continuously
                return@listen
            }
            ServerAI(event.gameType, "#AI_Random_" + event.gameType) { game, index ->
                return@ServerAI randomAction(game, index)
            }.register(events)
        })
        ServerAlphaBetaAIs(aiRepository).setup(events)
        ServerScoringAIs(aiRepository).setup(events)
    }

}

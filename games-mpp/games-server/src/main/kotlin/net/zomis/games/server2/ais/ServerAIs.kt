package net.zomis.games.server2.ais

import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameAIs
import net.zomis.games.server2.Client
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.ServerGame
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object ServerAIs {
    private val logger = KLoggers.logger(this)

    fun randomAction(game: ServerGame, client: Client, index: Int): PlayerGameMoveRequest? {
        val controller = game.obj!!
        val actionable = GameAIs.randomActionable(controller, index)
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

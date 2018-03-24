package net.zomis.games.server2.games.impl

import com.fasterxml.jackson.databind.node.IntNode
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.games.*
import net.zomis.games.ur.RoyalGameOfUr

class RoyalGameOfUrSystem {

    companion object {
        private val logger = KLoggers.logger(this)
        fun init(events: EventSystem) {
            events.addListener(PlayerGameMoveRequest::class, {
                if (it.game.gameType.type != "UR") {
                    return@addListener
                }
                if (it.game.obj == null) {
                    it.game.obj = RoyalGameOfUr()
                }
                val controller = it.game.obj as RoyalGameOfUr
                if (controller.isFinished) {
                    events.execute(it.illegalMove("Game already won by ${controller.winner}"))
                    return@addListener
                }

                val oldPlayer = controller.currentPlayer
                if (it.moveType == "roll") {
                    controller.doRoll()
                    events.execute(MoveEvent(it.game, it.player, "roll", ""))
                    events.execute(GameStateEvent(it.game, listOf(Pair("roll", controller.roll))))
                } else {
                    val x = (it.move as IntNode).intValue()
                    if (controller.currentPlayer != it.player) {
                        events.execute(it.illegalMove("Not your turn"))
                        return@addListener
                    }
                    if (controller.isMoveTime && controller.canMove(x)) {
                        controller.move(controller.currentPlayer, x, controller.roll)
                        logger.info { "${it.game} Player ${it.player} made move $x" }
                    } else {
                        events.execute(it.illegalMove("Not allowed to play there"))
                        return@addListener
                    }
                    events.execute(MoveEvent(it.game, it.player, "move", x))
                }
                if (controller.currentPlayer != oldPlayer) {
                    events.execute(GameStateEvent(it.game, listOf(Pair("player", controller.currentPlayer))))
                }


                if (controller.isFinished) {
                    val winner = controller.winner
                    it.game.players.indices.forEach({ playerIndex ->
                        val won = winner == playerIndex
                        val losePositionPenalty = if (won) 0 else 1
                        events.execute(PlayerEliminatedEvent(it.game, playerIndex, won, 1 + losePositionPenalty))
                    })
                    events.execute(GameEndedEvent(it.game))
                }
            })
            events.execute(GameTypeRegisterEvent("UR"))
        }
    }


}
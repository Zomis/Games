package net.zomis.games.server2.games.impl

import com.fasterxml.jackson.databind.node.IntNode
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.StartupEvent
import net.zomis.games.server2.games.*
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.games.TTClassicControllerWithGravity

fun TTPlayer.playerIndex(): Int {
    if (this == TTPlayer.X) return 0
    if (this == TTPlayer.O) return 1
    throw IllegalArgumentException("Current player must be X or O but was $this")
}

class Connect4 {

    companion object {
        private val logger = KLoggers.logger(this)
        fun init(events: EventSystem) {
            events.addListener(PlayerGameMoveRequest::class, {
                if (it.game.gameType.type == "Connect4") {
                    if (it.game.obj == null) {
                        it.game.obj = TTClassicControllerWithGravity(TTFactories().classicMNK(7, 6, 4))
                    }
                    val x = (it.move as IntNode).intValue()
                    val controller = it.game.obj as TTClassicControllerWithGravity
                    if (controller.isGameOver) {
                        events.execute(it.illegalMove("Game already won by ${controller.wonBy}"))
                        return@addListener
                    }
                    if (controller.currentPlayer.playerIndex() != it.player) {
                        events.execute(it.illegalMove("Not your turn"))
                        return@addListener
                    }
                    val playAt = (0 until controller.game.sizeY).asSequence()
                        .map { controller.game.getSub(x, it) }
                        .filter { !it.isWon }
                        .lastOrNull()

                    if (playAt != null && controller.play(playAt)) {
                        logger.info { "${it.game} Player ${it.player} played at $x ${playAt.y}" }
                        events.execute(MoveEvent(it.game, it.player, "move", x))
                    } else {
                        events.execute(it.illegalMove("Not allowed to play there"))
                    }

                    if (controller.isGameOver) {
                        val winner = controller.wonBy
                        it.game.players.indices.forEach({ playerIndex ->
                            val won = winner.playerIndex() == playerIndex
                            val losePositionPenalty = if (won) 0 else 1
                            events.execute(PlayerEliminatedEvent(it.game, playerIndex, won, 1 + losePositionPenalty))
                        })
                        events.execute(GameEndedEvent(it.game))
                    }
                }
            })
            events.listen("register Connect4", StartupEvent::class, {true}, {
                events.execute(GameTypeRegisterEvent("Connect4"))
            })
        }
    }

}

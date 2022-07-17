package net.zomis.games.server2.ais

import net.zomis.games.dsl.impl.GameAIs
import net.zomis.games.server2.Client
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.ServerGame

object ServerAIs {

    fun randomAction(game: ServerGame, client: Client, index: Int): PlayerGameMoveRequest? {
        val controller = game.obj!!
        val actionable = GameAIs.randomActionable(controller, index)
        return actionable?.let {
            PlayerGameMoveRequest(client, game, it.playerIndex, it.actionType, it.parameter, false)
        }
    }

}

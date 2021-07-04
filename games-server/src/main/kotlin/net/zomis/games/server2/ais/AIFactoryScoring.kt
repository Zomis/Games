package net.zomis.games.server2.ais

import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.server2.games.PlayerGameMoveRequest

class AIFactoryScoring {

    fun <T: Any> createAI(events: EventSystem, gameType: String, name: String, controller: GameController<T>) {
        ServerAI(gameType, name) {
            val obj = serverGame.obj!!.game as Game<T>
            val controllerContext = GameControllerContext(obj, playerIndex)
            val action = controller(controllerContext)
            if (action != null) PlayerGameMoveRequest(client, serverGame, playerIndex, action.actionType, action.parameter, false)
            else null
        }.register(events)
    }

}
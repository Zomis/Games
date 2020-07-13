package net.zomis.games.server2.ais

import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.games.PlayerGameMoveRequest

class AIFactoryScoring {

    fun <T: Any> createAI(events: EventSystem, gameType: String, name: String, controller: GameController<T>) {
        ServerAI(gameType, name) { game, index ->
            val obj = game.obj as GameImpl<T>
            val controllerContext = GameControllerContext(obj, index)
            val action = controller(controllerContext)
            if (action != null) PlayerGameMoveRequest(game, index, action.actionType, action.parameter, false)
            else null
        }.register(events)
    }

}
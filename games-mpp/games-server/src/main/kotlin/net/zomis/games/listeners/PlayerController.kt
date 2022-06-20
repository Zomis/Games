package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext

class PlayerController(
    private val game: Game<Any>,
    private val playerIndex: Int,
    private val controller: GameController<Any>
): GameListener {
    private val controllerContext = GameControllerContext(game, playerIndex)

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step == FlowStep.AwaitInput) {
            val action = controller.invoke(controllerContext)
            if (action != null) {
                println("PlayerController($playerIndex) returned $action")
                coroutineScope.launch {
                    println("PlayerController($playerIndex) launched coroutine")
                    delay(500)
                    game.actionsInput.send(action)
                    println("Sent action from $this")
                }
            }
        }
    }
}

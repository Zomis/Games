package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext

@Deprecated("Can be replaced with GameAI")
class PlayerController<out T: Any>(
    private val game: Game<T>,
    playerIndices: Iterable<Int>,
    private val controller: GameController<T>
): GameListener {
    private val controllerContexts: List<GameControllerContext<T>> = playerIndices.map { GameControllerContext(game, it) }

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step == FlowStep.AwaitInput) {
            val actions = controllerContexts.mapNotNull { controller.invoke(it) }
            for (action in actions) {
                println("PlayerController(${action.playerIndex}) returned $action")
                coroutineScope.launch {
                    println("PlayerController(${action.playerIndex}) launched coroutine")
                    delay(500)
                    game.actionsInput.send(action)
                    println("Sent action from $this: $action")
                }
            }
        }
    }
}

package net.zomis.games.listeners

import kotlinx.coroutines.*
import net.zomis.games.dsl.ConsoleController
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import java.util.Scanner

class ConsoleControl(val game: Game<Any>, val scanner: Scanner): GameListener {
    private var job: Job? = null
    private val controller = ConsoleController<Any>()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.GameEnd) job?.cancel()
        val jobNeedsStarting = step is FlowStep.AwaitInput && job == null
        if (step is FlowStep.GameSetup<*> || jobNeedsStarting) {
            job = coroutineScope.launch(CoroutineName(this::class.simpleName.toString()) + Dispatchers.IO) {
                while (game.isRunning()) {
                    val action = controller.inputRepeat { controller.queryInput(game, scanner) }
                    println("Sending action from ${this@ConsoleControl}")
                    game.actionsInput.send(action)
                    println("Sent action from ${this@ConsoleControl}")
                }
            }
        }
    }

}
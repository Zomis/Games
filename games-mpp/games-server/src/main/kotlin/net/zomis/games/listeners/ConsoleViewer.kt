package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.ConsoleView
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game

class ConsoleViewer(val game: Game<Any>): GameListener {
    val view = ConsoleView<Any>()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step == FlowStep.AwaitInput) {
            view.showView(game, null)
        }
    }

}
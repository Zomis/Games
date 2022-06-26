package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.FlowStep

class ReplayListener(val gameType: String) : GameListener {

    private var gameStartedState: GameSituationState = null
    private val actions = mutableListOf<ActionReplay>()
    private var playerCount: Int? = null
    private var config: GameConfigs? = null
    private var actionIndex = 0

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.ActionPerformed<*>) {
            val expectedActionIndex = actions.size
            if (actionIndex++ != expectedActionIndex) {
                throw IllegalStateException("ActionIndex $actionIndex was received but expected $expectedActionIndex")
            }
            println("Adding action ${actions.size}: ${step.action}")
            this.actions.add(step.toActionReplay())
        }
        if (step is FlowStep.GameSetup<*>) {
            this.playerCount = step.game.playerCount
            this.config = step.config
            this.gameStartedState = step.state
        }
    }

    fun data(): ReplayData = ReplayData(gameType, playerCount!!, config!!, gameStartedState, actions)

}
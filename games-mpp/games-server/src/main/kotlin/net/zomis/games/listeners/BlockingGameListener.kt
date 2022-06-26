package net.zomis.games.listeners

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game

class BlockingGameListener: GameListener {
    val lock = Mutex(locked = true)
    lateinit var game: Game<Any>

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step is FlowStep.GameSetup<*>) {
            game = step.game as Game<Any>
        }
        if (step is FlowStep.ProceedStep) {
            lock.unlock()
        }
    }

    suspend fun await() {
        lock.withLock {}
    }

    suspend fun <P: Any> awaitAndPerform(playerIndex: Int, type: String, parameter: P) {
        await()
        lock.lock()
        val action = game.actions.type(type)!!.createAction(playerIndex, parameter)
        game.actionsInput.send(action as Actionable<Any, out Any>)
    }

    suspend fun <P: Any> awaitAndPerformSerialized(playerIndex: Int, type: String, parameter: P) {
        await()
        lock.lock()
        val action = game.actions.type(type)!!.createActionFromSerialized(playerIndex, parameter)
        game.actionsInput.send(action as Actionable<Any, out Any>)
    }

    suspend fun <T: Any, P: Any> awaitAndPerform(playerIndex: Int, type: ActionType<T, P>, parameter: P) {
        await()
        lock.lock()
        val action = (game as Game<T>).actions.type(type)!!.createAction(playerIndex, parameter)
        game.actionsInput.send(action as Actionable<Any, out Any>)
    }

}
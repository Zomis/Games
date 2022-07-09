package net.zomis.games.dsl.listeners

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
    val gameEnd = Mutex(locked = true)
    lateinit var game: Game<Any>

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        when (step) {
            is FlowStep.GameSetup<*> -> game = step.game as Game<Any>
            is FlowStep.ProceedStep -> {
                if (step is FlowStep.GameEnd && gameEnd.isLocked) gameEnd.unlock()
                if (lock.isLocked) lock.unlock()
            }
            is FlowStep.PreMove -> if (!lock.isLocked) lock.lock()
            else -> {}
        }
    }

    suspend fun await() = lock.withLock {}
    suspend fun awaitGameEnd() = gameEnd.withLock {}

    suspend fun <T: Any, P: Any> awaitAndPerform(actionable: Actionable<T, P>)
        = awaitAndPerform(actionable.playerIndex, actionable.actionType, actionable.parameter)

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
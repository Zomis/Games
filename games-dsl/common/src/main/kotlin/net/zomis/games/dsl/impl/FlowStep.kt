package net.zomis.games.dsl.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.zomis.games.PlayerElimination
import net.zomis.games.dsl.ActionReplay
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameConfigs

sealed class FlowStep {
    interface ProceedStep
    interface ActionResult
    interface Completable {
        fun complete()
    }
    object GameEnd: FlowStep(), ProceedStep
    data class Elimination(val elimination: PlayerElimination): FlowStep()
    data class PreMove(
        val action: Actionable<*, *>,
        val state: MutableMap<String, Any>
    ): FlowStep(), Completable {
        private val completable = CompletableDeferred<Map<String, Any>>()
        val deferred: Deferred<Map<String, Any>> = completable
        override fun complete() { completable.complete(state) }

        fun setState(newState: Map<String, Any>) {
            state.clear()
            state.putAll(newState)
        }
    }

    data class ActionPerformed<T: Any>(
        val action: Actionable<T, Any>,
        val actionImpl: ActionTypeImplEntry<T, Any>,
        val replayState: Map<String,  Any>
    ): FlowStep(), ActionResult {
        val serializedParameter: Any get() = actionImpl.actionType.serialize(action.parameter)
        val playerIndex: Int get() = action.playerIndex
        val parameter: Any get() = action.parameter
        fun toActionReplay(): ActionReplay
                = ActionReplay(actionImpl.actionType.name, playerIndex, serializedParameter, replayState)
    }
    // TODO: Add reason for why Action is not allowed, of some form... name of rule(s)?
    data class IllegalAction(val actionType: String, val playerIndex: Int, val parameter: Any): FlowStep(), ActionResult
    data class Log(val log: ActionLogEntry): FlowStep()
    data class RuleExecution(val ruleName: String, val values: Any): FlowStep()
    // Use Deferred for PreSetup and PreMove, see https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html#actors
    data class PreSetup<T: Any>(
        val game: Game<T>,
        val state: MutableMap<String, Any>
    ): FlowStep(), Completable {
        private val completable = CompletableDeferred<Map<String, Any>>()
        val deferred: Deferred<Map<String, Any>> = completable
        override fun complete() { completable.complete(state) }
    }
    data class GameSetup<T: Any>(val game: Game<T>, val config: GameConfigs, val state: Map<String, Any>): FlowStep()
    //    class AwaitInput<T: Any>(val game: Game<T>, var deferred: Deferred<Actionable<T, out Any>?>? = null): FlowStep(), ProceedStep
    object AwaitInput: FlowStep(), ProceedStep
    object NextView : FlowStep()
}

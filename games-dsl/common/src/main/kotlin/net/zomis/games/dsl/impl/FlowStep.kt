package net.zomis.games.dsl.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.zomis.games.PlayerElimination
import net.zomis.games.dsl.*

sealed class FlowStep {
    interface ProceedStep
    interface ActionResultStep
    interface Completable {
        fun complete()
    }
    interface RandomnessResult {
        val state: Map<String, Any>
    }
    interface MutableRandomness: Completable {
        val state: MutableMap<String, Any>
        fun setState(newState: Map<String, Any>) {
            state.clear()
            state.putAll(newState)
        }
    }

    object GameEnd: FlowStep(), ProceedStep
    data class Elimination(val elimination: PlayerElimination): FlowStep()
    data class PreMove(
        val action: Actionable<*, *>,
        override val state: MutableMap<String, Any>
    ): FlowStep(), MutableRandomness, Completable {
        private val completable = CompletableDeferred<Map<String, Any>>()
        val deferred: Deferred<Map<String, Any>> = completable
        override fun complete() { completable.complete(state) }
    }

    data class ActionPerformed<T: Any>(
        val action: Actionable<T, Any>,
        val actionType: ActionType<T, Any>,
        override val state: Map<String, Any>,
        val result: ActionResult<T, out Any>? = null
    ): FlowStep(), ActionResultStep, RandomnessResult {
        val serializedParameter: Any get() = actionType.serialize(action.parameter)
        val playerIndex: Int get() = action.playerIndex
        val parameter: Any get() = action.parameter
        fun toActionReplay(): ActionReplay
                = ActionReplay(actionType.name, playerIndex, serializedParameter, state)
    }
    data class IllegalAction(val action: Actionable<out Any, out Any>, val results: ActionResult<out Any, out Any>?): FlowStep(), ActionResultStep
    data class Log(val log: ActionLogEntry): FlowStep()
    data class RuleExecution(val ruleName: String, val values: Any): FlowStep()
    // Use Deferred for PreSetup and PreMove, see https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html#actors
    data class PreSetup<T: Any>(
        val game: Game<T>,
        override val state: MutableMap<String, Any>
    ): FlowStep(), MutableRandomness {
        private val completable = CompletableDeferred<Map<String, Any>>()
        val deferred: Deferred<Map<String, Any>> = completable
        override fun complete() { completable.complete(state) }
    }
    data class GameStarted<T: Any>(val game: Game<T>, val config: GameConfigs): FlowStep()
    data class GameSetup<T: Any>(val game: Game<T>, val config: GameConfigs, override val state: Map<String, Any>): FlowStep(), RandomnessResult
    //    class AwaitInput<T: Any>(val game: Game<T>, var deferred: Deferred<Actionable<T, out Any>?>? = null): FlowStep(), ProceedStep
    object AwaitInput: FlowStep(), ProceedStep
    object NextView : FlowStep()
    data class GameException(val e: Throwable) : FlowStep()
}

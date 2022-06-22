package net.zomis.games.dsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.zomis.games.PlayerElimination
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.impl.ActionLogEntry
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.scorers.ScorerFactory

@Deprecated("Replaced by GameListeners")
class GameplayCallbacksList<T: Any>(val list: List<GameplayCallbacks<T>>): GameplayCallbacks<T>() {
    override fun startState(setStateCallback: (GameSituationState) -> Unit) = list.forEach { it.startState(setStateCallback) }
    override fun startedState(playerCount: Int, config: GameConfigs, state: GameSituationState) = list.forEach{it.startedState(playerCount, config, state)}

    override fun onPreMove(actionIndex: Int, action: Actionable<T, Any>, setStateCallback: (GameSituationState) -> Unit) = list.forEach {it.onPreMove(actionIndex, action, setStateCallback)}
    override fun onMove(actionIndex: Int, action: Actionable<T, Any>, actionReplay: ActionReplay) = list.forEach{it.onMove(actionIndex, action, actionReplay)}

    override fun onElimination(elimination: PlayerElimination) = list.forEach {it.onElimination(elimination)}
    override fun onLog(log: List<ActionLogEntry>) = list.forEach{it.onLog(log)}
}

interface GameListener {
    suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep)
}
class GameEntryPoint<T : Any>(private val gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    fun setup() = GameSetupImpl(gameSpec)
    fun scorers() = ScorerFactory(gameSpec)
    @Deprecated("use startGame with gameListeners instead")
    fun replayable(playerCount: Int, config: GameConfigs, vararg callbacks: GameplayCallbacks<T>)
        = GameReplayableImpl(gameSpec, playerCount, config, GameplayCallbacksList(callbacks.toList()))

    fun replay(replay: ReplayData): Replay<T> {
        if (replay.gameType != gameSpec.name) {
            throw IllegalArgumentException("Mismatching gametypes: Replay data for ${replay.gameType} cannot be used on ${gameSpec.name}")
        }
        return Replay(gameSpec, replay.playerCount, replay.config, replay)
    }
    @Deprecated("use startGame with ReplayListener instead")
    fun inMemoryReplay() = InMemoryReplayCallbacks<T>(gameSpec.name)

    suspend fun runTests() {
        setup().context.testCases.forEach {
            it.runTests(this)
        }
    }

    override fun toString(): String = "EntryPoint:$gameType"

}

object GamesImpl {

    val api = GamesApi
    fun <T : Any> game(gameSpec: GameSpec<T>) = GameEntryPoint(gameSpec)

}

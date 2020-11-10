package net.zomis.games.dsl

import net.zomis.games.PlayerElimination
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.impl.ActionLogEntry
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.dsl.impl.GameTestContext
import net.zomis.games.scorers.ScorerFactory

class GameplayCallbacksList<T: Any>(val list: List<GameplayCallbacks<T>>): GameplayCallbacks<T>() {
    override fun startState(setStateCallback: (GameSituationState) -> Unit) = list.forEach { it.startState(setStateCallback) }
    override fun startedState(playerCount: Int, config: Any, state: GameSituationState) = list.forEach{it.startedState(playerCount, config, state)}

    override fun onPreMove(actionIndex: Int, action: Actionable<T, Any>, setStateCallback: (GameSituationState) -> Unit) = list.forEach {it.onPreMove(actionIndex, action, setStateCallback)}
    override fun onMove(actionIndex: Int, action: Actionable<T, Any>, actionReplay: ActionReplay) = list.forEach{it.onMove(actionIndex, action, actionReplay)}

    override fun onElimination(elimination: PlayerElimination) = list.forEach {it.onElimination(elimination)}
    override fun onLog(log: List<ActionLogEntry>) = list.forEach{it.onLog(log)}
}

class GameEntryPoint<T : Any>(private val gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    fun setup() = GameSetupImpl(gameSpec)
    fun scorers() = ScorerFactory(gameSpec)
    fun replayable(playerCount: Int, config: Any?, vararg callbacks: GameplayCallbacks<T>)
        = GameReplayableImpl(gameSpec, playerCount, config, GameplayCallbacksList(callbacks.toList()))

    fun replay(replay: ReplayData,
               postReplayMoveCallback: GameplayCallbacks<T> = GameplayCallbacks(),
               alwaysCallback: GameplayCallbacks<T> = GameplayCallbacks()
    ): Replay<T> {
        if (replay.gameType != gameSpec.name) {
            throw IllegalArgumentException("Mismatching gametypes: Replay data for ${replay.gameType} cannot be used on ${gameSpec.name}")
        }
        return Replay(gameSpec, replay.playerCount, replay.config, replay, postReplayMoveCallback, alwaysCallback)
    }
    fun inMemoryReplay() = InMemoryReplayCallbacks<T>(gameSpec.name)

    fun runTests() {
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

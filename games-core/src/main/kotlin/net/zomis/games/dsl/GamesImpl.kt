package net.zomis.games.dsl

import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.scorers.ScorerFactory

class GameEntryPoint<T : Any>(private val gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    fun setup() = GameSetupImpl(gameSpec)
    fun scorers() = ScorerFactory(gameSpec)
    fun replayable(playerCount: Int, config: Any?, callbacks: GameplayCallbacks<T>)
        = GameReplayableImpl(gameSpec, playerCount, config, callbacks)

    fun replay(replay: ReplayData): Replay<T> {
        if (replay.gameType != gameSpec.name) {
            throw IllegalArgumentException("Mismatching gametypes: Replay data for ${replay.gameType} cannot be used on ${gameSpec.name}")
        }
        return Replay(gameSpec, replay.playerCount, replay.config, replay)
    }
    fun inMemoryReplay() = InMemoryReplayCallbacks<T>(gameSpec.name)

}

object GamesImpl {

    val api = GamesApi
    fun <T : Any> game(gameSpec: GameSpec<T>) = GameEntryPoint(gameSpec)

}

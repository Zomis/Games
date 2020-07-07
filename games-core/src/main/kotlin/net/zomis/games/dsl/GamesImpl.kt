package net.zomis.games.dsl

import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.scorers.ScorerFactory

class GameEntryPoint<T : Any>(private val gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    fun setup() = GameSetupImpl(gameSpec)
    fun scorers() = ScorerFactory(gameSpec)


}

object GamesImpl {

    val api = GamesApi
    fun <T : Any> game(gameSpec: GameSpec<T>) = GameEntryPoint(gameSpec)

}

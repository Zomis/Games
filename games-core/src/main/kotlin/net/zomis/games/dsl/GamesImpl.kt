package net.zomis.games.dsl

import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.impl.GameSetupImpl

object GamesImpl {

    val api = GamesApi
    fun <T : Any> setup(gameSpec: GameSpec<T>) = GameSetupImpl(gameSpec)

}

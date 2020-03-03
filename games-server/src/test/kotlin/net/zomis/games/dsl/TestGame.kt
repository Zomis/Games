package net.zomis.games.dsl

import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.ServerGames
import org.junit.jupiter.api.Assertions

object TestGame {

    fun <E : Any> create(name: String, config: Any? = null): GameTest<E> {
        val dsl = ServerGames.games[name] as GameSpec<E>
        val setup = GameSetupImpl(dsl)
        val impl = setup.createGame(if (config == null) setup.getDefaultConfig() else config)
        return GameTest(impl)
    }

}

class GameTest<T : Any>(val game: GameImpl<T>) {

    fun expectPossibleOptions(playerIndex: Int, actionType: String, expected: Int, vararg chosen: Any) {
        val actionInfo = game.actions.type(actionType)!!.availableParameters(playerIndex, chosen.toList())
        Assertions.assertEquals(expected, actionInfo.nextOptions.size)
    }

    fun expectPossibleActions(playerIndex: Int, actionType: String, expected: Int) {
        val availableActions = game.actions.type(actionType)!!.availableActions(playerIndex)
        Assertions.assertEquals(expected, availableActions.count())
    }

    fun performAction(playerIndex: Int, actionType: String, parameter: Any) {
        game.actions.type(actionType)!!.perform(playerIndex, parameter)
    }

}
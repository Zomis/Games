package net.zomis.games.dsl

import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.ServerGames
import org.junit.jupiter.api.Assertions

object TestGame {

    @Deprecated("only supports two player games and not widely used")
    fun <E : Any> create(name: String, config: GameConfigs? = null): GameAsserts<E> {
        val dsl = ServerGames.games[name] as GameSpec<E>
        val setup = GameSetupImpl(dsl)
        val configs = setup.configs()
        val impl = if (configs.isOldStyle()) {
            setup.createGameWithOldConfig(2, config ?: configs.configs.single().default.invoke())
        } else {
            setup.createGame(2, config!!)
        }
        return GameAsserts(impl)
    }

}

class GameAsserts<T : Any>(val game: Game<T>) {

    fun expectPossibleOptions(playerIndex: Int, actionType: String, expected: Int, vararg chosen: Any) {
        val actionInfo = game.actions.type(actionType)!!.actionInfoKeys(playerIndex, chosen.toList())
        Assertions.assertEquals(expected, actionInfo.keys.size)
    }

    fun expectPossibleActions(playerIndex: Int, actionType: String, expected: Int) {
        val availableActions = game.actions.type(actionType)!!.availableActions(playerIndex, null)
        Assertions.assertEquals(expected, availableActions.count()) { "Available actions are: $availableActions" }
    }

    fun expectPossibleActions(playerIndex: Int, expected: Int): List<Actionable<T, Any>> {
        val availableActions = game.actions.types().flatMap { it.availableActions(playerIndex, null) }
        Assertions.assertEquals(expected, availableActions.count()) { "Available actions are: $availableActions" }
        return availableActions
    }

    fun performAction(playerIndex: Int, actionType: String, parameter: Any) {
        val actionEntry = game.actions.type(actionType)!!
        val action = actionEntry.createAction(playerIndex, parameter)
        require(actionEntry.isAllowed(action)) { "Action is not allowed: $action" }
        actionEntry.perform(action)
    }

    fun performActionSerialized(playerIndex: Int, actionType: String, parameter: Any) {
        val actionEntry = game.actions.type(actionType)!!
        val action = actionEntry.createActionFromSerialized(playerIndex, parameter)
        require(actionEntry.isAllowed(action)) { "Action is not allowed: $action" }
        actionEntry.perform(action)
    }

}
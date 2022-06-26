package net.zomis.games.dsl

import kotlinx.coroutines.runBlocking
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.listeners.BlockingGameListener
import net.zomis.games.server2.ServerGames
import org.junit.jupiter.api.Assertions

class GameAsserts<T : Any>(val game: Game<T>, val blocking: BlockingGameListener? = null) {

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
        runBlocking {
            blocking?.await()
        }
        val actionEntry = game.actions.type(actionType)!!
        val action = actionEntry.createAction(playerIndex, parameter)
        require(actionEntry.isAllowed(action)) { "Action is not allowed: $action" }
        actionEntry.perform(action)
        runBlocking {
            blocking?.await()
        }
    }

    fun performActionSerialized(playerIndex: Int, actionType: String, parameter: Any) {
        runBlocking {
            blocking?.await()
        }
        val actionEntry = game.actions.type(actionType)!!
        val action = actionEntry.createActionFromSerialized(playerIndex, parameter)
        require(actionEntry.isAllowed(action)) { "Action is not allowed: $action" }
        actionEntry.perform(action)
        runBlocking {
            blocking?.await()
        }
    }

}
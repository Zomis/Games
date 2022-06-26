package net.zomis.games.dsl

import net.zomis.games.dsl.impl.Game
import net.zomis.games.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions

class GameAsserts<T : Any>(val game: Game<T>, private val blocking: BlockingGameListener) {

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

    suspend fun performAction(playerIndex: Int, actionType: String, parameter: Any) {
        blocking.awaitAndPerform(playerIndex, actionType, parameter)
        blocking.await()
    }

    suspend fun performActionSerialized(playerIndex: Int, actionType: String, parameter: Any) {
        blocking.awaitAndPerformSerialized(playerIndex, actionType, parameter)
        blocking.await()
    }

}
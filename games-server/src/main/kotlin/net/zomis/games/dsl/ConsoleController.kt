package net.zomis.games.dsl

import net.zomis.games.common.Point
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameControllerScope
import net.zomis.games.server2.games.ActionListRequestHandler
import net.zomis.games.server2.games.JsonChoices
import java.util.*

class ConsoleController<T: Any> {

    fun humanController(scanner: Scanner): (GameControllerScope<T>) -> Actionable<T, Any>? {
        val view = ConsoleView<T>()
        return {ctx ->
            view.showView(ctx.game)
            queryInput(ctx.game, scanner)
        }
    }

    fun <E> inputRepeat(function: () -> E?): E {
        var result: E? = null
        while (result == null) result = function()
        return result
    }

    fun queryInput(game: Game<T>, scanner: Scanner): Actionable<T, Any>? {
        println("Available actions is: ${game.actions.actionTypes}. Who is playing and what is your action?")
        val line = scanner.nextLine()
        if (!line.contains(" ")) {
            println("You forgot something.")
            if (line.isEmpty()) printAvailableActions(game)
            return null
        }
        val (playerIndex, actionType) = line.split(" ")
        val actionLogic = game.actions.types().find { it.name.toLowerCase() == actionType.toLowerCase() }
        if (actionLogic == null) {
            println("Invalid action")
            return null
        }

        val actionParameterClass = actionLogic.actionType.serializedType
        val action: Actionable<T, Any> = when (actionParameterClass) {
            Point::class -> {
                println("Enter x position where you want to play")
                val x = scanner.nextLine().toInt()
                println("Enter y position where you want to play")
                val y = scanner.nextLine().toInt()
                actionLogic.createActionFromSerialized(playerIndex.toInt(), Point(x, y))
            }
            Unit::class -> actionLogic.createActionFromSerialized(playerIndex.toInt(), Unit)
            else -> {
                stepByStepActionable(game, playerIndex.toInt(), actionType, scanner)
            }
        } ?: return null
        val allowed = actionLogic.isAllowed(action)
        println("Action $action allowed: $allowed")
        return action.takeIf { allowed }
    }

    private fun printAvailableActions(game: Game<T>) {
        game.playerIndices.map { playerIndex ->
            game.actions.types().forEach { actionType ->
                val actionsAvailable = actionType.availableActions(playerIndex, null).asSequence().take(1000).toList()
                val actionsCount = actionsAvailable.size
                val plus = if (actionsCount == 1000) "+" else ""
                if (actionsCount > 0) {
                    println("$playerIndex ${actionType.name}: $actionsCount$plus actions")
                    if (actionsCount <= 10) {
                        actionsAvailable.map { it.parameter }.forEach { println(it) }
                    }
                }
            }
        }
    }

    private fun stepByStepActionable(game: Game<T>, playerIndex: Int, moveType: String, scanner: Scanner): Actionable<T, Any>? {
        val chosen = mutableListOf<Any>()
        while (true) {
            val act = JsonChoices.availableActionsMessage(game, playerIndex, moveType, chosen).keys.keys

            println("  " + act.size + " choices")
            val entryList = act.entries.toList()
            entryList.forEachIndexed { index, value ->
                println("$index. ${value.key} - ${value.value.map { it.serialized }}")
            }

            val choice = scanner.nextLine().toIntOrNull() ?: return null
            val entryChosen = entryList.getOrNull(choice) ?: return null

            val actionType = game.actions.type(moveType)!!
            val actionInfoKey = entryChosen.value.single()
            if (actionInfoKey.isParameter) {
                return actionType.createActionFromSerialized(playerIndex, actionInfoKey.serialized)
            }

            chosen.add(actionInfoKey.serialized)
        }
    }

}
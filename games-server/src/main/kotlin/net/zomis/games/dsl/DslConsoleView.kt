package net.zomis.games.dsl

import kotlinx.coroutines.runBlocking
import net.zomis.common.convertToDBFormat
import net.zomis.games.common.Point
import net.zomis.games.dsl.impl.*
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.games.ActionListRequestHandler
import java.util.Scanner

class DslConsoleView<T : Any>(private val game: GameSpec<T>) {

    fun play(scanner: Scanner) {
        val entryPoint = GamesImpl.game(game)
        val setup = entryPoint.setup()
        println(setup.configClass())

        val config = setup.getDefaultConfig()
        println(config)
        println("Enter number of players:")
        val playerCount = scanner.nextLine().toInt()

        val replay = entryPoint.inMemoryReplay()
        val replayable = entryPoint.replayable(playerCount, config, replay)
        runBlocking {
            replayable.playThrough {
                showView(replayable.game)
                inputRepeat { queryInput(replayable.game, scanner) }
            }
            val savedReplay = entryPoint.replay(replay.data()).goToEnd()
            listOf<Int?>(null).plus(replayable.game.playerIndices).forEach {
                val match = replayable.game.view(it) == savedReplay.game.view(it)
                println("Replay for player $it verification: $match")
            }
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
                val actionsCount = actionType.availableActions(playerIndex, null).asSequence().take(1000).count()
                val plus = if (actionsCount == 1000) "+" else ""
                if (actionsCount > 0) {
                    println("$playerIndex ${actionType.name}: $actionsCount$plus actions")
                }
            }
        }
    }

    private fun stepByStepActionable(game: Game<T>, playerIndex: Int, moveType: String, scanner: Scanner): Actionable<T, Any>? {
        val reqHandler = ActionListRequestHandler(null)

        val chosen = mutableListOf<Any>()
        while (true) {
            val act = reqHandler.availableActionsMessage(game, playerIndex, moveType, chosen).keys.keys

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

    fun display(indentation: Int, name: String, data: Any?) {
        val prefix = (0 until indentation).joinToString("") { " " }
        when (data) {
            null -> println("$prefix$name = null")
            is Int -> println("$prefix$name = $data")
            is String -> println("$prefix$name = $data")
            is Boolean -> println("$prefix$name = $data")
            is Double -> println("$prefix$name = $data")
            is List<*> -> {
                println("$prefix$name")
                data.forEachIndexed { index, value ->
                    display(indentation + 2, index.toString(), value)
                }
            }
            is Set<*> -> display(indentation, name, data.toList())
            is Array<*> -> display(indentation, name, data.toList())
            is Pair<*, *> -> {
                println("$prefix$name ${data.first}: ${data.second}")
            }
            is Map<*, *> -> {
                println("$prefix$name")
                data.entries.sortedBy { it.key.toString() }.forEach {
                    display(indentation + 2, it.key.toString(), it.value)
                }
            }
            else -> {
                try {
                    display(indentation, name, convertToDBFormat(data))
                } catch (e: Exception) {
                    println("${prefix}Unable to transform $name to Map: $e Class is ${data.javaClass} and value $data")
                }
            }
        }
    }

    fun showView(game: Game<T>) {
        println()
        val currentPlayer = game.view(0)["currentPlayer"] as Int?
        display(0, "Game:", game.view(currentPlayer ?: 0))
    }

}

fun main(args: Array<String>) {
    val scanner = Scanner(System.`in`)
    val gameTypeList = ServerGames.games.map { it.key }.sorted()
    gameTypeList.forEachIndexed { index, gameType ->
       println("$index. $gameType")
    }
    println("Which game do you want to play?")
    val chosenGame = scanner.nextLine()
    val gameType = if (chosenGame.toIntOrNull() != null) gameTypeList[chosenGame.toInt()] else chosenGame
    val gameSpec = ServerGames.games[gameType] as GameSpec<Any>?
    if (gameSpec == null) {
        println("No such game type: $chosenGame")
        return
    }
    val view = DslConsoleView(gameSpec)
    view.play(scanner)
    scanner.close()
}

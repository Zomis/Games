package net.zomis.games.dsl

import net.zomis.common.convertToDBFormat
import net.zomis.games.common.Point
import net.zomis.games.dsl.impl.*
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.games.ActionListRequestHandler
import java.util.Scanner

class DslConsoleView<T : Any>(private val game: GameSpec<T>) {

    fun play(scanner: Scanner) {
        val context = GameSetupImpl(game)
        println(context.configClass())

        val config = context.getDefaultConfig()
        println(config)
        println("Enter number of players:")
        val playerCount = scanner.nextLine().toInt()
        val gameImpl = context.createGame(playerCount, config)
        println(gameImpl)

        this.showView(gameImpl)
        while (!gameImpl.isGameOver()) {
            if (this.queryInput(gameImpl, scanner)) {
                this.showView(gameImpl)
                gameImpl.stateKeeper.clear()
            }
        }
    }

    fun choiceActionable(actionLogic: ActionTypeImplEntry<T, Any>, playerIndex: Int, scanner: Scanner): Actionable<T, Any>? {
        val options = actionLogic.availableActions(playerIndex).toList()
        options.forEachIndexed { index, actionable -> println("$index. $actionable") }
        if (options.size <= 1) { return options.getOrNull(0) }
        else {
            println("Choose your action.")
            val actionIndex = scanner.nextLine().toIntOrNull()
            return options.getOrNull(actionIndex ?: -1)
        }
    }

    fun queryInput(game: GameImpl<T>, scanner: Scanner): Boolean {
        println("Available actions is: ${game.actions.actionTypes}. Who is playing and what is your action?")
        val line = scanner.nextLine()
        if (!line.contains(" ")) {
            println("You forgot something.")
            return false
        }
        val (playerIndex, actionType) = line.split(" ")
        val actionLogic = game.actions.types().find { it.name.toLowerCase() == actionType.toLowerCase() }
        if (actionLogic == null) {
            println("Invalid action")
            return false
        }

        val actionParameterClass = actionLogic.actionType.serializedType
        val action: Actionable<T, Any>? = when (actionParameterClass) {
            Point::class -> {
                println("Enter x position where you want to play")
                val x = scanner.nextLine().toInt()
                println("Enter y position where you want to play")
                val y = scanner.nextLine().toInt()
                actionLogic.createActionFromSerialized(playerIndex.toInt(), Point(x, y))
            }
            else -> {
                stepByStepActionable(game, playerIndex.toInt(), actionType, scanner)
//                choiceActionable(actionLogic, playerIndex.toInt(), scanner)
            }
        }
        if (action == null) {
            println("Not a valid action.")
            return false
        }
        val allowed = actionLogic.isAllowed(action)
        println("Action $action allowed: $allowed")
        if (allowed) {
            actionLogic.perform(action)
        }
        return allowed
    }

    private fun stepByStepActionable(game: GameImpl<T>, playerIndex: Int, moveType: String, scanner: Scanner): Actionable<T, Any>? {
        val reqHandler = ActionListRequestHandler(null)

        val chosen = mutableListOf<Any>()
        while (true) {
            val act = reqHandler.availableActionsMessage(game, playerIndex, moveType, chosen).singleOrNull()
                ?: return null

            println(act.first)
            println(" Next " + act.second.nextOptions.size + ". Params " + act.second.parameters.size)
            val next = act.second.nextOptions
            next.forEachIndexed { index, value -> println("$index. Next $value") }

            val params = act.second.parameters
            params.forEachIndexed { index, value -> println("${index + next.size}. Choice $value") }

            val choice = scanner.nextLine().toIntOrNull() ?: return null
            if (choice >= next.size) {
                val param = params.getOrNull(choice - next.size) ?: return null
                val actionType = game.actions.type(moveType)
                val deserializedParam = actionType?.actionType?.deserialize(
                    ActionOptionsContext(game.model, actionType.name, playerIndex), param
                )
                return actionType?.createAction(playerIndex, deserializedParam!!)
            } else {
                val chosenNext = next.getOrNull(choice) ?: return null
                chosen.add(chosenNext)
//                chosen.add(choice)
            }
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

    fun showView(game: GameImpl<T>) {
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

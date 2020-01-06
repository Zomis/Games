package net.zomis.games.dsl

import net.zomis.games.dsl.impl.*
import java.util.Scanner

class DslConsoleView<T : Any>(private val game: GameSpec<T>) {

    fun play(scanner: Scanner) {
        val context = GameSetupImpl(game)
        println(context.configClass())

        val config = context.getDefaultConfig()
        println(config)
        val model = context.createGame(config)
        println(model)

        while (!model.isGameOver()) {
            this.showView(model)
            this.queryInput(model, scanner)
        }
    }

    fun queryInput(game: GameImpl<T>, scanner: Scanner): Boolean {
        println("Available actions is: ${game.actions.actionTypes}. Who is playing and what is your action?")
        val (playerIndex, actionType) = scanner.nextLine().split(" ")
        val actionLogic = game.actions.type(actionType)
        if (actionLogic == null) {
            println("Invalid action")
            return false
        }

        val actionParameterClass = actionLogic.parameterClass
        val action: Actionable<T, Any>? = when (actionParameterClass) {
            Point::class -> {
                println("Enter position where you want to play")
                val x = scanner.nextLine().toInt()
                val y = scanner.nextLine().toInt()
                actionLogic.createAction(playerIndex.toInt(), Point(x, y))
            }
            else -> {
                val options = actionLogic.availableActions(playerIndex.toInt()).toList()
                options.forEachIndexed { index, actionable -> println("$index. $actionable") }
                if (options.size == 1) { options.getOrNull(0) }
                else {
                    println("Choose your action.")
                    val actionIndex = scanner.nextLine().toIntOrNull()
                    options.getOrNull(actionIndex ?: -1)
                }
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

    fun showView(game: GameImpl<T>) {
        println()
        println(game.view(0))
    }

}

fun main(args: Array<String>) {
    val scanner = Scanner(System.`in`)
    val view = DslConsoleView(DslUR().gameUR)
    view.play(scanner)
    scanner.close()
}

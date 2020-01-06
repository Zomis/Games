package net.zomis.games.dsl

import net.zomis.games.dsl.impl.*
import net.zomis.tttultimate.games.TTController
import java.util.Scanner

class DslConsoleView<T : Any>(private val game: GameSpec<T>) {

    fun play(scanner: Scanner) {
        val context = GameSetupImpl(game)
        println(context.configClass())

        val config = context.getDefaultConfig()
        println(config)
        val model = context.createGame(config)
        println(model)

        while (true) {
            this.showView(model)
            this.queryInput(model, scanner)
        }

    }

    fun queryInput(game: GameImpl<T>, scanner: Scanner): Boolean {

        println("Who is playing?")
        val playerIndex = scanner.nextLine().toInt()

        println("Available actions is: ${game.availableActionTypes()}. Choose your action:")
        val actionType = scanner.nextLine()
        val actionLogic = game.actionType<Any>(actionType)
        if (actionLogic == null) {
            println("Invalid action")
            return false
        }

        val actionParameterClass = game.actionParameter(actionType)
        val action: Actionable<T, Any>?
        if (actionParameterClass == Point::class) {
            println("Enter position where you want to play")
            val x = scanner.nextLine().toInt()
            val y = scanner.nextLine().toInt()
            action = actionLogic.createAction(playerIndex, Point(x, y))
        } else {
            val options = actionLogic.availableActions(playerIndex).toList()
            options.forEachIndexed { index, actionable -> println("$index. $actionable") }
            println("Choose your action.")
            val actionIndex = scanner.nextLine().toInt()
            action = options.getOrNull(actionIndex)
        }
        if (action == null) {
            println("Not a valid action.")
            return false
        }
        val allowed = actionLogic.actionAllowed(action)
        println("Action $action allowed: $allowed")
        if (allowed) {
            actionLogic.performAction(action)
        }
        return allowed
    }

    fun showView(game: GameImpl<T>) {
        println(game.view(0))
    }

}

fun main(args: Array<String>) {
    val scanner = Scanner(System.`in`)
    val view = DslConsoleView(DslUR().gameUR)
    view.play(scanner)
    scanner.close()
}

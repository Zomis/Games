package net.zomis.games.dsl

import net.zomis.games.dsl.impl.*
import net.zomis.tttultimate.games.TTController
import java.util.Scanner

class DslConsoleView<T : Any> {

    fun play(game: GameSpec<T>, scanner: Scanner) {
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
        val actionLogic = game.actionType<Point>(actionType)
        if (actionLogic == null) {
            println("Invalid action")
            return false
        }

        println("Enter position where you want to play")
        val x = scanner.nextLine().toInt()
        val y = scanner.nextLine().toInt()

        val action = actionLogic.createAction(playerIndex, Point(x, y))
        val allowed = actionLogic.actionAllowed(action)
        println("Action at $x $y allowed: $allowed")
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
    val view = DslConsoleView<TTController>()
    view.play(DslTTT().game, scanner)
    scanner.close()
}

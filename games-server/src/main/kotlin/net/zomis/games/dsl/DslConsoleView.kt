package net.zomis.games.dsl

import net.zomis.tttultimate.games.TTController
import java.util.*

class DslConsoleView<T : Any> {
    val context = GameDslContext<T>()

    fun play(game: GameDsl<T>, scanner: Scanner) {
        game(context)
        println(context.configClass)

        // Setup model
        val modelContext = GameModelContext<T, Any>()
        context.modelDsl(modelContext)
        val config = modelContext.creator()

        println(config)
        val model = modelContext.factory(config)
        println(model)

        while (true) {
            this.showView(model)
            this.queryInput(model, scanner)
        }

    }

    fun queryInput(model: T, scanner: Scanner): Boolean {
        val logicContext = GameLogicContext(model)
        context.logicDsl(logicContext)

        println("Who is playing?")
        val playerIndex = scanner.nextLine().toInt()

        println("Available actions is: ${logicContext.actions.keys}. Choose your action:")
        val actionType = scanner.nextLine()
        val actionLogic = logicContext.actions[actionType]
        if (actionLogic == null) {
            println("Invalid action")
            return false
        }

        val logic2dContext = GameLogicContext2D<T, Any?>(model)
        actionLogic(logic2dContext)
        println("Enter position where you want to play")
        val x = scanner.nextLine().toInt()
        val y = scanner.nextLine().toInt()
        if ((0..logic2dContext.size.first).contains(x) && (0..logic2dContext.size.second).contains(y)) {
            val tile = logic2dContext.getter(x, y)
            if (tile == null) {
                println("Tile not found: $x, $y")
                return false
            }
            val action = Action2D<T, Any?>(model, playerIndex, x, y, tile)
            val allowed = logic2dContext.allowedCheck(action)
            println("Action at $x $y allowed: $allowed")
            if (allowed) {
                logic2dContext.effect(action)
            }
            return allowed
        } else {
            println("Tile is outside range: $x, $y")
            return false
        }
    }

    fun showView(model: T) {
        val viewContext = GameViewContext(model)
        context.viewDsl(viewContext)
        val view = viewContext.result()
        println(view)
    }

}

fun main(args: Array<String>) {
    val scanner = Scanner(System.`in`)
    val view = DslConsoleView<TTController>()
    view.play(DslTTT().game, scanner)
    scanner.close()
}

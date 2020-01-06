package net.zomis.games.dsl

import net.zomis.common.convertToDBFormat
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.*
import net.zomis.games.server2.Server2
import net.zomis.games.server2.ServerConfig
import net.zomis.games.server2.clients.FakeClient
import java.util.Scanner
import java.util.UUID

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
                if (options.size <= 1) { options.getOrNull(0) }
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

fun dbTestUR() {
    val events = EventSystem()
    Server2(events).start(ServerConfig().also { it.database = true })
    val fakeClient = FakeClient(UUID.fromString("deb00deb-8378-0000-0001-000000000000"))
    Thread.sleep(1000)
    fakeClient.sendToServer(events, """
        {"type": "Auth", "provider": "guest", "token": false }
    """)
    Thread.sleep(1000)
    fakeClient.sendToServer(events, """
        {"type": "ClientGames", "gameTypes": ["DSL-UR"], "maxGames": 1 }
    """)
    Thread.sleep(1000)
    fakeClient.sendToServer(events, """
        {"type": "Invite", "gameType": "DSL-UR", "invite": ["#AI_Random_DSL-UR"] }
    """)
    Thread.sleep(1000)
    fakeClient.sendToServer(events, """
        {"type": "move", "gameType": "DSL-UR", "gameId": 1, "player": 0,
         "moveType": "roll", "move": null }
    """)
    Thread.sleep(15000)
    fakeClient.sendToServer(events, """
        {"type": "ViewRequest", "gameType": "DSL-UR", "gameId": 1 }
    """)
    Thread.sleep(15000)

    fakeClient.sendToServer(events, """
        {"type": "move", "gameType": "DSL-UR", "gameId": 1, "player": 0,
         "moveType": "move", "move": 0 }
    """)
    Thread.sleep(5000)
    fakeClient.sendToServer(events, """
        {"type": "ViewRequest", "gameType": "DSL-UR", "gameId": 1 }
    """)
    Thread.sleep(10000)
}

fun main(args: Array<String>) {
    val scanner = Scanner(System.`in`)
    val view = DslConsoleView(DslUR().gameUR)
    view.play(scanner)
    scanner.close()
}

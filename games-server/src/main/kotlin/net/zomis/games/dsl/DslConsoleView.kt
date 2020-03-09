package net.zomis.games.dsl

import net.zomis.common.convertToDBFormat
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.*
import net.zomis.games.server2.Server2
import net.zomis.games.server2.ServerConfig
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.ActionListRequestHandler
import net.zomis.games.server2.games.ServerGame
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

    fun choiceActionable(actionLogic: ActionTypeImplEntry<T, Any, Actionable<T, Any>>, playerIndex: Int, scanner: Scanner): Actionable<T, Any>? {
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

        val actionParameterClass = actionLogic.parameterClass
        val action: Actionable<T, Any>? = when (actionParameterClass) {
            Point::class -> {
                println("Enter x position where you want to play")
                val x = scanner.nextLine().toInt()
                println("Enter y position where you want to play")
                val y = scanner.nextLine().toInt()
                actionLogic.createAction(playerIndex.toInt(), Point(x, y))
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
                return game.actions.type(moveType)?.createAction(playerIndex, param)
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
            is Int -> println("$prefix$name = $data")
            is String -> println("$prefix$name = $data")
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
                data.entries.sortedBy { it.key as String }.forEach {
                    display(indentation + 2, it.key as String, it.value)
                }
            }
            else -> {
                try {
                    display(indentation, name, convertToDBFormat(data!!))
                } catch (e: Exception) {
                    println("${prefix}Unable to transform $name to Map: $e Class is ${data?.javaClass} and value $data")
                }
            }
        }
    }

    fun showView(game: GameImpl<T>) {
        println()
        display(0, "Game:", game.view(0))
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

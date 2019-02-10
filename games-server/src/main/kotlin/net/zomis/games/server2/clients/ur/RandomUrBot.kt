package net.zomis.games.server2.clients.ur

import klogging.KLoggers
import net.zomis.games.ur.RoyalGameOfUr
import java.net.URI

class RandomUrBot(url: String) {

    private val logger = KLoggers.logger(this)

    private var gameId: String? = null
    private var playerIndex: Int = -1
    val client = WSClient(URI(url))

    fun play() {
        client.connectBlocking()
        client.send("""{ "type": "ClientGames", "gameTypes": ["UR"], "maxGames": 1 }""")
        client.send("""v1:{ "game": "UR", "type": "matchMake" }""")
        val controller = RoyalGameOfUr()
        val gameStart = client.takeUntilJson { it.getText("type") == "GameStarted" && it.getText("gameType") == "UR" }
        gameId = gameStart.getText("gameId")
        playerIndex = gameStart.getInt("yourIndex")

        var allowMove = true
        loop@while (true) {

            if (allowMove && controller.currentPlayer == playerIndex) {
                Thread.sleep(1200)
                if (controller.isRollTime()) {
                    sendRoll()
                } else if (controller.isMoveTime) {
                    sendMove(controller)
                }
            }
            allowMove = false

            Thread.sleep(100)

            val json = client.expectJsonObject { true }
            logger.info { "$controller received: $json"  }
            val type = json.getText("type")
            when (type) {
                "PlayerEliminated" -> {
                    if (json.getInt("player") == playerIndex) {
                        break@loop
                    }
                }
                "GameMove" -> {
                    if (json.getText("moveType") == "move") {
                        val pos = json.getInt("move")
                        controller.move(controller.currentPlayer, pos, controller.roll)
                    }
                    allowMove = true
                }
                "GameState" -> {
                    if (json.has("roll")) {
                        controller.doRoll(json.getInt("roll"))
                    }
                }
                "IllegalMove" -> {
                    throw IllegalArgumentException(json.toString())
                }
            }
        }
        client.closeBlocking()
    }

    private fun sendRoll() {
        client.send("""v1:{ "game": "UR", "gameId": "$gameId", "type": "move", "moveType": "roll", "move": "" }""")
    }

    private fun sendMove(controller: RoyalGameOfUr) {
        val move = (0..15).filter { controller.canMove(controller.currentPlayer, it, controller.roll) }
                .shuffled().first()

        client.send("""v1:{ "game": "UR", "gameId": "$gameId", "type": "move", "moveType": "move", "move": $move }""")
    }

}

fun main(args: Array<String>) {
    RandomUrBot("ws://127.0.0.1:8081").play()
}

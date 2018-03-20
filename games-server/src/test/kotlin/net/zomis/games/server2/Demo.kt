package net.zomis.games.server2

import java.net.URI

fun main(args: Array<String>) {
    val p1 = WSClient(URI("ws://127.0.0.1:8081"))
    p1.connectBlocking()

    val p2 = WSClient(URI("ws://127.0.0.1:8081"))
    p2.connectBlocking()

    p1.send("""v1:{ "game": "Connect2", "type": "matchMake" }""")
    p1.send("""v1:{ "game": "Connect4", "type": "matchMake" }""")
    p2.send("""v1:{ "game": "Connect4", "type": "matchMake" }""")

    p1.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 3 }""")
    p2.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 4 }""")
    p1.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 2 }""")
    p2.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 5 }""")
    p1.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 1 }""")
    p2.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 6 }""")

    // Try to cheat - wrong player
    p2.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 6 }""")

    // Win the game
    p1.send("""v1:{ "game": "Connect4", "gameId": "1", "type": "move", "move": 0 }""")

    p1.close()
    p2.close()
}
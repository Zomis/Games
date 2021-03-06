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
        val view = ConsoleView<T>()
        val controller = ConsoleController<T>()
        runBlocking {
            replayable.playThrough {
                controller.inputRepeat { controller.queryInput(replayable.game, scanner) }
            }
            val savedReplay = entryPoint.replay(replay.data()).goToEnd()
            listOf<Int?>(null).plus(replayable.game.playerIndices).forEach {
                val match = replayable.game.view(it) == savedReplay.game.view(it)
                println("Replay for player $it verification: $match")
            }
        }
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

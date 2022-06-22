package net.zomis.games.dsl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.flow.GameFlowImpl
import net.zomis.games.listeners.ConsoleControl
import net.zomis.games.listeners.ConsoleViewer
import net.zomis.games.listeners.PlayerController
import net.zomis.games.listeners.ReplayListener
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.ServerAIs
import java.util.Scanner

class DslConsoleView<T : Any>(private val game: GameSpec<T>) {

    fun play(scanner: Scanner) {
        val entryPoint = GamesImpl.game(game)
        val setup = entryPoint.setup()
        val config = setup.configs()
        println(config)
        // TODO: Ask for game config(s), make it configurable with JSON-like entry
        val playerCount = if (setup.playersCount.count() == 1) setup.playersCount.random() else {
            println("Enter number of players: (${setup.playersCount})")
            scanner.nextLine().toInt()
        }

        val replayListener = ReplayListener(game.name)
        runBlocking {
            val game = entryPoint.setup().startGame(this, playerCount) { g ->
                listOf(
                    ConsoleViewer(g),
                    ConsoleControl(g, scanner),
                    replayListener,
                    PlayerController(g, 0.toSingleList()) { controller ->
                        ServerAIs(AIRepository(), emptySet()).randomActionable(controller.game, controller.playerIndex)
                    }
                )
            }
            if (game is GameFlowImpl<*>) {
                while (game.isRunning()) {
                    game.feedbackReceiverFlow().collect {
                        println("Received feedback: $it")
                    }
                }
            }
        }

        if (true) return
        val replay = entryPoint.inMemoryReplay()
        val replayable = entryPoint.replayable(playerCount, config, replay)
        runBlocking {
            val savedReplay = entryPoint.replay(replay.data()).goToEnd()
            listOf<Int?>(null).plus(replayable.game.playerIndices).forEach {
                val match = replayable.game.view(it) == savedReplay.game.view(it)
                println("Replay for player $it verification: $match")
            }
        }
    }

}

class DslConsoleSetup {
    fun chooseGame(scanner: Scanner): GameSpec<Any> {
        val gameTypeList = ServerGames.games.map { game -> game.key }.sorted()
        gameTypeList.forEachIndexed { index, gameType ->
            println("$index. $gameType")
        }
        println("Which game do you want to play?")
        val chosenGame = scanner.nextLine()
        val gameType = if (chosenGame.toIntOrNull() != null) gameTypeList[chosenGame.toInt()] else chosenGame
        val gameSpec = ServerGames.games[gameType] as GameSpec<Any>?
        if (gameSpec == null) {
            println("No such game type: $chosenGame")
            throw IllegalStateException()
        }
        return gameSpec
    }
}

fun main() {
    val scanner = Scanner(System.`in`)
    val gameSpec = DslConsoleSetup().chooseGame(scanner)
    val view = DslConsoleView(gameSpec)
    view.play(scanner)
    scanner.close()
}

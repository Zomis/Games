package net.zomis.games.dsl

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.jackson.ReplayDataDeserializer
import net.zomis.games.listeners.*
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.ServerAIs
import java.nio.file.Path
import java.util.Scanner
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

class DslConsoleView<T : Any>(private val game: GameSpec<T>) {

    fun resume(replayData: ReplayData, file: Path, scanner: Scanner) {
        val mapper = jacksonObjectMapper()
        val entryPoint = GamesImpl.game(game)
        val replayListener = ReplayListener(game.name)
        val blockingGameListener = BlockingGameListener()

        runBlocking {
            val savedReplay = entryPoint.replay(this, replayData, { clazz, serialized ->
                println("Converting $serialized (${serialized::class}) to $clazz")
                mapper.convertValue(serialized, clazz.java)
            }) { g ->
                listOf(
                    ConsoleViewer(g as Game<Any>).postReplay(replayData),
                    ConsoleControl(g as Game<Any>, scanner).postReplay(replayData),
                    replayListener,
                    FileReplay(file, replayListener).postReplay(replayData),
                    blockingGameListener,
                    PlayerController(g, 0.toSingleList()) { controller ->
                        ServerAIs(AIRepository(), emptySet()).randomActionable(controller.game, controller.playerIndex)
                    }.postReplay(replayData)
                )
            }.goToEnd().awaitCatchUp()
            if (savedReplay.game.isGameOver()) {
                println("Game was already finished")
                file.deleteIfExists()
                play(scanner, file)
            }
            println("Caught up")
            blockingGameListener.awaitGameEnd()
            println("Game over")
//            ConsoleView<T>().showView(savedReplay.game, 0)
        }
    }

    fun play(scanner: Scanner, file: Path? = null) {
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
            val blockingGameListener = BlockingGameListener()
            val game = entryPoint.setup().startGame(this, playerCount) { g ->
                listOf(
                    ConsoleViewer(g),
                    ConsoleControl(g, scanner),
                    replayListener,
                    FileReplay(file, replayListener),
                    blockingGameListener,
                    PlayerController(g, 0.toSingleList()) { controller ->
                        ServerAIs(AIRepository(), emptySet()).randomActionable(controller.game, controller.playerIndex)
                    }
                )
            }
            blockingGameListener.awaitGameEnd()

            val savedReplay = entryPoint.replay(this, replayListener.data()).goToEnd()
            listOf<Int?>(null).plus(game.playerIndices).forEach {
                val match = game.view(it) == savedReplay.game.view(it)
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

    fun createNewGame(file: Path) {
        val gameSpec = DslConsoleSetup().chooseGame(scanner)
        val view = DslConsoleView(gameSpec)
        view.play(scanner, file)
    }

    val file = Path.of("console-view.json")
    if (file.isRegularFile()) {
        val tree: ObjectNode = jacksonObjectMapper().readTree(file.inputStream()) as ObjectNode
        val t = ReplayDataDeserializer.deserialize(tree) { ServerGames.games[it] }
        val view = DslConsoleView(ServerGames.games.getValue(t.gameType))
        view.resume(t, file, scanner)
    } else {
        createNewGame(file)
    }
    scanner.close()
}

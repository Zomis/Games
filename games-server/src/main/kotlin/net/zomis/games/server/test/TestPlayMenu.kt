package net.zomis.games.server.test

import net.zomis.games.server2.ServerGames
import java.io.File
import java.util.*

object TestPlayMenu {
    val root = File("playthroughs")

    val choices = TestPlayChoices(
        gameName = { ServerGames.games.keys.random() },
        playersCount = { it.setup().playersCount.random() },
        config = { it.setup().getDefaultConfig() }
    )

    fun main(game: String? = null) {
        val f = root
        f.walk().filter { it.isFile && it.name.endsWith(".json") }.forEach {
            if (game == null || it.absolutePath.contains(game)) {
                println(it.absolutePath)
                PlayTests.fullJsonTest(it, choices)
            }
        }
    }

    fun menu() {
        println("Choose your option")
        println("C: Create new test")
        println("F: Run a file test")
        println("A: Run all tests")
        println("G: Run all tests for specific game")

        val scanner = Scanner(System.`in`)
        when (scanner.nextLine()) {
            "" -> return
            "f", "F" -> {
                println("Enter file name")
                var f = root
                while (!f.isFile) {
                    f.listFiles()!!.forEach {
                        println(it.name)
                    }
                    f = File(f, scanner.nextLine())
                }
                PlayTests.fullJsonTest(f, choices)
            }
            "c", "C" -> {
                ServerGames.games.map { it.key }.sorted().forEach { println(it) }
                println("Enter game name")
                val chosenGame = scanner.nextLine()
                val setup = ServerGames.setup(chosenGame) ?: throw IllegalArgumentException("Invalid game type: $chosenGame")

                println("Enter players (${setup.playersCount})")
                val playersCount = scanner.nextLine().toInt()
                PlayTests.createNew(File("playthroughs", "t.json"), chosenGame, playersCount, null)
            }
            "a", "A" -> main()
            "g", "G" -> {
                ServerGames.games.map { it.key }.sorted().forEach { println(it) }
                println("Enter game name")
                val chosenGame = scanner.nextLine()

                main(chosenGame)
            }
        }
    }

    fun file(s: String): File = File(root, s)

}

fun main() {
    PlayTests.fullJsonTest(TestPlayMenu.file("t.json"), TestPlayMenu.choices)
//    TestPlayMenu.menu()
}

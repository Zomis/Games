package net.zomis.games.dsl

import net.zomis.common.convertToDBFormat
import net.zomis.games.dsl.impl.Game

class ConsoleView<T: Any> {

    fun showView(game: Game<T>, playerIndex: Int? = null) {
        println()
        val currentPlayer = game.view(0)["currentPlayer"] as Int?
        display(0, "Game:", game.view(playerIndex ?: currentPlayer ?: 0))
    }

    fun display(indentation: Int, name: String, data: Any?) {
        val prefix = (0 until indentation).joinToString("") { " " }
        when (data) {
            null -> println("$prefix$name = null")
            is Int -> println("$prefix$name = $data")
            is String -> println("$prefix$name = $data")
            is Boolean -> println("$prefix$name = $data")
            is Double -> println("$prefix$name = $data")
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
                data.entries.sortedBy { it.key.toString() }.forEach {
                    display(indentation + 2, it.key.toString(), it.value)
                }
            }
            else -> {
                try {
                    display(indentation, name, convertToDBFormat(data))
                } catch (e: Exception) {
                    println("${prefix}Unable to transform $name to Map: $e Class is ${data.javaClass} and value $data")
                }
            }
        }
    }

}
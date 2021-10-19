package net.zomis.games.dsl

import net.zomis.common.convertToDBFormat
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.impl.Game

class ConsoleView<T: Any> {

    fun showView(game: Game<T>, playerIndex: Int? = null) {
        println()
        val currentPlayer = game.view(0)["currentPlayer"] as Int?
        print(0, "Game:", game.view(playerIndex ?: currentPlayer ?: 0))
    }

    fun print(indentation: Int, name: String, data: Any?) {
        strings(indentation, name, data).forEach { println(it) }
    }

    fun strings(indentation: Int, name: String?, data: Any?): List<String> {
        val prefix = (0 until indentation).joinToString("") { " " }
        fun start(postName: String): String {
            if (name != null) return "$prefix$name$postName"
            return prefix
        }
        if (name == "grid") {
            return start("").toSingleList() + displayGrid(indentation + 2, data as List<List<*>>)
        }
        val strings = sequence {
            when (data) {
                null, is Int, is String, is Boolean, is Double -> yield(start (" = ") + data.toString())
                is List<*> -> {
                    yield(start(""))
                    data.forEachIndexed { index, value ->
                        yieldAll(strings(indentation + 2, index.toString(), value))
                    }
                }
                is Set<*> -> strings(indentation, name, data.toList())
                is Array<*> -> strings(indentation, name, data.toList())
                is Pair<*, *> -> {
                    yield(start(" ") + data.first + ": " + data.second)
                }
                is Map<*, *> -> {
                    yield(start(""))
                    data.entries.sortedBy { it.key.toString() }.forEach {
                        yieldAll(strings(indentation + 2, it.key.toString(), it.value))
                    }
                }
                else -> {
                    try {
                        yieldAll(strings(indentation, name, convertToDBFormat(data)))
                    } catch (e: Exception) {
                        yield("${prefix}Unable to transform $name to Map: $e Class is ${data.javaClass} and value $data")
                    }
                }
            }
        }
        return strings.toList()
    }

    fun displayGrid(indentation: Int, data: List<List<*>>): List<String> {
        if (data.isEmpty()) {
            return emptyList()
        }
        val prefix = (0 until indentation).joinToString("") { " " }
        return sequence {
            /* one char = one char, one line = with spaces, multi-line = with |- separators */
            val dataStrings = data.map { row ->
                row.map { strings(0, null, it) }
            }
            val maxLinesPerRow = dataStrings.map { row -> row.map { it.size }.maxOrNull()!! }
            val maxWidthPerCol = dataStrings[0].indices.map { colIndex ->
                dataStrings.map { it[colIndex] }.flatten().map { it.length }.maxOrNull()!!
            }
            if (maxLinesPerRow.maxOrNull()!! == 1 && maxWidthPerCol.maxOrNull()!! == 1) {
                // use single chars
                yieldAll(dataStrings.map { row -> prefix + row.joinToString("") { it.joinToString() } })
            } else if (maxLinesPerRow.maxOrNull()!! == 1) {
                // use spaces
                yieldAll(dataStrings.mapIndexed { y, list ->
                    val line = list.mapIndexed { x, cell ->
                        cell.joinToString().padStart(maxWidthPerCol[x])
                    }.joinToString(" ")
                    prefix + line
                })
            } else {
                // use | and -
                val lines = dataStrings.mapIndexed { y, list ->
                    list.mapIndexed { x, cell ->
                        cell.filter { it.isNotEmpty() }.map { it.padStart(maxWidthPerCol[x]) }
                    }
                }
                val maxLine = maxWidthPerCol.sum() + maxWidthPerCol.size + 1 // col widths + X separators + 1 extra for start/end
                val lineSeparator = "".padEnd(maxLine, '-')
                yield(prefix + lineSeparator)
                for (row in lines) {
                    val maxCellLines = row.map { it.size }.maxOrNull() ?: 1
                    val columnLines = (0 until maxCellLines).map { line ->
                        row.map { cell ->
                            cell[line]
                        }.joinToString("|", "|", "|")
                    }.map { prefix + it }
                    yieldAll(columnLines)
                    yield(prefix + lineSeparator)
                }
            }
        }.toList()
    }

}
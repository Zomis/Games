package net.zomis.games.server.test

import java.util.Scanner

class PlayTestInput(val scanner: Scanner) {
    fun playerIndex(): Int {
        println("Enter player index")
        return scanner.nextLine().toInt()
    }

    fun <T> fromList(list: List<T>, prompt: String? = null): T {
        if (prompt != null) {
            println(prompt)
        }
        list.forEachIndexed { index, t ->
            println("$index: $t")
        }
        val index = scanner.nextLine().toInt()
        return list[index]
    }

    fun number(prompt: String): Int {
        println(prompt)
        return scanner.nextLine().toInt()
    }

}
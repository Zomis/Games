package net.zomis.games.cards

import net.zomis.games.impl.Hanabi
import net.zomis.games.server2.db.DBIntegration

class HanabiProbabilities {

    fun hanabi() {
//        val dbGame = DBIntegration().loadGame("41cbc0f2-f14a-4f65-aee7-04ef7221e585")!!
        val dbGame = DBIntegration().loadGame("9eb03cf5-9f85-4c8f-9304-2e791f518556")!!
        val game = dbGame.at(16)

        val hanabi = game.model as Hanabi
        val playerIndex = 1
        val results = net.zomis.games.impl.HanabiProbabilities.showProbabilities(hanabi, playerIndex)
        results.forEachIndexed {index, cardResults ->
            println("Card $index")
            cardResults.forEach { (key, probability) ->
                println("  $key: $probability")
            }
        }
    }

}

fun main() {
    HanabiProbabilities().hanabi()
}

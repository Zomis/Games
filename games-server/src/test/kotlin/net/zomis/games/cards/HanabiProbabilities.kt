package net.zomis.games.cards

import net.zomis.games.cards.probabilities.CardCounter
import net.zomis.games.cards.probabilities.CardSolutions
import net.zomis.games.cards.probabilities.v2.CardAnalyzeSolution
import net.zomis.games.cards.probabilities.v2.CardAnalyzeSolutions
import net.zomis.games.impl.Hanabi
import net.zomis.games.impl.HanabiCard
import net.zomis.games.impl.HanabiColor
import net.zomis.games.server2.db.DBIntegration
import org.junit.jupiter.api.Test

class HanabiProbabilities {

    fun hanabi() {
        val dbGame = DBIntegration().loadGame("41cbc0f2-f14a-4f65-aee7-04ef7221e585")!!
        val game = dbGame.at(32)
        val hanabi = game.model as Hanabi

        val playerIndex = 0
        val playerHidden = hanabi.players[playerIndex].cards.toList().mapIndexed {index, it ->
            CardZone(mutableListOf(it)).also { z -> z.name = "Player $index" }
        }
        val knowns = hanabi.players.minus(hanabi.players[playerIndex]).flatMap { it.cards.toList() }
            .plus(hanabi.colors.flatMap { it.board.toList() })
            .plus(hanabi.colors.flatMap { it.discard.toList() })
        val deck = hanabi.deck.also { it.name = "Deck" }

        val counter = CardCounter<HanabiCard>()
            .hiddenZones(*playerHidden.toTypedArray())
            .hiddenZones(deck)
//            .knownZones(CardZone(knowns.toMutableList()))

        playerHidden.forEach { zone ->
            val card = zone.cards.single()
            card.possibleValues.forEach { (value, isValue) ->
                println("Add valueKnown $card $value $isValue")
                val intValue = if (isValue) 1 else 0
                counter.exactRule(zone, intValue) { it.value == value }
            }
            card.possibleColors.forEach { (color, isColor) ->
                println("Add colorKnown $card $color $isColor")
                val intValue = if (isColor) 1 else 0
                counter.exactRule(zone, intValue) { it.color == color }
            }
        }

//        DslConsoleView(HanabiGame.game).showView(game as GameImpl<Hanabi>)

        val s2 = CardAnalyzeSolutions(counter.solve2().toList())
        showDistributions2(s2, playerHidden, hanabi)

        if (true) return
        val solutions = counter.solve()

        println(solutions.getTotalCombinations())
        solutions.getSolutions().forEach {
            outputSolution(it)
        }

        showDistributions(solutions, playerHidden, hanabi)
    }

    fun isCard(color: HanabiColor, value: Int): (HanabiCard) -> Boolean = { isColor(color)(it) && isNumber(value)(it) }
    fun isColor(color: HanabiColor): (HanabiCard) -> Boolean = { it.color == color }
    fun isNumber(value: Int): (HanabiCard) -> Boolean = { it.value == value }
    fun isUselessCard(hanabi: Hanabi): (HanabiCard) -> Boolean = { card ->
        val previousRange = 1 until card.value
        val colorData = hanabi.colorData(card)
        previousRange.any { previous ->
            val exists = hanabi.countInDeck(card.color, previous)
            val discarded = colorData.discard.cards.count { it.value == previous && it.color == card.color }
            exists == discarded
        }
    }
    fun playable(hanabi: Hanabi): (HanabiCard) -> Boolean = { hanabi.playAreaFor(it) != null }
    fun beenPlayed(hanabi: Hanabi): (HanabiCard) -> Boolean = { hanabi.colorData(it).board.cards.any { c -> c.value == it.value } }
    fun notTheOnlyOne(hanabi: Hanabi): (HanabiCard) -> Boolean = { !beenPlayed(hanabi)(it) && !isOnlyOneRemaining(hanabi)(it) }
    fun discardable(hanabi: Hanabi): (HanabiCard) -> Boolean = { beenPlayed(hanabi)(it) || !isOnlyOneRemaining(hanabi)(it)  || isUselessCard(hanabi)(it) }
    fun indispensible(hanabi: Hanabi): (HanabiCard) -> Boolean = { !discardable(hanabi)(it) } // !beenPlayed(hanabi)(it) && isOnlyOneRemaining(hanabi)(it)
    fun indispensible2(hanabi: Hanabi): (HanabiCard) -> Boolean = { !beenPlayed(hanabi)(it) && isOnlyOneRemaining(hanabi)(it) }
    fun isOnlyOneRemaining(hanabi: Hanabi): (HanabiCard) -> Boolean = {
        val colorData = hanabi.colorData(it)
        val count = hanabi.countInDeck(it.color, it.value)
        val exists = colorData.board.cards.count { c -> c.value == it.value } + colorData.discard.cards.count { c -> c.value == it.value }
        count - exists == 1
    }

    private fun showDistributions2(solutions: CardAnalyzeSolutions<HanabiCard>, playerHidden: List<CardZone<HanabiCard>>, hanabi: Hanabi) {
        val probabilities = mapOf<String, (HanabiCard) -> Boolean>(
                "playable" to playable(hanabi),
                "beenPlayed" to beenPlayed(hanabi),
                "notTheOnlyOne" to notTheOnlyOne(hanabi),
                "useless" to isUselessCard(hanabi),
                "discardable" to discardable(hanabi),
                "indispensible" to indispensible(hanabi),
                "indispensible2" to indispensible2(hanabi)
        )
        .plus(hanabi.colors.map { colorData -> colorData.color.name to isColor(colorData.color) })
        .plus((1..5).map { "Value $it" to isNumber(it) })
        .plus(hanabi.colors.flatMap { color ->
            (1..5).map { "${color.color.name} $it" to isCard(color.color, it) }
        })

        playerHidden.forEach { hidden ->
            println(hidden)
            probabilities.forEach { (name, predicate) ->
                val probabilityNumbers = solutions.getProbabilityDistributionOf(hidden, predicate)
                val trueProbability = probabilityNumbers[1]
                if (trueProbability > 0) {
                    println("  $name: $trueProbability")
                }
            }
            println()
        }
    }

    private fun showDistributions(solutions: CardSolutions<CardZone<HanabiCard>, HanabiCard>, playerHidden: List<CardZone<HanabiCard>>, hanabi: Hanabi) {
        val probabilities = mapOf<String, (HanabiCard) -> Boolean>(
            "playable" to playable(hanabi),
            "beenPlayed" to beenPlayed(hanabi),
            "notTheOnlyOne" to notTheOnlyOne(hanabi),
            "useless" to isUselessCard(hanabi),
            "discardable" to discardable(hanabi),
            "indispensible" to indispensible(hanabi),
            "indispensible2" to indispensible2(hanabi)
        )
        .plus(hanabi.colors.map { colorData -> colorData.color.name to isColor(colorData.color) })
        .plus((1..5).map { "Value $it" to isNumber(it) })
        .plus(hanabi.colors.flatMap { color ->
            (1..5).map { "${color.color.name} $it" to isCard(color.color, it) }
        })

        playerHidden.forEachIndexed { index, hidden ->
            println("Card $index")
            probabilities.forEach { (name, predicate) ->
                val probabilityNumbers = solutions.getProbabilityDistributionOf(hidden, predicate)
                val trueProbability = probabilityNumbers[1]
                if (trueProbability > 0) {
                    println("  $name: $trueProbability")
                }
            }
            println()
        }
    }

}

fun main() {
    HanabiProbabilities().hanabi()
}

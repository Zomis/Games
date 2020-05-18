package net.zomis.games.impl

import net.zomis.games.cards.CardZone
import net.zomis.games.cards.probabilities.CardCounter
import net.zomis.games.cards.probabilities.v2.CardAnalyzeSolutions

data class HanabiCardProbabilities(
    val playable: Double,
    val beenPlayed: Double,
    val notTheOnlyOne: Double,
    val useless: Double,
    val discardable: Double,
    val indispensible: Double,
    val colors: Map<HanabiColor, Double>,
    val numbers: Map<Int, Double>,
    val exactCard: Map<HanabiCard, Double>
)
data class HanabiHandProbabilities(val hand: List<HanabiCardProbabilities>)

object HanabiProbabilities {

    fun solver(game: Hanabi, playerIndex: Int): Pair<CardCounter<HanabiCard>, List<CardZone<HanabiCard>>> {
        val playerHidden = game.players[playerIndex].cards.toList().mapIndexed {index, it ->
            CardZone(mutableListOf(it)).also { z -> z.name = "Player $index" }
        }
        val deck = game.deck.also { it.name = "Deck" }
        val counter = CardCounter<HanabiCard>()
                .hiddenZones(*playerHidden.toTypedArray())
                .hiddenZones(deck)

        playerHidden.forEach { zone ->
            val card = zone.cards.single()
            card.possibleValues.forEach { (value, isValue) ->
                val intValue = if (isValue) 1 else 0
                counter.exactRule(zone, intValue) { it.value == value }
            }
            card.possibleColors.forEach { (color, isColor) ->
                val intValue = if (isColor) 1 else 0
                counter.exactRule(zone, intValue) { it.color == color }
            }
        }
        return counter to playerHidden
    }

    fun showProbabilities(game: Hanabi, playerIndex: Int): List<Map<String, Double>> {
        val (counter, playerHidden) = solver(game, playerIndex)
        val s2 = CardAnalyzeSolutions(counter.solve2().toList())
        return showDistributions2(s2, playerHidden, game)
    }

    fun calculateProbabilities(game: Hanabi, playerIndex: Int): HanabiHandProbabilities {
        val (counter, playerHidden) = solver(game, playerIndex)
        val s2 = CardAnalyzeSolutions(counter.solve2().toList())
        return toProbabilities(s2, playerHidden, game)
    }

    fun isCard(color: HanabiColor, value: Int): (HanabiCard) -> Boolean = { isColor(color)(it) && isNumber(value)(it) }
    fun isColor(color: HanabiColor): (HanabiCard) -> Boolean = { it.color == color }
    fun isNumber(value: Int): (HanabiCard) -> Boolean = { it.value == value }
    fun isUselessCard(hanabi: Hanabi): (HanabiCard) -> Boolean = { card ->
        val previousRange = 1 until card.value
        val colorData = hanabi.colorData(card)
        previousRange.any { previous ->
            val exists = hanabi.config.countInDeck(card.color, previous)
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
        val count = hanabi.config.countInDeck(it.color, it.value)
        val exists = colorData.board.cards.count { c -> c.value == it.value } + colorData.discard.cards.count { c -> c.value == it.value }
        count - exists == 1
    }

    fun toProbabilities(solutions: CardAnalyzeSolutions<HanabiCard>, playerHidden: List<CardZone<HanabiCard>>, hanabi: Hanabi): HanabiHandProbabilities {
        return HanabiHandProbabilities(playerHidden.map {hidden ->
            val probabilityLookup: ((HanabiCard) -> Boolean) -> Double = {
                solutions.getProbabilityDistributionOf(hidden, it)[1]
            }
            HanabiCardProbabilities(
                playable = probabilityLookup(playable(hanabi)),
                beenPlayed = probabilityLookup(beenPlayed(hanabi)),
                notTheOnlyOne = probabilityLookup(notTheOnlyOne(hanabi)),
                useless = probabilityLookup(isUselessCard(hanabi)),
                discardable = probabilityLookup(discardable(hanabi)),
                indispensible = probabilityLookup(indispensible(hanabi)),
                colors = hanabi.colors.map { it.color }.associateWith { probabilityLookup(isColor(it)) }.filter { it.value > 0 },
                numbers = (1..5).associateWith { probabilityLookup(isNumber(it)) }.filter { it.value > 0 },
                exactCard = hanabi.colors.flatMap { color -> (1..5).map { number ->
                    HanabiCard(color.color, number, colorKnown = false, valueKnown = false)
                } }.associateWith { probabilityLookup(isCard(it.color, it.value)) }.filter { it.value > 0 }
            ).also { if (it.exactCard.isEmpty()) throw IllegalStateException("Nothing is possible") }
        })
    }

    private fun showDistributions2(solutions: CardAnalyzeSolutions<HanabiCard>, playerHidden: List<CardZone<HanabiCard>>, hanabi: Hanabi): List<Map<String, Double>> {
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

        return playerHidden.map {hidden ->
            probabilities.mapNotNull {
                val probabilityNumbers = solutions.getProbabilityDistributionOf(hidden, it.value)
                val trueProbability = probabilityNumbers[1]
                if (trueProbability > 0) it.key to trueProbability else null
            }.toMap()
        }
    }

}

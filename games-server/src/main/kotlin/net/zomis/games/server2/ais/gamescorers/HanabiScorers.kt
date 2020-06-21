package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.*
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerAnalyzeProvider
import net.zomis.games.server2.ais.scorers.ScorerFactory
import net.zomis.games.server2.ais.scorers.ScorerScope

object HanabiScorers {
    fun ais(): List<ScorerAIFactory<Hanabi>> {
        return listOf(
            aiFirst(),
            aiFirstImproved()
            , aiDebugAnimations()
            , aiDebugHand()
            // AI Second is so far too slow to play
        )
    }
    fun aiFirst() = ScorerAIFactory("Hanabi", "#AI_Probabilistic_Player",
        playProbability.weight(1.01), discardProbability.weight(0.5), playableClues.weight(2.0),
        indispensibleClues.weight(0.7))

    fun aiFirstImproved() = ScorerAIFactory("Hanabi", "#AI_Probabilistic_Player_2",
        playProbability.weight(1.01), discardProbability.weight(0.5), playableClues.weight(2.0),
        indispensibleClues.weight(0.7), playFromRight, discardFromLeft)

    fun aiSecond() = ScorerAIFactory("Hanabi", "#AI_Probabilistic_Player_ClueGiver",
        playProbability.weight(1.01), discardProbability.weight(0.2), playableCardPlayableClue.weight(2.0),
        indispensibleCardIndispensibleClue.weight(1.5), playFromRight, discardFromLeft)

    fun aiDebugAnimations() = ScorerAIFactory("Hanabi", "#AI_Debug_Animations", discardFromLeft, clueLowPlayer.weight(0.1))
    fun aiDebugHand() = ScorerAIFactory("Hanabi", "#AI_Debug_Hand", clue, clueLowPlayer, clueColor)

    fun random() = ScorerAIFactory("Hanabi", "#AI_Random", scorers.simple { 1.0 })

    val scorers = ScorerFactory<Hanabi>()

    val clue = scorers.conditional { action.actionType == HanabiGame.giveClue.name }
    val clueLowPlayer = scorers.conditionalType(HanabiClue::class) { -action.parameter.player.toDouble() } as Scorer<Hanabi, Any>
    val clueColor = scorers.conditionalType(HanabiClue::class) { if (action.parameter.color != null) 1.0 else 0.0 } as Scorer<Hanabi, Any>

    val playableClues = scorers.conditionalType(HanabiClue::class) {
        action.game.players[action.parameter.player].cards.cards.filter { it.matches(action.parameter) }
            .sumByDouble { if (HanabiProbabilities.playable(model).invoke(it)) 1.0 else -0.1 }
    } as Scorer<Hanabi, Any>

    val indispensibleClues = scorers.conditionalType(HanabiClue::class) {
        action.game.players[action.parameter.player].cards.cards.filter { it.matches(action.parameter) }
                .sumByDouble { if (HanabiProbabilities.indispensible(model).invoke(it)) 1.0 else -0.1 }
    } as Scorer<Hanabi, Any>

    val probabilityProvider = scorers.provider {ctx ->
        HanabiProbabilities.calculateProbabilities(ctx.model, ctx.playerIndex).also { probs ->
            probs.hand.forEach { cardProbs -> println(cardProbs) }
        }
    }

    val clueChangeProbabilityProvider = scorers.provider {ctx ->
        val clues = ctx.model.possibleClues(ctx.playerIndex)
        val players = clues.map { it.player }.distinct()
        val probabilitiesBefore = players.map { playerIndex ->
            playerIndex to HanabiProbabilities.calculateProbabilitiesAfterClue(ctx.model, ctx.playerIndex, HanabiClue(playerIndex, null, null))
        }
        clues.associateWith {clue ->
            val before = probabilitiesBefore.first { it.first == clue.player }.second
            val after = HanabiProbabilities.calculateProbabilitiesAfterClue(ctx.model, ctx.playerIndex, clue)
            after - before
        }
    }

    fun cardProbsDiff(scorerScope: ScorerScope<Hanabi, HanabiClue>): List<Pair<HanabiCard, HanabiCardProbabilities>> {
        val probabilityDiffs = scorerScope.require(clueChangeProbabilityProvider)!!
        val probs = probabilityDiffs.getValue(scorerScope.action.parameter)
        val player = scorerScope.action.game.players[scorerScope.action.parameter.player]
        val cardProbs = player.cards.cards.mapIndexed { index, hanabiCard ->
            hanabiCard to probs.hand[index]
        }
        return cardProbs
    }

    val playableCardPlayableClue = scorers.conditionalType(HanabiClue::class) {
        val playable = HanabiProbabilities.playable(model)
        val diffs = cardProbsDiff(this)
        diffs.sumByDouble { if (playable(it.first)) it.second.playable else -it.second.playable }
    } as Scorer<Hanabi, Any>

    val indispensibleCardIndispensibleClue = scorers.conditionalType(HanabiClue::class) {
        val condition = HanabiProbabilities.indispensible(model)
        val diffs = cardProbsDiff(this)
        diffs.sumByDouble { if (condition(it.first)) it.second.playable else -it.second.playable }
    } as Scorer<Hanabi, Any>

    val playFromRight = scorers.conditional { action.actionType == HanabiGame.play.name }.multiply(scorers.simple { (action.parameter as Int).toDouble() * 0.001 })
    val discardFromLeft = scorers.conditional { action.actionType == HanabiGame.discard.name }.multiply(scorers.simple {
        (action.game.current.cards.cards.lastIndex - (action.parameter as Int).toDouble()) * 0.001
    })

    val playProbability = scorers.conditional { action.actionType == HanabiGame.play.name }.multiply(scorers.simple {
        val probabilities = require(probabilityProvider)!!
        val cardIndex = this.action.parameter as Int
        probabilities.hand[cardIndex].playable
    })

    val discardProbability = scorers.conditional { action.actionType == HanabiGame.discard.name }.multiply(scorers.simple {
        val probabilities = require(probabilityProvider)!!
        val cardIndex = this.action.parameter as Int
        probabilities.hand[cardIndex].discardable - probabilities.hand[cardIndex].indispensible
    })

    // If I think card is indispensible, give clue.
    // If I think card can be played, give clue (unless someone else also has this card with a clue about playability?)

    // ClueHelpfulness --> Analyze a player's cards, based on what you know yourself, and determine the sum of changes in
    //                     playable + indispensible.

}
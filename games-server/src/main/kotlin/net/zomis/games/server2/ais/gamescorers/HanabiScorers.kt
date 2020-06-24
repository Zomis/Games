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

    val scorers = ScorerFactory<Hanabi>()

    val clue = scorers.isAction(HanabiGame.giveClue)
    val clueLowPlayer = scorers.action(HanabiGame.giveClue) { -action.parameter.player.toDouble() }
    val clueColor = scorers.action(HanabiGame.giveClue) { if (action.parameter.color != null) 1.0 else 0.0 }

    val playableClues = scorers.action(HanabiGame.giveClue) {
        action.game.players[action.parameter.player].cards.cards.filter { it.matches(action.parameter) }
            .sumByDouble { if (HanabiProbabilities.playable(model).invoke(it)) 1.0 else -0.1 }
    }

    val indispensibleClues = scorers.action(HanabiGame.giveClue) {
        action.game.players[action.parameter.player].cards.cards.filter { it.matches(action.parameter) }
                .sumByDouble { if (HanabiProbabilities.indispensible(model).invoke(it)) 1.0 else -0.1 }
    }

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

    val playableCardPlayableClue = scorers.action(HanabiGame.giveClue) {
        val playable = HanabiProbabilities.playable(model)
        val diffs = cardProbsDiff(this)
        diffs.sumByDouble { if (playable(it.first)) it.second.playable else -it.second.playable }
    }

    val indispensibleCardIndispensibleClue = scorers.action(HanabiGame.giveClue) {
        val condition = HanabiProbabilities.indispensible(model)
        val diffs = cardProbsDiff(this)
        diffs.sumByDouble { if (condition(it.first)) it.second.playable else -it.second.playable }
    }

    val playFromRight = scorers.action(HanabiGame.play) { (action.parameter).toDouble() * 0.001 }
    val discardFromLeft = scorers.action(HanabiGame.discard) {
        (action.game.current.cards.cards.lastIndex - (action.parameter).toDouble()) * 0.001
    }

    val playProbability = scorers.action(HanabiGame.play) {
        val probabilities = require(probabilityProvider)!!
        val cardIndex = this.action.parameter
        probabilities.hand[cardIndex].playable
    }

    val discardProbability = scorers.action(HanabiGame.discard) {
        val probabilities = require(probabilityProvider)!!
        val cardIndex = this.action.parameter
        probabilities.hand[cardIndex].discardable - probabilities.hand[cardIndex].indispensible
    }

    // If I think card is indispensible, give clue.
    // If I think card can be played, give clue (unless someone else also has this card with a clue about playability?)

    // ClueHelpfulness --> Analyze a player's cards, based on what you know yourself, and determine the sum of changes in
    //                     playable + indispensible.

}
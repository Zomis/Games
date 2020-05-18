package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.*
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerAnalyzeProvider
import net.zomis.games.server2.ais.scorers.ScorerFactory

object HanabiScorers {
    fun ais(): List<ScorerAIFactory<Hanabi>> {
        return listOf(
            aiFirst()
        )
    }
    fun aiFirst() = ScorerAIFactory("Hanabi", "#AI_Probabilistic_Player",
        playProbability.weight(1.01), discardProbability.weight(0.5), playableClues.weight(2.0),
        indispensibleClues.weight(0.7))
    fun random() = ScorerAIFactory("Hanabi", "#AI_Random", scorers.simple { 1.0 })

    val scorers = ScorerFactory<Hanabi>()

    val clue = scorers.conditional { action.actionType == HanabiGame.giveClue.name }
    val cheatPlay = scorers.conditional { action.actionType == HanabiGame.play.name }.multiply(scorers.conditional {
        val card = action.game.current.cards[action.parameter as Int].card
        action.game.playAreaFor(card) != null
    })

    val cheatDiscard = scorers.conditional { action.actionType == HanabiGame.discard.name }.multiply(scorers.conditional {
        val card = action.game.current.cards[action.parameter as Int].card.known(true)
        action.game.colors.flatMap { it.board.toList() }.any { c -> c.known(true) == card }
    })

    val playableClues = scorers.conditionalType(HanabiClue::class) {
        action.game.players[action.parameter.player].cards.cards.filter {
            it.matches(action.parameter) && HanabiProbabilities.playable(model).invoke(it)
        }.isNotEmpty().let { if (it) 1.0 else null }
    } as Scorer<Hanabi, Any>

    val indispensibleClues = scorers.conditionalType(HanabiClue::class) {
        action.game.players[action.parameter.player].cards.cards.filter {
            it.matches(action.parameter) && HanabiProbabilities.indispensible(model).invoke(it)
        }.isNotEmpty().let { if (it) 1.0 else null }
    } as Scorer<Hanabi, Any>

    val probabilityProvider: ScorerAnalyzeProvider<Hanabi, HanabiHandProbabilities> = {ctx ->
        HanabiProbabilities.calculateProbabilities(ctx.model, ctx.playerIndex).also { probs ->
            probs.hand.forEach { cardProbs -> println(cardProbs) }
        }
    }

    val playProbability = scorers.conditional { action.actionType == HanabiGame.play.name }.multiply(scorers.simple {
        val probabilities = require(probabilityProvider)!!
        val cardIndex = this.action.parameter as Int
        probabilities.hand[cardIndex].playable
    })

    val discardProbability = scorers.conditional { action.actionType == HanabiGame.discard.name }.multiply(scorers.simple {
        val probabilities = require(probabilityProvider)!!
        val cardIndex = this.action.parameter as Int
        probabilities.hand[cardIndex].discardable
    })

    // val scorers = Scorers.game(Hanabi::class)
    // val sc = scorers.scorer { action, params -> ... }
    // val sc = scorers.conditional { action -> 0 or continue }.map { action.parameter as HanabiClue? }

    // If I think card is indispensible, give clue.
    // If I think card can be played, give clue (unless someone else also has this card with a clue about playability?)

    // ClueHelpfulness --> Analyze a player's cards, based on what you know yourself, and determine the sum of changes in
    //                     playable + indispensible.

    // Need to analyze all probabilities once, for playProbability + discardProbability.

//    scorers.conditional { action.actionType == HanabiGame.giveClue.name }.multiply(2).multiply { 2 }.plus {  }
// scorers.simple { require(provider) }

    /*
    val playProbability: FScorer<GameImpl<Hanabi>, Actionable<Hanabi, Any>> = SimpleScorer { action, params ->

        val clue = action.parameter as HanabiClue? ?: return@SimpleScorer 0.0

        1.0
    }
    val discardProbability: FScorer<GameImpl<Hanabi>, Actionable<Hanabi, Any>> = SimpleScorer { action, params ->

        val clue = action.parameter as HanabiClue? ?: return@SimpleScorer 0.0

        1.0
    }*/

}
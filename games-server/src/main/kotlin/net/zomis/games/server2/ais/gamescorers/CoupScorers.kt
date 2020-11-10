package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.CoupActionType
import net.zomis.games.impl.CoupRuleBased

object CoupScorers {

    val scorers = GamesImpl.game(CoupRuleBased.game).scorers()

    val hasCharacter = scorers.actionConditional(CoupRuleBased.perform) {
        val claim = action.parameter.action.claim
        claim == null || action.parameter.player.influence.cards.contains(claim)
    }
    val hasCharacterCounter = scorers.actionConditional(CoupRuleBased.counter) {
        val claim = action.parameter
        action.game.players[action.playerIndex].influence.cards.contains(claim)
    }
    val trust = scorers.action(CoupRuleBased.approve) { 1.0 }
    val exchange = scorers.actionConditional(CoupRuleBased.perform) { action.parameter.action == CoupActionType.EXCHANGE }
    val counteract = scorers.action(CoupRuleBased.counter) { 1.0 }
    val challenge = scorers.action(CoupRuleBased.challenge) { 1.0 }

    private val aiTruth = scorers.ai("#AI_Truth", hasCharacter, hasCharacterCounter)
    private val aiTrust = scorers.ai("#AI_Trust", hasCharacter, hasCharacterCounter, trust, exchange.weight(0.1))
    private val aiGood = scorers.ai("#AI_Good", hasCharacter, hasCharacterCounter, trust, exchange.weight(0.1), counteract.weight(-1), challenge.weight(-1))
    private val aiNegative = scorers.ai("#AI_Trump", hasCharacter.weight(-1),
        hasCharacterCounter.weight(-1), trust.weight(-1), counteract.weight(1))

    fun ais() = listOf(aiTrust, aiTruth, aiGood, aiNegative)

}

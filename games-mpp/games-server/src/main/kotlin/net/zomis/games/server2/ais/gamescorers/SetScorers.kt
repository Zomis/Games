package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.SetGame
import net.zomis.games.scorers.ScorerController

object SetScorers {

    val scorers = GamesImpl.game(SetGame.game).scorers()
    fun ais(): List<ScorerController<out Any>> = listOf(setFinder)

    val isSetScorer = scorers.actionConditional(SetGame.callSet) {
        val cards = model.stringsToCards(action.parameter.set)
        val setResult = model.setCardsResult(cards)
        setResult.validSet
    }

    val setFinder = scorers.ai("#AI_SetFinder", isSetScorer)

}
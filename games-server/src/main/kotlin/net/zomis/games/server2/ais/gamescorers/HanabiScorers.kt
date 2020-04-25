package net.zomis.games.server2.ais.gamescorers

import net.zomis.aiscores.FScorer
import net.zomis.aiscores.scorers.SimpleScorer
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.impl.Hanabi
import net.zomis.games.impl.HanabiGame

object HanabiScorers {

    val hanabiCluegiver: FScorer<GameImpl<Hanabi>, Actionable<Hanabi, Any>> = SimpleScorer { action, params ->
        if (action.actionType != HanabiGame.giveClue.name) return@SimpleScorer 0.0
        1.0
    }
    val hanabiCheatPlay: FScorer<GameImpl<Hanabi>, Actionable<Hanabi, Any>> = SimpleScorer { action, params ->
        if (action.actionType != HanabiGame.play.name) return@SimpleScorer 0.0
        val card = action.game.current.cards[action.parameter as Int].card
        if (action.game.playAreaFor(card) != null) 1.0 else -1.0
    }

    val hanabiCheatDiscard: FScorer<GameImpl<Hanabi>, Actionable<Hanabi, Any>> = SimpleScorer { action, params ->
        if (action.actionType != HanabiGame.discard.name) return@SimpleScorer 0.0
        val card = action.game.current.cards[action.parameter as Int].card.known(true)
        if (action.game.board.any { it.toList().any { c -> c.known(true) == card } }) 1.0 else -1.0
    }

}
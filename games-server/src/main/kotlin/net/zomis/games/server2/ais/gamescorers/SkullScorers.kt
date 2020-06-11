package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.SkullCard
import net.zomis.games.impl.SkullGame
import net.zomis.games.impl.SkullGameModel
import net.zomis.games.impl.SkullPlayer
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.ScorerFactory

object SkullScorers {

    val scorers = ScorerFactory<SkullGameModel>()

    fun ais(): List<ScorerAIFactory<SkullGameModel>> {
        return listOf(
            ScorerAIFactory("Skull", "#AI_Reasonable",
                play, betMinimal, pass, discardFirstTwoFlowers
            ),
            ScorerAIFactory("Skull", "#AI_Self_Destruct", playSkull, betHigh.weight(10), discardFlower),
            ScorerAIFactory("Skull", "#AI_Play_Random_Pass", play.weight(0.1), pass, betLow),
            ScorerAIFactory("Skull", "#AI_Flower_Pass", playFlower.weight(20), pass.weight(10), betLow),
            ScorerAIFactory("Skull", "#AI_Keep_Playing", play.weight(10), pass.weight(0.1), betLow)
        )
    }

    val playFlower = scorers.conditional { action.actionType == SkullGame.play.name && action.parameter == SkullCard.FLOWER }
    val playSkull = scorers.conditional { action.actionType == SkullGame.play.name && action.parameter == SkullCard.SKULL }
    val betHigh = scorers.simple { if (action.actionType == SkullGame.bet.name) 1.1 + (action.parameter as Int).toDouble() / 100 else 0.0 }
    val betLow = scorers.simple { if (action.actionType == SkullGame.bet.name) 1.1 + -(action.parameter as Int).toDouble() / 100 else 0.0 }
    val betMinimal = scorers.conditional { action.actionType == SkullGame.bet.name && action.parameter == action.game.currentBet() + 1 }
    val play = scorers.conditional { action.actionType == SkullGame.play.name }
    val pass = scorers.conditional { action.actionType == SkullGame.pass.name }
    val discardFlower = scorers.conditional { action.actionType == SkullGame.discard.name && action.parameter == SkullCard.FLOWER }
    val discardFirstTwoFlowers = scorers.conditional {
        action.actionType == SkullGame.discard.name && action.parameter == SkullCard.FLOWER && action.game.currentPlayer.hand.size > 2
    }
    val choose = scorers.simple { if (action.actionType == SkullGame.choose.name) (action.parameter as SkullPlayer).index.toDouble() else 0.0 }

}

package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.impl.SkullCard
import net.zomis.games.impl.SkullGame
import net.zomis.games.impl.SkullGameModel
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.server2.ais.scorers.ScorerFactory

object SkullScorers {

    val scorers = ScorerFactory<SkullGameModel>()

    fun ais(): List<ScorerAIFactory<SkullGameModel>> {
        return listOf(
            ScorerAIFactory("Skull", "#AI_Reasonable",
                play, betMinimal, pass, discardFirstTwoFlowers, chooseAny
            ),
            ScorerAIFactory("Skull", "#AI_Self_Destruct", playSkull, betHigh.weight(10), discardFlower),
            ScorerAIFactory("Skull", "#AI_Play_Random_Pass", play.weight(0.1), pass, betLow),
            ScorerAIFactory("Skull", "#AI_Flower_Pass", playFlower.weight(20), pass.weight(10), betLow),
            ScorerAIFactory("Skull", "#AI_Keep_Playing", play.weight(10), pass.weight(0.1), betLow)
        )
    }

    val playFlower = scorers.actionConditional(SkullGame.play) { action.parameter == SkullCard.FLOWER }
    val playSkull = scorers.actionConditional(SkullGame.play) { action.parameter == SkullCard.SKULL }
    val betHigh = scorers.action(SkullGame.bet) { 1.1 + (action.parameter).toDouble() / 100 }
    val betLow = scorers.action(SkullGame.bet) { 1.1 + -(action.parameter).toDouble() / 100 }
    val betMinimal = scorers.actionConditional(SkullGame.bet) { action.parameter == action.game.currentBet() + 1 }
    val play = scorers.isAction(SkullGame.play)
    val chooseAny = scorers.isAction(SkullGame.choose)
    val pass = scorers.isAction(SkullGame.pass)
    val discardFlower = scorers.actionConditional(SkullGame.discard) { action.parameter == SkullCard.FLOWER }
    val discardFirstTwoFlowers = scorers.actionConditional(SkullGame.discard) { action.parameter == SkullCard.FLOWER && action.game.currentPlayer.hand.size > 2 }
    val choose = scorers.action(SkullGame.choose) { action.parameter.index.toDouble() }

}

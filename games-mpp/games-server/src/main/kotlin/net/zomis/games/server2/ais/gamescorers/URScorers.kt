package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.DslUR
import net.zomis.games.scorers.ScorerAnalyzeProvider
import net.zomis.games.scorers.ScorerController
import net.zomis.games.ur.RoyalGameOfUr
import java.util.function.IntUnaryOperator

object URScorers {

    val scorers = GamesImpl.game(DslUR.gameUR).scorers()

    fun ais() = listOf(
            scorers.ai("#AI_KFE521S3",
                knockout.weight(5), gotoFlower.weight(2), gotoSafety.weight(0.1),
                leaveSafety.weight(-0.1), riskOfBeingTakenThere.weight(-0.1), exit
            ),
            monteCarloSimulatingAI(),
            scorers.ai("#AI_Horrible",
                knockout.weight(-5), gotoFlower.weight(-2), riskOfBeingTakenThere.weight(0.1), exit.weight(-1)
            ),
            scorers.ai("#AI_KnockoutAndFlower",
                knockout.weight(5), gotoFlower.weight(2)
            )
/*            scorers.ai("#AI_MonteCarlo",
                monteCarloScorer(monteCarloProvider(1000, monteCarloSimulatingAI()))
            )*/
        )
/*
    fun monteCarloProvider(fights: Int, simulatingAI: ScorerController<RoyalGameOfUr>) = scorers.provider { ctx ->
        println("Invoking MonteCarloProvider for $fights fights")
        val monteCarloAI = URScorerMonteCarlo(fights, simulatingAI)
        monteCarloAI.positionToMove(ctx.model)
    }
*/
    fun monteCarloScorer(provider: ScorerAnalyzeProvider<RoyalGameOfUr, Int>) = scorers.actionConditional(DslUR.move) {
        val best = require(provider)!!
        best == action.parameter
    }

    fun monteCarloSimulatingAI() = scorers.ai("#AI_KFE521T",
        knockout.weight(5), gotoFlower.weight(2), riskOfBeingTakenThere.weight(-0.1), exit
    )


    val knockout = scorers.actionConditional(DslUR.move) {
        val next = action.parameter + model.roll
        model.canKnockout(next) && model.playerOccupies(model.opponentPlayer, next)
    }

    val position = scorers.action(DslUR.move) {
        action.parameter / RoyalGameOfUr.EXIT.toDouble()
    }

    val leaveFlower = scorers.actionConditional(DslUR.move) { model.isFlower(action.parameter) }
    val gotoSafety = scorers.actionConditional(DslUR.move) {
        val next = action.parameter + model.roll
        next > 12 // TODO: Add condition on current <= 12
    }
    val leaveSafety = scorers.actionConditional(DslUR.move) {
        val next = action.parameter + model.roll
        action.parameter <= 4 && next > 4
    }
    val riskOfBeingTakenHere = scorers.action(DslUR.move) {
        val positionToTakePossible = IntUnaryOperator { roll: Int ->
            if (model.canKnockout(action.parameter) && model.playerOccupies(model.opponentPlayer, action.parameter - roll)) 1 else 0
        }
        val take1 = positionToTakePossible.applyAsInt(1) * 4.0 / 16.0
        val take2 = positionToTakePossible.applyAsInt(2) * 6.0 / 16.0
        val take3 = positionToTakePossible.applyAsInt(3) * 4.0 / 16.0
        val take4 = positionToTakePossible.applyAsInt(4) * 1.0 / 16.0
        take1 + take2 + take3 + take4
    }
    val riskOfBeingTakenThere = scorers.action(DslUR.move) {
        val pieces = model.piecesCopy
        val index = pieces[model.currentPlayer].indexOf(action.parameter)
        pieces[model.currentPlayer][index] = action.parameter + model.roll

        val copy = RoyalGameOfUr(model.opponentPlayer, -1, pieces)

        /*
         * Create a copy of board and analyze risk of being taken.
         * Remember that 1/16, 4/16, 6/16, 4/16, 1/16
         */
        val take1 = canTakeWithRoll(copy, 1) * 4.0 / 16.0;
        val take2 = canTakeWithRoll(copy, 2) * 6.0 / 16.0;
        val take3 = canTakeWithRoll(copy, 3) * 4.0 / 16.0;
        val take4 = canTakeWithRoll(copy, 4) * 1.0 / 16.0;
        take1 + take2 + take3 + take4
    }
    private fun canTakeWithRoll(ur: RoyalGameOfUr, roll: Int): Double {
        val pieces = ur.piecesCopy
        val cp = ur.currentPlayer
        val opponent = (cp + 1) % pieces.size
        for (value in pieces[cp]) {
            val next = value + roll
            if (ur.canKnockout(next) && ur.playerOccupies(opponent, next)) {
                return 1.0
            }
        }
        return 0.0
    }

    val exit = scorers.actionConditional(DslUR.move) {
        val next = action.parameter + model.roll
        next == RoyalGameOfUr.EXIT
    }
    val gotoFlower = scorers.actionConditional(DslUR.move) {
        val next = action.parameter + model.roll
        model.isFlower(next)
    }

}
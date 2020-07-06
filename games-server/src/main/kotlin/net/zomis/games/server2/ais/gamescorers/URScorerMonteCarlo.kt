package net.zomis.games.server2.ais.gamescorers

import klog.KLoggers
import net.zomis.fight.ext.FightCollectors
import net.zomis.fight.ext.WinResult
import net.zomis.games.dsl.Action
import net.zomis.games.dsl.Actionable
import net.zomis.games.server2.ais.ScorerAIFactory
import net.zomis.games.ur.RoyalGameOfUr
import java.util.function.ToIntFunction
import java.util.stream.IntStream

class URScorerMonteCarlo(private val fights: Int, private val ai: ScorerAIFactory<RoyalGameOfUr>) : ToIntFunction<RoyalGameOfUr> {
    val logger = KLoggers.logger(this)
    fun positionToMove(game: RoyalGameOfUr): Int {
        val possibleActions = getPossibleActions(game)
        if (possibleActions.size == 1) {
            return possibleActions[0]
        }
        var best = 0.0
        var bestAction = -1
        val me = game.currentPlayer
        for (action in possibleActions) {
            val copy = game.copy()
            copy.move(game.currentPlayer, action, game.roll)
            val expectedWin = fight(copy, me)
            logger.info { "Action $action in state $game has $expectedWin" }
            if (expectedWin > best) {
                bestAction = action
                best = expectedWin
            }
        }
        val aiResult = askAI(game)
        if (aiResult != bestAction) {
            logger.info { "Monte Carlo returned different result than its simulation AI in state $game." +
                " AI $aiResult - Monte Carlo $bestAction" }
        }
        return bestAction
    }

    private fun fight(game: RoyalGameOfUr, me: Int): Double {
        val collector = FightCollectors.stats()
        return IntStream.range(0, fights)
            .parallel()
            .mapToObj { singleFight(game.copy(), me, it) }
            .collect(collector)
            .percentage
    }

    private fun singleFight(game: RoyalGameOfUr, me: Int, i: Int): WinResult {
        while (!game.isFinished) {
            while (game.isRollTime()) {
                game.doRoll(game.randomRoll())
            }
            val movePosition = askAI(game)
            val allowed = game.move(game.currentPlayer, movePosition, game.roll)
            check(allowed) { "Unexpected move: " + game.toCompactString() + ": " + movePosition }
        }
        return WinResult.resultFor(game.winner, me, -1)
    }

    private fun askAI(game: RoyalGameOfUr): Int {
        val possibleActions = game.piecesCopy[game.currentPlayer].distinct().filter {
            game.canMove(game.currentPlayer, it, game.roll)
        }.map {
            Action(game, game.currentPlayer, "move", it) as Actionable<RoyalGameOfUr, Any>
        }
        val scores = this.ai.scoreSelected(game, game.currentPlayer, possibleActions)
        return scores.maxBy { it.second!! }!!.first.action.parameter as Int
    }

    fun getPossibleActions(game: RoyalGameOfUr): IntArray {
        return IntStream.range(0, 15).filter { i: Int -> game.canMove(game.currentPlayer, i, game.roll) }.toArray()
    }

    override fun applyAsInt(game: RoyalGameOfUr): Int {
        return positionToMove(game)
    }

}

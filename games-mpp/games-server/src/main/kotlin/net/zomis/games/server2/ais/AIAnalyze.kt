package net.zomis.games.server2.ais

import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.Game
import net.zomis.games.scorers.ScorerController

data class AIAnalyzeResult(val scores: List<ActionAnalyze>?, val heuristic: Double?)
data class ActionAnalyze(val moveType: String, val parameter: Any, val score: Double)

class AIAnalyze {
    fun scoring(game: Game<Any>, scoring: ScorerController<Any>, playerIndex: Int): AIAnalyzeResult? {
        val scores = scoring.score(GameControllerContext(game, playerIndex))
        val scoreResults = scores.scores.map {
            ActionAnalyze(it.action.actionType, it.action.parameter, it.score ?: 0.0)
        }
        return AIAnalyzeResult(scoreResults, null)
    }

    fun alphaBeta(game: Game<Any>, alphaBetaConfig: AIAlphaBetaConfig<Any>, playerIndex: Int): AIAnalyzeResult? {
        val options = alphaBetaConfig.evaluateActions(game, playerIndex).map {
            ActionAnalyze(it.first.actionType, it.first.parameter, it.second)
        }
        val heuristic = alphaBetaConfig.evaluateState(game, playerIndex)

        return AIAnalyzeResult(options, heuristic)
    }

}

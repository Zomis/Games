package net.zomis.games.server2.ais

import net.zomis.games.dsl.impl.GameImpl

data class AIAnalyzeResult(val scores: List<ActionAnalyze>?, val heuristic: Double?)
data class ActionAnalyze(val moveType: String, val parameter: Any, val score: Double)

class AIAnalyze {
    fun scoring(game: GameImpl<Any>, scoring: ScorerAIFactory<Any>, playerIndex: Int): AIAnalyzeResult? {
        val scorer = AIFactoryScoring().scorer(scoring.config.build(), playerIndex)
        val scores = scorer.analyzeAndScore(game)
        val scoreResults = scores.scores.toList().map { ActionAnalyze(it.first.actionType, it.first.parameter, it.second.score) }
        return AIAnalyzeResult(scoreResults, null)
    }

    fun alphaBeta(game: GameImpl<Any>, alphaBetaConfig: AIAlphaBetaConfig<Any>, playerIndex: Int): AIAnalyzeResult? {
        val options = alphaBetaConfig.evaluateActions(game, playerIndex).map {
            ActionAnalyze(it.first.actionType, it.first.parameter, it.second)
        }
        val heuristic = alphaBetaConfig.evaluateState(game, playerIndex)

        return AIAnalyzeResult(options, heuristic)
    }

}

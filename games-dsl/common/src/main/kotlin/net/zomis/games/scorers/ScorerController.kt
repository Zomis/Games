package net.zomis.games.scorers

import net.zomis.bestOf
import net.zomis.games.ais.noAvailableActions
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameControllerScope

class ScorerResult<T: Any>(context: ScorerContext<T>, val score: Double?) {
    val action: Actionable<T, Any> = context.action
}

class ScorerResults<T: Any>(val providers: Map<ScorerAnalyzeProvider<T, Any>, Any?>, val scores: List<ScorerResult<T>>)

class ScorerController<T : Any>(val gameType: String, val name: String, vararg configArr: Scorer<T, Any>) {
    val config = configArr.toList()

    fun availableActions(scope: GameControllerScope<T>): List<Actionable<T, Any>> {
        return scope.game.actions.types().flatMap {
            it.availableActions(scope.playerIndex, null)
        }.filter {
            scope.game.actions.type(it.actionType)!!.isAllowed(it)
        }
    }

    fun scoreSelected(
            model: T, playerIndex: Int,
            availableActions: List<Actionable<T, Any>>
    ): ScorerResults<T> {
        val providers = mutableMapOf<ScorerAnalyzeProvider<T, Any>, Any?>()
        val scoreContext = availableActions.map {action ->
            ScorerContext(model, playerIndex, action, providers)
        }
        val scores = scoreContext.map {scorerContext ->
            val scored = config.mapNotNull { it.score(scorerContext) }
            val sum = if (scored.isEmpty()) null else scored.sum()
            ScorerResult(scorerContext, sum)
        }.filter { it.score != null }
        return ScorerResults(providers.toMap(), scores)
    }

    fun score(scope: GameControllerScope<T>): ScorerResults<T> {
        val availableActions = this.availableActions(scope)
        return scoreSelected(scope.model, scope.playerIndex, availableActions)
    }

    fun gameAI(): GameAI<T> = GameAI(name) {
        queryable { scope ->
            val scoreResult = this@ScorerController.score(scope)
            mapOf(
                "providers" to scoreResult.providers,
                "scores" to scoreResult.scores.map {
                    mapOf("actionType" to it.action.actionType, "action" to it.action.parameter, "score" to it.score)
                }.sortedBy { it["score"] as Double }
            )
        }
        action {
            val context = GameControllerContext(game, playerIndex)
            if (config.isEmpty()) {
                throw IllegalArgumentException("All controllers must have at least one scorer (even if it just returns zero for everything)")
            }
            if (!noAvailableActions(game, playerIndex)) {
                val scores = score(context)
                val bestScores = scores.scores.bestOf { it.score!! }
                val move = if (bestScores.isNotEmpty()) bestScores.random().action else availableActions(context).random()
                move
            } else null
        }
    }

}
package net.zomis.games.server2.ais

import net.zomis.bestBy
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerAnalyzeProvider
import net.zomis.games.server2.ais.scorers.ScorerContext
import net.zomis.games.server2.games.PlayerGameMoveRequest

class AIFactoryScoring {

    fun <T: Any> createController(config: List<Scorer<T, Any>>): GameController<T> = {scope ->
        if (config.isEmpty()) {
            throw IllegalArgumentException("All controllers must have at least one scorer (even if it just returns zero for everything)")
        }
        if (!noAvailableActions(scope.game, scope.playerIndex)) {
            val providers = mutableMapOf<ScorerAnalyzeProvider<T, Any>, Any?>()
            val availableActions = scope.game.actions.types().flatMap {
                it.availableActions(scope.playerIndex)
            }.filter {
                scope.game.actions.type(it.actionType)!!.isAllowed(it)
            }
            val scoreContext = availableActions.map {action ->
                ScorerContext(scope.game.model, scope.playerIndex, action, providers)
            }
            val scores = scoreContext.map {scorerContext ->
                val scored = config.mapNotNull { it.score(scorerContext) }
                val sum = if (scored.isEmpty()) null else scored.sum()
                scorerContext to sum
            }.filter { it.second != null }

            val bestScores = scores.bestBy { it.second!! }
            val move = bestScores.random()
            move.first.action
        } else null
    }

    fun <T: Any> createAI(events: EventSystem, gameType: String, name: String, ai: List<Scorer<T, Any>>) {
        val controller = createController(ai)

        ServerAI(gameType, name) { game, index ->
            val obj = game.obj as GameImpl<T>
            val controllerContext = GameControllerContext(obj, index)
            val action = controller(controllerContext)
            if (action != null) listOf(PlayerGameMoveRequest(game, index, action.actionType, action.parameter))
            else emptyList()
        }.register(events)
    }

}
package net.zomis.games.server2.ais

import net.zomis.bestBy
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerScope
import net.zomis.games.server2.ais.gamescorers.*
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerAnalyzeProvider
import net.zomis.games.server2.ais.scorers.ScorerContext
import net.zomis.games.server2.games.GameTypeRegisterEvent

class ScorerAIFactory<T: Any>(val gameType: String, val name: String, vararg configArr: Scorer<T, Any>) {
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
    ): List<Pair<ScorerContext<T>, Double?>> {
        val providers = mutableMapOf<ScorerAnalyzeProvider<T, Any>, Any?>()
        val scoreContext = availableActions.map {action ->
            ScorerContext(model, playerIndex, action, providers)
        }
        val scores = scoreContext.map {scorerContext ->
            val scored = config.mapNotNull { it.score(scorerContext) }
            val sum = if (scored.isEmpty()) null else scored.sum()
            scorerContext to sum
        }.filter { it.second != null }
        return scores
    }

    fun score(scope: GameControllerScope<T>): List<Pair<ScorerContext<T>, Double?>> {
        val availableActions = this.availableActions(scope)
        return scoreSelected(scope.model, scope.playerIndex, availableActions)
    }

    fun createController(): GameController<T> = {scope ->
        if (config.isEmpty()) {
            throw IllegalArgumentException("All controllers must have at least one scorer (even if it just returns zero for everything)")
        }
        if (!noAvailableActions(scope.game, scope.playerIndex)) {
            val scores = this.score(scope)
            val bestScores = scores.bestBy { it.second!! }
            val move = if (bestScores.isNotEmpty()) bestScores.random().first.action else this.availableActions(scope).random()
            move
        } else null
    }

}

class ServerScoringAIs(private val aiRepository: AIRepository) {
    fun setup(events: EventSystem) {
        val factories = listOf(
            LiarsDiceScorer.ais(),
            ArtaxScorers.ais(),
            SplendorScorers.ais(), HanabiScorers.ais(), URScorers.ais(),
            DungeonMayhemScorers.ais(), SkullScorers.ais()
        ).flatten()
        factories.groupBy { it.gameType }.forEach { entry ->
            events.listen("Register scoring AIs in ${entry.key}", GameTypeRegisterEvent::class, { it.gameType == entry.key }) {
                entry.value.forEach {factory ->
                    aiRepository.createScoringAI(events, factory)
                }
            }
        }
    }

}
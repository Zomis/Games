package net.zomis.games.server2.ais

import net.zomis.bestBy
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerScope
import net.zomis.games.impl.ArtaxGame
import net.zomis.games.impl.TTArtax
import net.zomis.games.server2.ais.gamescorers.*
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerAnalyzeProvider
import net.zomis.games.server2.ais.scorers.ScorerContext
import net.zomis.games.server2.ais.scorers.ScorerFactory
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.tttultimate.Direction8

class ScorerAIFactory<T: Any>(val gameType: String, val name: String, vararg configArr: Scorer<T, Any>) {
    val config = configArr.toList()

    fun availableActions(scope: GameControllerScope<T>): List<Actionable<T, Any>> {
        return scope.game.actions.types().flatMap {
            it.availableActions(scope.playerIndex)
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
        val artaxScorers = ScorerFactory<TTArtax>()
        val artaxTake = artaxScorers.action(ArtaxGame.moveAction) {
            val pm = action.parameter
            val board = model.board
            val neighbors = Direction8.values()
                .map { board.point(pm.destination.x + it.deltaX, pm.destination.y + it.deltaY) }
                .mapNotNull { it.rangeCheck(board) }
                .count { it.value != action.playerIndex && it.value != null }
            neighbors.toDouble()
        }
        val copying = artaxScorers.action(ArtaxGame.moveAction) {
            action.parameter.let {
                -it.destination.minus(it.source).abs().distance()
            }
        }
        val factories = listOf(
            ScorerAIFactory("Artax", "#AI_Aggressive_Simple", artaxTake),
            ScorerAIFactory("Artax", "#AI_Aggressive_Defensive", copying, artaxTake.weight(0.35)),
            ScorerAIFactory("Artax", "#AI_Defensive", copying.weight(2), artaxTake.weight(0.35))
        ).asSequence()
            .plus(SplendorScorers.ais())
            .plus(HanabiScorers.ais())
            .plus(URScorers.ais())
            .plus(DungeonMayhemScorers.ais())
            .plus(SkullScorers.ais())
            .toList()
        factories.groupBy { it.gameType }.forEach { entry ->
            events.listen("Register scoring AIs in ${entry.key}", GameTypeRegisterEvent::class, { it.gameType == entry.key }) {
                entry.value.forEach {factory ->
                    aiRepository.createScoringAI(events, factory)
                }
            }
        }
    }

}
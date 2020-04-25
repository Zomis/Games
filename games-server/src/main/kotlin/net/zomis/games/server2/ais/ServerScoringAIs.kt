package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.node.IntNode
import net.zomis.aiscores.*
import net.zomis.aiscores.scorers.SimpleScorer
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.PointMove
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.sourcedest.TTArtax
import net.zomis.games.impl.Hanabi
import net.zomis.games.server2.ais.gamescorers.HanabiScorers
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.ur.RoyalGameOfUr
import net.zomis.games.ur.ais.MonteCarloAI
import net.zomis.games.ur.ais.RoyalGameOfUrAIs
import net.zomis.tttultimate.Direction8
import java.util.function.ToIntFunction

typealias ActScorable<T> = Actionable<T, Any>
data class ScorerAIFactory<T: Any>(val gameType: String, val name: String, val config: ScoreConfigFactory<GameImpl<T>, ActScorable<T>>)
fun <T: Any> scf(): ScoreConfigFactory<GameImpl<T>, ActScorable<T>> = ScoreConfigFactory()

class ServerScoringAIs(private val aiRepository: AIRepository) {
    fun setup(events: EventSystem) {
        events.listen("register ServerAIs for Game of UR", GameTypeRegisterEvent::class, { it.gameType == "DSL-UR" }, {
            createURAI(events, "#AI_KFE521S3", RoyalGameOfUrAIs.scf()
                    .withScorer(RoyalGameOfUrAIs.knockout, 5.0)
                    .withScorer(RoyalGameOfUrAIs.gotoFlower, 2.0)
                    .withScorer(RoyalGameOfUrAIs.gotoSafety, 0.1)
                    .withScorer(RoyalGameOfUrAIs.leaveSafety, -0.1)
                    .withScorer(RoyalGameOfUrAIs.riskOfBeingTaken, -0.1)
                    .withScorer(RoyalGameOfUrAIs.exit))
            val ai = createURAI(events, "#AI_KFE521T", RoyalGameOfUrAIs.scf()
                    .withScorer(RoyalGameOfUrAIs.knockout, 5.0)
                    .withScorer(RoyalGameOfUrAIs.gotoFlower, 2.0)
                    .withScorer(RoyalGameOfUrAIs.riskOfBeingTaken, -0.1)
                    .withScorer(RoyalGameOfUrAIs.exit))
            createURAI(events, "#AI_Horrible", RoyalGameOfUrAIs.scf()
                    .withScorer(RoyalGameOfUrAIs.knockout, 5.0)
                    .withScorer(RoyalGameOfUrAIs.gotoFlower, 2.0)
                    .withScorer(RoyalGameOfUrAIs.riskOfBeingTaken, -0.1)
                    .withScorer(RoyalGameOfUrAIs.exit).multiplyAll(-1.0))
            createURAI(events, "#AI_KnockoutAndFlower", RoyalGameOfUrAIs.scf()
                    .withScorer(RoyalGameOfUrAIs.knockout, 5.0)
                    .withScorer(RoyalGameOfUrAIs.gotoFlower, 2.0)
            )
            createURAI(events, "#AI_MonteCarlo", MonteCarloAI(1000, ai))
        })

        val artaxTake: FScorer<GameImpl<TTArtax>, ActScorable<TTArtax>> = SimpleScorer { action, params ->
            val pm = (action.parameter) as PointMove
            val board = params.parameters.model.board
            val neighbors = Direction8.values()
                .map { board.point(pm.destination.x + it.deltaX, pm.destination.y + it.deltaY) }
                .mapNotNull { it.rangeCheck(board) }
                .count { it.value != action.playerIndex && it.value != null }
            neighbors.toDouble()
        }
        val copying = SimpleScorer<GameImpl<TTArtax>, ActScorable<TTArtax>> { action, params ->
            action.parameter.let { it as PointMove }.let {
                -it.destination.minus(it.source).abs().distance()
            }
        }
        val factories = listOf(
            ScorerAIFactory("Hanabi", "#AI_Cheating_PlayAndDiscard", scf<Hanabi>()
                .withScorer(HanabiScorers.hanabiCheatPlay).withScorer(HanabiScorers.hanabiCheatDiscard)
                .withScorer(HanabiScorers.hanabiCluegiver)),
            ScorerAIFactory("Artax", "#AI_Aggressive_Simple", scf<TTArtax>().withScorer(artaxTake)),
            ScorerAIFactory("Artax", "#AI_Aggressive_Defensive", scf<TTArtax>().withScorer(copying).withScorer(artaxTake, 0.35)),
            ScorerAIFactory("Artax", "#AI_Defensive", scf<TTArtax>().withScorer(copying, 2.0).withScorer(artaxTake, 0.35))
        )
        factories.groupBy { it.gameType }.forEach { entry ->
            events.listen("Register scoring AIs in ${entry.key}", GameTypeRegisterEvent::class, { it.gameType == entry.key }) {
                entry.value.forEach {factory ->
                    aiRepository.createScoringAI(events, factory)
                }
            }
        }
    }

    private fun createURAI(events: EventSystem, name: String,
                           ai: ScoreConfigFactory<RoyalGameOfUr, Int>): ToIntFunction<RoyalGameOfUr> {
        return this.createURAI(events, name, RoyalGameOfUrAIs.URScorer(name, ai))
    }

    private fun createURAI(events: EventSystem, name: String, ai: ToIntFunction<RoyalGameOfUr>):
            ToIntFunction<RoyalGameOfUr> {
        ServerAI("DSL-UR", name) { game, index ->
            val obj = game.obj as GameImpl<RoyalGameOfUr>
            val ur = obj.model
            if (index != ur.currentPlayer) {
                return@ServerAI listOf()
            }

            if (ur.isRollTime()) {
                return@ServerAI listOf(PlayerGameMoveRequest(game, index, "roll", -1))
            }

            val move = ai.applyAsInt(ur)
            listOf(PlayerGameMoveRequest(game, index, "move", IntNode(move)))
        }.register(events)
        return ai
    }

}
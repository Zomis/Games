package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.ObjectMapper
import net.zomis.games.ur.ais.RoyalGameOfUrAIs
import net.zomis.games.ur.ais.RoyalGameOfUrAIs.*
import com.fasterxml.jackson.databind.node.IntNode
import net.zomis.aiscores.FieldScoreProducer
import net.zomis.aiscores.ScoreConfigFactory
import net.zomis.aiscores.ScoreParameters
import net.zomis.aiscores.ScoreStrategy
import net.zomis.aiscores.extra.ScoreUtils
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.ur.RoyalGameOfUr
import net.zomis.games.ur.ais.MonteCarloAI
import net.zomis.tttultimate.games.TTController
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.ToIntFunction

class ServerAIs {

    fun register(events: EventSystem, executor: ScheduledExecutorService) {
        events.listen("ServerAIs Delayed move", DelayedAIMoves::class, {true}, {
            executor.schedule({
                it.moves.forEach({
                    events.execute(it)
                })
            }, 1000, TimeUnit.MILLISECONDS)
        })
        events.listen("register ServerAIs for Game of UR", GameTypeRegisterEvent::class, { it.gameType == "UR" }, {
            createURAI(events, "#AI_KFE521S3", RoyalGameOfUrAIs.scf()
                .withScorer(knockout, 5.0)
                .withScorer(gotoFlower, 2.0)
                .withScorer(gotoSafety, 0.1)
                .withScorer(leaveSafety, -0.1)
                .withScorer(riskOfBeingTaken, -0.1)
                .withScorer(exit))
            val ai = createURAI(events, "#AI_KFE521T", scf()
                    .withScorer(knockout, 5.0)
                    .withScorer(gotoFlower, 2.0)
                    .withScorer(riskOfBeingTaken, -0.1)
                    .withScorer(exit))
            createURAI(events, "#AI_Random", RoyalGameOfUrAIs.scf())
            createURAI(events, "#AI_Horrible", scf()
                    .withScorer(knockout, 5.0)
                    .withScorer(gotoFlower, 2.0)
                    .withScorer(riskOfBeingTaken, -0.1)
                    .withScorer(exit).multiplyAll(-1.0))
            createURAI(events, "#AI_KnockoutAndFlower", RoyalGameOfUrAIs.scf()
                .withScorer(knockout, 5.0)
                .withScorer(gotoFlower, 2.0)
            )
            createURAI(events, "#AI_MonteCarlo", MonteCarloAI(1000, ai))

            val ttAllowed: (TTController, Int, Int) -> Boolean = { game, x, y -> game.isAllowedPlay(game.game.getSmallestTile(x, y)!!) }
            XYScorer("Connect4", "#AI_C4_Random", ScoreConfigFactory(), XYScoreStrategy(7, 6, ttAllowed)).create(events)
            XYScorer("UTTT", "#AI_UTTT_Random", ScoreConfigFactory(), XYScoreStrategy(9, 9, ttAllowed)).create(events)
        })
    }

    class XYScoreStrategy<T>(val width: Int, val height: Int, val allowed: (T, Int, Int) -> Boolean) : ScoreStrategy<T, Pair<Int, Int>> {
        override fun canScoreField(p0: ScoreParameters<T>?, p1: Pair<Int, Int>?): Boolean {
            return allowed.invoke(p0!!.parameters, p1!!.first, p1.second)
        }

        override fun getFieldsToScore(p0: T): MutableCollection<Pair<Int, Int>> {
            return (0 until width).flatMap {x ->
                (0 until height).map { y ->
                    x to y
                }
            }.toMutableList()
        }
    }

    class XYScorer<T>(val gameType: String, val name: String, scoreConfig: ScoreConfigFactory<T, Pair<Int, Int>>,
              val scoreStrategy: XYScoreStrategy<T>) {
        val producer = FieldScoreProducer(scoreConfig.build(), scoreStrategy)
        val mapper = ObjectMapper()

        fun positionToMove(game: T): Pair<Int, Int>? {
            val best = ScoreUtils.pickBest(producer, game, Random())
                    ?: return null
            return best.field
        }

        fun create(events: EventSystem) {
            ServerAI(gameType, name, { game, index ->
                val controller = game.obj as T
                val move = positionToMove(controller) ?: return@ServerAI listOf()
                listOf(PlayerGameMoveRequest(game, index, "move",
                    mapper.createObjectNode().put("x", move.first).put("y", move.second)))
            }).register(events)
        }
    }

    private fun createURAI(events: EventSystem, name: String,
           ai: ScoreConfigFactory<RoyalGameOfUr, Int>): ToIntFunction<RoyalGameOfUr> {
        return this.createURAI(events, name, URScorer(name, ai))
    }

    private fun createURAI(events: EventSystem, name: String, ai: ToIntFunction<RoyalGameOfUr>):
        ToIntFunction<RoyalGameOfUr> {
        ServerAI("UR", name, { game, index ->
            val ur = game.obj as RoyalGameOfUr
            if (index != ur.currentPlayer) {
                return@ServerAI listOf()
            }

            if (ur.isRollTime()) {
                return@ServerAI listOf(PlayerGameMoveRequest(game, index, "roll", -1))
            }

            // IntelliJ claims "Cannot access class net.zomis...RoyalGameOfUr" - I disagree, it works just fine.
            val move = ai.applyAsInt(ur)
            listOf(PlayerGameMoveRequest(game, index, "move", IntNode(move)))
        }).register(events)
        return ai
    }

}

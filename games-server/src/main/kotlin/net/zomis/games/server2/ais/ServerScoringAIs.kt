package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.node.IntNode
import net.zomis.aiscores.ScoreConfigFactory
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.ur.RoyalGameOfUr
import net.zomis.games.ur.ais.MonteCarloAI
import net.zomis.games.ur.ais.RoyalGameOfUrAIs
import java.util.function.ToIntFunction

class ServerScoringAIs {
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
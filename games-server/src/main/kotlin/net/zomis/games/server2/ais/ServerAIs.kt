package net.zomis.games.server2.ais

import net.zomis.games.ur.ais.RoyalGameOfUrAIs
import net.zomis.games.ur.ais.RoyalGameOfUrAIs.*
import com.fasterxml.jackson.databind.node.IntNode
import net.zomis.aiscores.ScoreConfigFactory
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.ur.RoyalGameOfUr
import net.zomis.games.ur.ais.MonteCarloAI
import net.zomis.tttultimate.games.TTClassicControllerWithGravity
import java.util.function.ToIntFunction

class ServerAIs {

    fun register(events: EventSystem) {
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

            ServerAI("Connect4", "#AI_C4Random", { game, playerIndex ->
                val controller = game.obj as TTClassicControllerWithGravity
                val possibles = (0 until controller.game.sizeX).flatMap {x ->
                    (0 until controller.game.sizeY).map { y ->
                        x to y
                    }
                }.filter { controller.isAllowedPlay(controller.game.getSub(it.first, it.second)) }
                val chosen = possibles.shuffled().firstOrNull() ?: return@ServerAI listOf()

                listOf(PlayerGameMoveRequest(game, playerIndex, "move", IntNode(chosen.first)))
            }).register(events)
        })
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

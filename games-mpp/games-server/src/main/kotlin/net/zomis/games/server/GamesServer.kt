package net.zomis.games.server

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.server2.db.DBIntegration
import net.zomis.games.server2.games.*

object GamesServer {
    object Replays {
        fun fileRecordReplay(fileName: String): GameListener = TODO()
        fun database(dbIntegration: DBIntegration, serverGame: ServerGame): GameListener = object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                when (step) {
                    is FlowStep.GameSetup<*> -> { dbIntegration.superTable.createGame(serverGame, step.state) }
                    is FlowStep.ActionPerformed<*> -> { dbIntegration.superTable.addMove(serverGame, step) }
                    is FlowStep.GameEnd -> { dbIntegration.superTable.finishGame(serverGame) }
                    is FlowStep.Elimination -> { dbIntegration.superTable.playerEliminated(serverGame, step) }
                }
            }
        }
        fun noReplays(): GameListener = object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            }
        }
    }

}
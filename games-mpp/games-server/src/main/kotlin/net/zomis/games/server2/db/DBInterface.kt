package net.zomis.games.server2.db

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.server2.PlayerDatabaseInfo
import net.zomis.games.server2.games.ServerGame

interface DBInterface {
    fun createGame(game: ServerGame, replayState: Map<String, Any>)
    fun addMove(serverGame: ServerGame, move: FlowStep.ActionPerformed<*>)
    fun playerEliminated(serverGame: ServerGame, event: FlowStep.Elimination)
    fun finishGame(game: ServerGame)
    fun listUnfinished(): Set<DBGameSummary>

    fun loadGame(gameId: String): DBGame?
    fun loadGameIgnoreErrors(gameId: String): DBGame?
    fun gameListener(serverGame: ServerGame): GameListener {
        return object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                when (step) {
                    is FlowStep.GameSetup<*> -> { createGame(serverGame, step.state) }
                    is FlowStep.ActionPerformed<*> -> { addMove(serverGame, step) }
                    is FlowStep.GameEnd -> { finishGame(serverGame) }
                    is FlowStep.Elimination -> { playerEliminated(serverGame, step) }
                    else -> {}
                }
            }
        }
    }

    fun cookieAuth(cookie: String): PlayerDatabaseInfo?

}

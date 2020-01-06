package net.zomis.games.server2.db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameSetupImpl

private val mapper = jacksonObjectMapper()
class DBGame(
        gameSpec: GameSpec<Any>,
        val gameId: String,
        val playersInGame: List<GamesTables.PlayerInGame>,
        val gameType: String,
        val gameState: Int,
        val timeStarted: Long,
        val timeLastAction: Long,
        val moveHistory: List<GamesTables.MoveHistory>) {

    private val gameSetup = GameSetupImpl(gameSpec)
    private val game = gameSetup.createGame(gameSetup.getDefaultConfig())
    val views = mutableListOf(game.view(null))

    init {
        moveHistory.forEach {
            // TODO: Set replay mode in replayState
            val logic = game.actions[it.moveType]
                    ?: throw BadReplayException("Unable to perform $it: No such move type")
            val param = if (it.move == null) Unit
            else mapper.readValue(mapper.writeValueAsString(it.move), logic.parameterClass.java)
            val actionable = logic.createAction(it.playerIndex, param)
            if (!logic.isAllowed(actionable)) {
                throw BadReplayException("Unable to perform $it: Move is not allowed")
            }
            logic.perform(actionable)
            views.add(game.view(null))
        }
        if (!game.isGameOver()) {
            throw BadReplayException("Game is not finished after all moves are made. Last view was ${views.last()}")
        }
    }

}
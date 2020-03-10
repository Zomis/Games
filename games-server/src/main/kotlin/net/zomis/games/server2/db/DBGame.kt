package net.zomis.games.server2.db

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameSetupImpl

enum class GameState(val value: Int) {
    HIDDEN(-1),
    UNFINISHED(0),
    PUBLIC(1),
    ;
}
data class PlayerInGameResults(val result: Double,
   val resultPosition: Int, val resultReason: String, val score: Map<String, Any?>)
data class PlayerInGame(val player: GamesTables.PlayerView?, val playerIndex: Int, val results: PlayerInGameResults?)

private val mapper = jacksonObjectMapper()
data class DBGameSummary(
    @JsonIgnore
    val gameSpec: GameSpec<Any>,
    val gameId: String,
    val playersInGame: List<PlayerInGame>,
    val gameType: String,
    val gameState: Int,
    val timeStarted: Long,
    val timeLastAction: Long
)
class DBGame(@JsonUnwrapped val summary: DBGameSummary, moveHistory: List<GamesTables.MoveHistory>) {

    private val gameSetup = GameSetupImpl(summary.gameSpec)
    @JsonIgnore
    val game = gameSetup.createGame(summary.playersInGame.size, gameSetup.getDefaultConfig())
    val views = mutableListOf(game.view(null))

    init {
        moveHistory.forEachIndexed { index, it ->
            val logic = game.actions[it.moveType]
                    ?: throw BadReplayException("Unable to perform $it: No such move type")
            val param = if (it.move == null) Unit
            else mapper.readValue(mapper.writeValueAsString(it.move), logic.parameterClass.java)
            val actionable = logic.createAction(it.playerIndex, param)
            if (!logic.isAllowed(actionable)) {
                val view = game.view(null)
                throw BadReplayException("Unable to perform $it: Move at index $index is not allowed. View is $view")
            }
            logic.replayAction(actionable, it.state)
            views.add(game.view(null))
        }
    }

}
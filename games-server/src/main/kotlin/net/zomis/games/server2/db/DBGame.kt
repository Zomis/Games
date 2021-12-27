package net.zomis.games.server2.db

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.games.dsl.ActionReplay
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.ReplayData
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.dsl.impl.StateKeeper

enum class GameState(val value: Int) {
    HIDDEN(-1),
    UNFINISHED(0),
    PUBLIC(1),
    ;
}
data class PlayerInGameResults(val result: Double,
   val resultPosition: Int, val resultReason: String, val score: Map<String, Any?>)
data class PlayerInGame(val player: PlayerView?, val playerIndex: Int, val results: PlayerInGameResults?)
class BadReplayException(message: String): Exception(message)

private val mapper = jacksonObjectMapper()
data class DBGameSummary(
    @JsonIgnore
    val gameSpec: GameSpec<Any>,
    val gameConfig: GameConfigs,
    val gameId: String,
    val playersInGame: List<PlayerInGame>,
    val gameType: String,
    val gameState: Int,
    @JsonIgnore
    val startingState: Map<String, Any>?,
    val timeStarted: Long
)
class DBGame(@JsonUnwrapped val summary: DBGameSummary, @JsonIgnore val moveHistory: List<MoveHistory>) {
    private val logger = KLoggers.logger(this)
    private val gameSetup = GameSetupImpl(summary.gameSpec)
    @JsonIgnore
    val stateKeeper = StateKeeper().also { if (summary.startingState != null) it.setState(summary.startingState) }
    @JsonIgnore
    val game = gameSetup.createGameWithState(summary.playersInGame.size, summary.gameConfig, stateKeeper)
    val views = mutableListOf<Map<String, Any?>>()
    val errors = mutableListOf<String>()
    val timeLastAction = moveHistory.map { it.time }.maxByOrNull { it ?: 0 }

    init {
        views.add(game.view(null))
        for (move in moveHistory.withIndex()) {
            if (!performMove(game, move)) break
            views.add(game.view(null))
        }
    }

    private fun performMove(game: Game<Any>, move: IndexedValue<MoveHistory>): Boolean {
        val it = move.value
        val logic = game.actions[it.moveType]
            ?: throw BadReplayException("Unable to perform $it: No such move type")
        val actionable =
            if (it.move == null)
                logic.createAction(it.playerIndex, Unit)
            else {
                val serialized = mapper.readValue(mapper.writeValueAsString(it.move), logic.actionType.serializedType.java)
                logic.createActionFromSerialized(it.playerIndex, serialized)
            }

        if (!logic.isAllowed(actionable)) {
            addError("Unable to perform $it: Move at index ${move.index} is not allowed.")
            return false
        }
        try {
            logic.replayAction(actionable, it.state)
        } catch (e: Exception) {
            logger.error(e) { "Unable to perform move: $move." }
            addError("Unable to perform move: $move. $e")
            return false
        }
        return true
    }

    fun at(position: Int): Game<Any> {
        val stateKeeper = StateKeeper().also { if (summary.startingState != null) it.setState(summary.startingState) }
        val game = gameSetup.createGameWithState(summary.playersInGame.size, summary.gameConfig, stateKeeper)
        moveHistory.slice(0 until position).withIndex().forEach { performMove(game, it) }
        return game
    }

    fun addError(error: String) {
        this.errors.add(error)
    }

    fun hasErrors(): Boolean {
        return this.errors.any()
    }

    fun replayData(): ReplayData {
        return ReplayData(
            summary.gameType, summary.playersInGame.size,
            summary.gameConfig, summary.startingState,
            moveHistory.map { ActionReplay(it.moveType, it.playerIndex, it.move ?: Unit, it.state) }
        )
    }

}
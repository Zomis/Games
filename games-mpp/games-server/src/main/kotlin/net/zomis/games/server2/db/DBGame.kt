package net.zomis.games.server2.db

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.server.GamesServer

enum class GameState(val value: Int) {
    HIDDEN(-1),
    UNFINISHED(0),
    PUBLIC(1),
    ;
}
data class PlayerInGameResults(val result: Double,
   val resultPosition: Int, val resultReason: String, val score: Map<String, Any?>)
data class PlayerInGame(val player: PlayerView?, val playerIndex: Int, val results: PlayerInGameResults?)

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
) {
    fun serialize() = mapOf(
        "gameType" to gameType,
        "config" to gameConfig.toJSON(),
        "gameId" to gameId,
        "playersInGame" to playersInGame,
        "gameState" to gameState,
        "startingState" to startingState,
        "timeStarted" to timeStarted
    )
}
class DBGame(@JsonUnwrapped val summary: DBGameSummary, @JsonIgnore val moveHistory: List<MoveHistory>) {
    private val mapper = jacksonObjectMapper()
    val views = mutableListOf<JsonNode>()
    val errors = mutableListOf<String>()
    val timeLastAction = moveHistory.map { it.time }.maxByOrNull { it ?: 0 }

    suspend fun game(coroutineScope: CoroutineScope): Game<Any> {
        class MyListener(val game: Game<Any>): GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                if (step is FlowStep.ProceedStep) {
                    views.add(mapper.valueToTree(game.view(null)))
                }
                if (step is FlowStep.IllegalAction) {
                    addError("Illegal action: ${step.action}")
                }
            }
        }
        return GamesImpl.game(summary.gameSpec).replay(coroutineScope, replayData(), GamesServer.actionConverter, {
            listOf(MyListener(it))
        }, fork = false).goToEnd().awaitCatchUp().game
    }

    suspend fun at(coroutineScope: CoroutineScope, position: Int): Game<Any> {
        return GameEntryPoint(summary.gameSpec).replay(coroutineScope, replayData(), GamesServer.actionConverter, {
            listOf()
        }, fork = false).gotoPosition(position).awaitCatchUp().game
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
            moveHistory.map { ActionReplay(it.moveType, it.playerIndex, it.move ?: Unit, it.state ?: emptyMap()) }
        )
    }

    fun toJSON(): Map<String, Any?> = mapOf(
        "views" to views,
        "errors" to errors,
        "timeLastAction" to timeLastAction
    ) + summary.serialize()

}
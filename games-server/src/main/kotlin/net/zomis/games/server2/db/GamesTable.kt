package net.zomis.games.server2.db

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.AttributeUpdate
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.model.*
import klog.KLoggers
import net.zomis.common.convertFromDBFormat
import net.zomis.common.convertToDBFormat
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.PlayerId
import net.zomis.games.server2.games.*
import java.math.BigDecimal
import java.time.Instant
import kotlin.system.measureNanoTime

enum class GameState(val value: Int) {
    HIDDEN(-1),
    UNFINISHED(0),
    PUBLIC(1),
    ;
}

class BadReplayException(message: String): Exception(message)

class GamesTables(private val dynamoDB: AmazonDynamoDB) {
    /*
  - GamePlayers
    -- update on game start or end
H   - GameId: UUID
S   - PlayerIndex: Number
S12 - PlayerId: UUID
S2  - Result: Number (1 / 0 / -1 for win/draw/loss)
    - ResultPosition: Number (1, 2, 3, 4... up to 8 or however many players is in game)
    - ResultReason: String (walkover, gameend, surrender...)
    ? RatingBefore: Number
    - Score: Map
*/
    private val gamePlayers = GamePlayersTable(dynamoDB)
    private val games = GamesTable(dynamoDB)

    private class GamePlayersTable(dynamoDB: AmazonDynamoDB) {
        val tableName = "Server2-GamePlayers"
        val gameId = "GameId"
        val playerIndex = "PlayerIndex"
        val playerId = "PlayerId"
        val result = "Result"
        val resultPosition = "ResultPosition"
        val resultReason = "ResultReason"
        val score = "Score"

        val table = MyTable(dynamoDB, tableName).strings(gameId, playerId).numbers(playerIndex, result)
        val primaryIndex = table.primaryIndex(gameId, playerIndex)
        val indexPlayer = table.index(ProjectionType.ALL, listOf(playerId), listOf(result))

        fun addPlayerInGame(game: ServerGame, playerIndex: Int, playerId: PlayerId) {
            val updates = arrayOf(
                AttributeUpdate(this.playerId).put(playerId.toString())
            )
            table.table.updateItem(this.gameId, game.gameId, this.playerIndex, playerIndex, *updates)
        }

        fun eliminatePlayer(game: ServerGame, playerIndex: Int, result: Double, resultPosition: Int, resultReason: String, score: Map<String, Any>) {
            val updates = arrayOf(
                AttributeUpdate(this.result).put(result),
                AttributeUpdate(this.resultPosition).put(resultPosition),
                AttributeUpdate(this.resultReason).put(resultReason),
                AttributeUpdate(this.score).put(score)
            )
            table.table.updateItem(gameId, game.gameId, this.playerIndex, playerIndex, *updates)
        }
    }

    private class GamesTable(dynamoDB: AmazonDynamoDB) {
        private val logger = KLoggers.logger(this)
/*
  - Games
    -- update on game start, move-made and end
H   - GameId: UUID
S1  - GameType: String
S1  - Finished: Boolean
    - StatisticsMode: Number (something might be needed, for whether to show game or not, but not sure if necessary)
S1  - TimeLastAction: Number (timestamp)
    - TimeStarted: Number (timestamp)
    - TimeLimit: Number (timestamp, if no move is done before this then walkover)
    - Options: Map
    - Moves: List
      - Type: String
      - Player: Int - special value for the game itself (such as generating mines in MFE)
      - State: Map - for saving result of randomness (mines placed, UR dice roll result)
      - Move: Map
    - Players: see GamePlayers table
*/
        val tableName = "Server2-Games"
        val gameId = "GameId"
        val gameType = "GameType"
        val finishedState = "GameState"
        val timeLastAction = "TimeLastAction"
        val timeStarted = "TimeStarted"
        val options = "Options"
        val moves = "Moves"
        val table = MyTable(dynamoDB, tableName).strings(gameId, gameType).numbers(finishedState)
        val primaryIndex = table.primaryIndex(gameId)
        val unfinishedIndex = table.index(ProjectionType.ALL, listOf(finishedState), listOf(gameType))

        fun createGame(game: ServerGame, options: Map<String, Any>) {
            val update = UpdateItemSpec().withPrimaryKey(this.gameId, game.gameId)
                .withAttributeUpdate(
                    AttributeUpdate(this.gameType).put(game.gameType.type),
                    AttributeUpdate(this.finishedState).put(GameState.UNFINISHED.value),
                    AttributeUpdate(this.timeStarted).put(Instant.now().epochSecond),
                    AttributeUpdate(this.options).put(options)
                )
            performUpdate(update)
        }

        private fun <String, V> Map<String, V>.plusIf(key: String, value: V?): Map<String, V> {
            return if (value != null) this.plus(key to value) else this
        }
        fun addMove(move: MoveEvent) {
            val serverGame = move.game
            val moveData = mapOf(
                "moveType" to move.moveType,
                "playerIndex" to move.player
            ).let {
                val moveObject = convertToDBFormat(move.move)
                it.plusIf("move", moveObject)
            }.let {
                if (serverGame.obj is GameImpl<*>) {
                    val game = serverGame.obj as GameImpl<*>
                    val lastMoveState = game.actions.lastMoveState()
                    if (lastMoveState.isNotEmpty()) {
                        return@let it.plusIf("state", convertToDBFormat(lastMoveState))
                    }
                }
                it
            }
            val itemUpdate = UpdateItemSpec().withPrimaryKey(this.gameId, serverGame.gameId)
                .withUpdateExpression("SET $moves = list_append(if_not_exists($moves, :emptyList), :move)")
                .withValueMap(ValueMap()
                    .withList(":move", listOf(moveData))
                    .withList(":emptyList", emptyList<Any>())
                )
            performUpdate(itemUpdate)
        }

        fun finishGame(game: ServerGame) {
            val update = UpdateItemSpec().withPrimaryKey(this.gameId, game.gameId)
                .withAttributeUpdate(
                    AttributeUpdate(this.finishedState).put(GameState.PUBLIC.value),
                    AttributeUpdate(this.timeLastAction).put(Instant.now().epochSecond)
                )
            performUpdate(update)
        }

        fun performUpdate(updateItemSpec: UpdateItemSpec) {
            var consumedCapacity: ConsumedCapacity? = null
            val time = measureNanoTime {
                val updateResult = table.table.updateItem(updateItemSpec.withReturnConsumedCapacity(ReturnConsumedCapacity.INDEXES))
                consumedCapacity = updateResult.updateItemResult.consumedCapacity
            }
            logger("Performing $updateItemSpec update took $time with consumed capacity $consumedCapacity")
        }
    }

    private fun dbEnabled(game: ServerGame): Boolean {
        return game.gameMeta.database
    }

    fun register(events: EventSystem): List<CreateTableRequest> {
        events.listen("save game in Database", GameStartedEvent::class, { dbEnabled(it.game) }, {event ->
            val playerIds = event.game.players
                .map { it.playerId ?: throw IllegalStateException("Missing playerId for ${it.name}") }
            games.createGame(event.game, mapOf())
            playerIds.forEachIndexed { index, playerId ->
                gamePlayers.addPlayerInGame(event.game, index, playerId)
            }
        })
        events.listen("save game move in Database", MoveEvent::class, { dbEnabled(it.game) }, {event ->
            games.addMove(event)
        })
        events.listen("finish game in Database", GameEndedEvent::class, { dbEnabled(it.game) }, {event ->
            games.finishGame(event.game)
        })
        events.listen("eliminate player in Database", PlayerEliminatedEvent::class, { dbEnabled(it.game) }, {event ->
            val result = event.winner.result
            gamePlayers.eliminatePlayer(event.game, event.player, result, event.position, "eliminated", mapOf())
        })
        return listOf(gamePlayers.table.createTableRequest(), games.table.createTableRequest())
    }

    data class MoveHistory(val moveType: String, val playerIndex: Int, val move: Any?, val state: Map<String, Any>?)
    data class PlayerView(val playerId: String, val name: String)
    data class PlayerInGame(val player: PlayerView?, val playerIndex: Int, val result: Double,
            val resultPosition: Int, val resultReason: String, val score: Map<String, Any?>)
    fun fetchGame(gameId: String, gameSpecs: Map<String, Any>): DBGame? {
        val playersInGameItems = this.gamePlayers.table.table.query(gamePlayers.gameId, gameId)
        val playersInGame = playersInGameItems.map {
            val playerId = it.getString(gamePlayers.playerId)
            val playerView = AuthTable(dynamoDB).fetchPlayerView(playerId)
            val score = it.getMap<Any?>(gamePlayers.score)
            PlayerInGame(playerView, it.getInt(gamePlayers.playerIndex),
                    it.getDouble(gamePlayers.result), it.getInt(gamePlayers.resultPosition),
                    it.getString(gamePlayers.resultReason), score)
        }
        println("$gameId returned $playersInGame")

        val gameItem = this.games.table.table.getItem(games.gameId, gameId) ?: return null
        val moves = gameItem.getList("moves") ?: gameItem.getList<Map<String, Any>>("Moves")
        val moveHistory = moves.map {
            MoveHistory(
                it["moveType"] as String,
                (it["playerIndex"] as BigDecimal).toInt(),
                convertFromDBFormat(it["move"]),
                convertFromDBFormat(it["state"]) as Map<String, Any>?
            )
        }
        val gameType = gameItem.getString(games.gameType)
        val gameState = gameItem.getInt(games.finishedState)
        val timeStarted = gameItem.getLong(games.timeStarted)
        val timeLastAction = gameItem.getLong(games.timeLastAction)
        val gameSpec = gameSpecs[gameType] ?: throw IllegalArgumentException("No gameSpec found for gameType $gameType")

        return DBGame(gameSpec as GameSpec<Any>, gameId, playersInGame, gameType, gameState, timeStarted, timeLastAction, moveHistory)
//        AuthTable(dynamoDB).userLookup(playerIds)
    }


}

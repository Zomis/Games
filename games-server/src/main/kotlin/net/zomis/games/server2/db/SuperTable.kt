package net.zomis.games.server2.db

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.*
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec
import com.amazonaws.services.dynamodbv2.model.*
import klog.KLoggers
import net.zomis.common.convertFromDBFormat
import net.zomis.common.convertToDBFormat
import net.zomis.core.events.EventSystem
import net.zomis.core.events.ListenerPriority
import net.zomis.games.Features
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.*
import net.zomis.games.server2.ais.ServerAIProvider
import net.zomis.games.server2.games.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

data class UnfinishedGames(val unfinishedGames: MutableSet<DBGameSummary>)
data class MoveHistory(val moveType: String, val playerIndex: Int, val move: Any?, val state: Map<String, Any>?, val time: Long? = null)
data class PlayerView(val playerId: String, val name: String)

class SuperTable(private val dynamoDB: AmazonDynamoDB) {

    private val logger = KLoggers.logger(this)

    private fun dbEnabled(game: ServerGame): Boolean {
        return game.gameMeta.database
    }

    fun setup(features: Features, events: EventSystem): List<CreateTableRequest> {
        events.listen("Auth", ClientLoginEvent::class, {true}, {
            this.authenticate(it)
        })
        events.listen("Load unfinished games", StartupEvent::class, {true}, {
            features.addData(UnfinishedGames(this.listUnfinished().toMutableSet()))
        })
        events.listen("save game in Database", ListenerPriority.LATER, GameStartedEvent::class, { dbEnabled(it.game) }, {
            event -> this.createGame(event.game)
        })
        events.listen("save game move in Database", MoveEvent::class, { dbEnabled(it.game) }, {event ->
            this.addMove(event)
        })
        events.listen("finish game in Database", GameEndedEvent::class, { dbEnabled(it.game) }, { event ->
            this.finishGame(event.game)
        })
        events.listen("eliminate player in Database", PlayerEliminatedEvent::class, { dbEnabled(it.game) }, {event ->
            this.playerEliminated(event)
        })
        return listOf(this.table).map { it.createTableRequest() }
    }

    val tableName = "Server2"
    val pk = "PK"
    val sk = "SK"
    val data = "Data"
    val table = MyTable(dynamoDB, tableName).strings(pk, sk).numbers(data)
    val primaryIndex = table.primaryIndex(pk, sk)
    val gsi = table.index(ProjectionType.KEYS_ONLY, listOf(sk), listOf(data))

    private val SK_UNFINISHED = "unfinished"
    private val SK_PUSH_TO_STATS = "push-to-stats"

    enum class Prefix {
        GAME,
        TAG,
        GAME_TYPE,
        SUMMARY,
        ZMOVE,
        PLAYER, SESSION, OAUTH, ALIAS,
        ;

        val prefix = this.name.toLowerCase() + ":"
        fun extract(fieldValue: String): String {
            if (!fieldValue.startsWith(prefix)) {
                throw IllegalArgumentException("$fieldValue does not begin with $prefix")
            }
            return fieldValue.substringAfter(prefix)
        }
        fun sk(value: String) = prefix + value
        fun rangeKeyCondition(): RangeKeyCondition = RangeKeyCondition("SK").beginsWith(this.prefix)
    }

    enum class Fields(val fieldName: String) {
        PLAYER_NAME("PlayerName"),
        PLAYER_PREFIX("Player"),
        GAME_TYPE("GameType"),
        GAME_PLAYERS("GamePlayers"),
        GAME_TIME_LAST("GameTime"),
        GAME_TIME_STARTED("GameTimeStarted"),
        GAME_OPTIONS("Options"),
        GAME_HIDDEN("Hidden"),

        MOVE_PLAYER_INDEX("PlayerIndex"),
        MOVE_TIME("Time"),
        MOVE_TYPE("MoveType"),
        MOVE("Move"),
        MOVE_STATE("MoveState")
        ;
    }

    fun createGame(game: ServerGame) {
        val pkValue = Prefix.GAME.sk(game.gameId)

        // Don't use data here because PK and SK is the same, this doesn't need to be in GSI-1
        val state: Any? = gameRandomnessState(game)
        var updates = listOf(
            AttributeUpdate(Fields.GAME_TYPE.fieldName).put(game.gameType.type),
            AttributeUpdate(Fields.GAME_TIME_STARTED.fieldName).put(Instant.now().epochSecond)
        )
        if (game.gameMeta.gameOptions != game.gameSetup().getDefaultConfig()) {
            updates = updates.plus(AttributeUpdate(Fields.GAME_OPTIONS.fieldName).put(convertToDBFormat(game.gameMeta.gameOptions)))
        }
        if (state != null) {
            updates = updates.plus(AttributeUpdate(Fields.MOVE_STATE.fieldName).put(state))
        }

        val update = UpdateItemSpec().withPrimaryKey(this.pk, pkValue, this.sk, pkValue)
            .withAttributeUpdate(updates)
        this.update("Create game $pkValue", update)
        val epochMilli = Instant.now().toEpochMilli()
        this.simpleUpdate(pkValue, SK_UNFINISHED, epochMilli)
//        this.simpleUpdate(pkValue, gameSortKey(game.gameId), epochSecond,
//            Fields.GAME_TYPE to game.gameType.type,
//            Fields.GAME_PLAYERS to game.players.map { mapOf("Name" to it.name!!) }
//        )

        // Add players in game
        val playerIds = game.players
            .map { it.playerId ?: throw IllegalStateException("Missing playerId for ${it.name}") }
        val players = playerIds.withIndex().groupBy({ it.value }) { it.index }
        players.forEach { (playerId, indexes) ->
            this.simpleUpdate(pkValue, Prefix.PLAYER.sk(playerId.toString()), epochMilli,
                Fields.GAME_PLAYERS to indexes.map {
                    mapOf("Index" to it)
                }
//                TODO: Instead of the above, use Player0: name, Player1: name
            )
        }
    }

    fun addMove(move: MoveEvent) {
        val epochMilli = Instant.now().toEpochMilli()
        val serverGame = move.game
        move.game.lastMove = epochMilli
        val moveIndex = serverGame.nextMoveIndex()
        val dbMove = if (serverGame.obj is GameImpl<*>) {
            val gameImpl = serverGame.obj as GameImpl<*>
            val actionType = gameImpl.actions.type(move.moveType)!!.actionType
            actionType.serialize(move.move)
        } else { move.move }
        val moveData = convertToDBFormat(dbMove)
        val state: Any? = gameRandomnessState(serverGame)
        val updates = mutableListOf(
            AttributeUpdate(Fields.MOVE_TIME.fieldName).put(epochMilli),
            AttributeUpdate(Fields.MOVE_PLAYER_INDEX.fieldName).put(move.player),
            AttributeUpdate(Fields.MOVE_TYPE.fieldName).put(move.moveType)
        )
        if (moveData != null) {
            updates += AttributeUpdate(Fields.MOVE.fieldName).put(moveData)
        }
        if (state != null) {
            updates += AttributeUpdate(Fields.MOVE_STATE.fieldName).put(state)
        }

        val update = UpdateItemSpec().withPrimaryKey(this.pk, Prefix.GAME.sk(serverGame.gameId),
            this.sk, Prefix.ZMOVE.sk(moveIndex.toString())).withAttributeUpdate(updates)
        this.update("Add Move $move", update)
    }

    private fun gameRandomnessState(serverGame: ServerGame): Any? {
        if (serverGame.obj is GameImpl<*>) {
            val game = serverGame.obj as GameImpl<*>
            val lastMoveState = game.stateKeeper.lastMoveState()
            if (lastMoveState.isNotEmpty()) {
                return convertToDBFormat(lastMoveState)
            }
        }
        return null
    }

    fun update(description: String, update: UpdateItemSpec) {
        this.logCapacity(description, { it.updateItemResult.consumedCapacity }, {
            table.table.updateItem(update.withReturnConsumedCapacity(ReturnConsumedCapacity.INDEXES))
        })
    }

    fun playerEliminated(event: PlayerEliminatedEvent) {
        val pkValue = Prefix.GAME.sk(event.game.gameId)

        val playerData = mapOf(
            "Result" to event.winner.result,
            "ResultPosition" to event.position,
            "ResultReason" to "eliminated"
        )
        this.update("Eliminate player ${event.player} in game $pkValue", UpdateItemSpec().withPrimaryKey(this.pk, pkValue, this.sk, pkValue)
          .withAttributeUpdate(AttributeUpdate(Fields.PLAYER_PREFIX.fieldName + event.player).put(playerData)))
    }

    fun finishGame(game: ServerGame) {
        val pkValue = Prefix.GAME.sk(game.gameId)
        this.logCapacity("remove unfinished ${game.gameId}", { it.deleteItemResult.consumedCapacity }) {
            table.table.deleteItem(DeleteItemSpec().withPrimaryKey(this.pk, pkValue, this.sk, SK_UNFINISHED)
                .withReturnConsumedCapacity(ReturnConsumedCapacity.INDEXES))
        }
        this.simpleUpdate(pkValue, SK_PUSH_TO_STATS, Instant.now().toEpochMilli())
    }

    fun <T : Any> logCapacity(description: String, capacity: (T) -> ConsumedCapacity?, function: () -> T): T {
        val startTime = System.nanoTime()
        val result = function()
        val endTime = System.nanoTime()
        val timeTaken = endTime - startTime
        logger.info { "Consumed Capacity on $description consumed ${capacity(result)} and took $timeTaken" }
        return result
    }

    fun getGame(game: DBGameSummary): DBGame {
        val moves = getGameMoves(game.gameId)
        return DBGame(game, moves)
    }

    fun listUnfinished(): Set<DBGameSummary> {
        return this.gsiLookup(QuerySpec().withHashKey(this.sk, SK_UNFINISHED).withRangeKeyCondition(RangeKeyCondition(this.data).gt(0))).map { unfinishedRow ->
            val gameId = unfinishedRow[this.pk] as String
            getGameSummary(gameId)
        }.filterNotNull().toSet()
    }

    fun getGameMoves(gameId: String): List<MoveHistory> {
        val dbMoves = this.queryTable(QuerySpec()
                .withHashKey(this.pk, Prefix.GAME.sk(gameId))
                .withRangeKeyCondition(Prefix.ZMOVE.rangeKeyCondition()))
        return dbMoves.map {
            Prefix.ZMOVE.extract(it.getString(this.sk)).toInt() to MoveHistory(
                it[Fields.MOVE_TYPE.fieldName] as String,
                (it[Fields.MOVE_PLAYER_INDEX.fieldName] as BigDecimal).toInt(),
                convertFromDBFormat(it[Fields.MOVE.fieldName]),
                convertFromDBFormat(it[Fields.MOVE_STATE.fieldName]) as Map<String, Any>?,
                (it[Fields.MOVE_TIME.fieldName] as BigDecimal).toLong() / 1000
            )
        }.sortedBy { it.first }.map { it.second }
    }

    fun authenticate(event: ClientLoginEvent) {
        // 1. Lookup GSI-1:   oauth:<provider>/<providerId>, any sort key. Get back playerId
        val provider = event.provider
        val providerId = event.providerId
        val skValue = Prefix.OAUTH.sk("$provider/$providerId")
        val existing = this.gsiLookup(QuerySpec().withHashKey(this.sk, skValue)).firstOrNull()
        // TODO: Cleanup database and change to singleOrNull()

        val timestamp = Instant.now().epochSecond

        // If exists, overwrite client playerId, and update sort key (last connected time)
        if (existing != null) {
            val uuid = existing.getString(this.pk).substringAfter(':')
            val pkValue = Prefix.PLAYER.sk(uuid)
            event.client.updateInfo(event.loginName, UUID.fromString(uuid))
            if (event.provider == ServerAIProvider) {
                logger.info { "AI Logged in: ${event.loginName} using id from database $uuid. Skipping timestamp update" }
                // Server AI times should be updated when the AI is used, not when starting the server
                return
            }
            val updateResult = this.table.table.updateItem(pk, pkValue, sk, skValue, AttributeUpdate(data).put(timestamp))
            logger.info("Update ${event.client.name}. Found $uuid. Update result $updateResult")
            return
        }

        // If not exists: PutItem - playerId, oauth:provider/ProviderId, last login
        if (event.client.playerId == null) {
            throw IllegalStateException("Client should have playerId set: ${event.client.name}")
        }

        val pkValue = Prefix.PLAYER.sk(event.client.playerId.toString())
        val putItemRequest = PutItemRequest(tableName, mapOf(
            pk to AttributeValue(pkValue),
            sk to AttributeValue(skValue),
            data to timeStamp(),
            Fields.PLAYER_NAME.fieldName to AttributeValue(event.client.name)
        )).withReturnConsumedCapacity(ReturnConsumedCapacity.INDEXES)
        val updateResult = this.logCapacity("authenticate $pkValue", { it.consumedCapacity }) { dynamoDB.putItem(putItemRequest) }
        logger.info("Update ${event.client.name}. Adding as $pkValue. Update result $updateResult")
    }

    private fun gsiLookup(query: QuerySpec): ItemCollection<QueryOutcome> {
        return this.logCapacity("Querying GSI on ${query.hashKey} / ${query.rangeKeyCondition}", { it.accumulatedConsumedCapacity }) {
            this.gsi.index.query(query.withReturnConsumedCapacity(ReturnConsumedCapacity.INDEXES))
        }
    }

    fun simpleUpdate(pkValue: String, skValue: String, data: Long?, vararg pairs: Pair<Fields, Any>) {
        val update = UpdateItemSpec().withPrimaryKey(this.pk, pkValue, this.sk, skValue)
            .withAttributeUpdate(
                pairs.map {
                    AttributeUpdate(it.first.fieldName).put(it.second)
                }.let {
                    if (data != null) it.plus(AttributeUpdate(this.data).put(data)) else it
                }
            )
        this.update("Simple Update $pkValue, $skValue", update)
    }

    fun getGameSummary(gameId: String): DBGameSummary? {
        val pureGameId = Prefix.GAME.extract(gameId)
        val query = QuerySpec()
            .withHashKey(this.pk, Prefix.GAME.sk(pureGameId))
            .withRangeKeyCondition(RangeKeyCondition(this.sk).between("a", "y"))

        val sks = this.queryTable(query).associateBy {
            it[this.sk] as String
        }

        val gameDetails = sks.getValue(gameId)
        logger.info { "Game Details for game $gameId: $gameDetails" }
        if (!gameDetails.hasAttribute(Fields.GAME_TYPE.fieldName)) {
            throw IllegalStateException("Missing gameType field for game $gameId")
        }
        val playersInGame = sks.filter { it.key.startsWith(Prefix.PLAYER.prefix) }.flatMap {playerEntry ->
            val playerId = Prefix.PLAYER.extract(playerEntry.key)
            // Look in it[Fields.GAME_PLAYERS] for which indexes a player belongs to. (Maybe also store name?)
            val indexes = playerEntry.value[Fields.GAME_PLAYERS.fieldName] as List<Map<String, Any>>
            return@flatMap indexes.map {playerInfo ->
                val index = (playerInfo["Index"] as BigDecimal).toInt()
                val playerName = playerInfo["Name"] as String? ?: findPlayerName(playerId) ?: "UNKNOWN"
                val attributeName = Fields.PLAYER_PREFIX.fieldName + index
                val hasDetails = gameDetails.hasAttribute(attributeName)
                val playerResults = if (hasDetails) {
                    val details = gameDetails[attributeName] as Map<String, Any>
                    val result = (details["Result"] as BigDecimal).toDouble()
                    val resultPosition = (details["ResultPosition"] as BigDecimal).toInt()
                    PlayerInGameResults(result, resultPosition, details["ResultReason"] as String, mapOf())
                } else null
                PlayerInGame(PlayerView(playerId, playerName), index, playerResults)
            }
        }.sortedBy { it.playerIndex }
        val unfinished = sks.any { it.key == this.SK_UNFINISHED || it.key == "tag:$SK_UNFINISHED" }
        val hidden = gameDetails.hasAttribute(Fields.GAME_HIDDEN.fieldName)
        val gameState = when {
            unfinished -> GameState.UNFINISHED
            hidden -> GameState.HIDDEN
            else -> GameState.PUBLIC
        }
        val startingState = convertFromDBFormat(gameDetails[Fields.MOVE_STATE.fieldName]) as Map<String, Any>?
        val timeStarted = gameDetails[Fields.GAME_TIME_STARTED.fieldName] as BigDecimal

        val gameType = gameDetails[Fields.GAME_TYPE.fieldName] as String
        val gameSpec = ServerGames.games[gameType] as GameSpec<Any>?
        if (gameSpec == null) {
            logger.warn { "Ignoring loading game $gameId. Expected gameType $gameType not found." }
            return null
        }

        val setup = GameSetupImpl(gameSpec)
        val config = if (gameDetails.hasAttribute(Fields.GAME_OPTIONS.fieldName)) {
            val gameConfigJSON =
                    gameDetails.getJSON(Fields.GAME_OPTIONS.fieldName)
            if (gameConfigJSON.length >= 10) JacksonTools.readValue(gameConfigJSON, setup.configClass().java) else setup.getDefaultConfig()
        } else setup.getDefaultConfig()

        return DBGameSummary(gameSpec, config, Prefix.GAME.extract(gameId), playersInGame, gameType, gameState.value,
                startingState, timeStarted.longValueExact())
    }

    private fun findPlayerName(playerId: String): String? {
        val query = QuerySpec()
                .withHashKey(this.pk, Prefix.PLAYER.sk(playerId))
                .withRangeKeyCondition(RangeKeyCondition(this.sk).between("o", "s"))
        val playerItem = this.queryTable(query).firstOrNull()
        return playerItem?.get("PlayerName") as String?
    }

    private fun queryTable(query: QuerySpec): ItemCollection<QueryOutcome> {
        return this.logCapacity("${query.hashKey} / ${query.rangeKeyCondition}", {it.accumulatedConsumedCapacity}) {
            this.table.table.query(query.withReturnConsumedCapacity(ReturnConsumedCapacity.INDEXES))
        }
    }

    fun cookieAuth(cookie: String): PlayerInfo? {
        val time = System.currentTimeMillis()
        val earliestPreviousLoginTime = time - TimeUnit.DAYS.toMillis(30)
        logger.info { "Looking for cookie $cookie with earliest previous login time $earliestPreviousLoginTime" }
        val skValue = Prefix.OAUTH.sk("guest/$cookie")
        val existing = this.gsiLookup(QuerySpec().withHashKey(this.sk, skValue)
                .withRangeKeyCondition(RangeKeyCondition(this.data).gt(earliestPreviousLoginTime / 1000))
        ).firstOrNull() ?: return null

        val itemWithData = this.getItem(existing.getString(this.pk), existing.getString(this.sk)) ?: return null
        logger.info { itemWithData.asMap() }
        val playerId = Prefix.PLAYER.extract(itemWithData.getString(this.pk))
        return PlayerInfo(itemWithData.getString(Fields.PLAYER_NAME.fieldName), UUID.fromString(playerId))
    }

    private fun getItem(pkValue: String, skValue: String): Item? {
        var result: Item? = null
        val time = measureNanoTime {
            result = table.table.getItem(this.pk, pkValue, this.sk, skValue)
        }
        logger.info { "Consumed Capacity on GetItem($pkValue, $skValue) consumed null and took $time" }
        return result
    }


}

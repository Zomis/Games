package net.zomis.games.server2.db.files

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.listeners.CombinedListener
import net.zomis.games.jackson.ReplayDataDeserializer
import net.zomis.games.listeners.FileReplay
import net.zomis.games.listeners.ReplayListener
import net.zomis.games.server2.ClientLoginEvent
import net.zomis.games.server2.PlayerDatabaseInfo
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.db.*
import net.zomis.games.server2.games.ServerGame
import java.time.Instant
import java.util.*
import kotlin.io.path.*

class FileDB: DBInterface {
    private val mapper = jacksonObjectMapper()

    val rootPath = Path("db")
    val gamesPath = rootPath.resolve("games").also { it.createDirectories() }
    val playersFile = rootPath.resolve("players.json").also {
        if (it.notExists()) mapper.writeValue(it.outputStream(), emptyMap<String, Any>())
    }
    fun path(gameId: String) = gamesPath.resolve("$gameId.json")

    override fun listUnfinished(): Set<DBGameSummary> {
//        gamesPath.forEach {  }
        TODO("Not yet implemented")
    }

    override fun loadGame(gameId: String): DBGame {
        val gameTypeLookup: (String) -> GameSpec<out Any> = { ServerGames.games.getValue(it) }
        val fullFile = path("$gameId-full")
        if (fullFile.isRegularFile()) {
            val fullTree = mapper.readTree(fullFile.inputStream()) as ObjectNode
            val replayData = ReplayDataDeserializer.deserialize(fullTree, gameTypeLookup)
            val summary = DBGameSummary(
                gameTypeLookup.invoke(replayData.gameType) as GameSpec<Any>,
                replayData.config,
                gameId,
                (0 until replayData.playerCount).map { PlayerInGame(PlayerView("replay-$gameId-$it", "Player $it"), it, null) },
                replayData.gameType,
                0,
                replayData.initialState,
                Instant.now().toEpochMilli()
            )
            val moveHistory = replayData.actions.map { MoveHistory(it.actionType, it.playerIndex, it.serializedParameter, it.state) }
            return DBGame(summary, moveHistory)
        }
        val treeSummary = mapper.readTree(path("$gameId-summary").inputStream()) as ObjectNode
        val summary = ReplayDataDeserializer.deserializeDBSummary(treeSummary) { ServerGames.games[it] }
        val tree: ObjectNode = jacksonObjectMapper().readTree(path(gameId).inputStream()) as ObjectNode
        val t = ReplayDataDeserializer.deserialize(tree) { ServerGames.games[it] }
        val moveHistory = t.actions.map { MoveHistory(it.actionType, it.playerIndex, it.serializedParameter, it.state) }
        return DBGame(summary, moveHistory)
    }

    override fun loadGameIgnoreErrors(gameId: String): DBGame = loadGame(gameId)

    override fun gameListener(serverGame: ServerGame, replayListener: ReplayListener): GameListener {
        val saveGeneric = object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                if (step is FlowStep.GameSetup<*> || step is FlowStep.Elimination || step is FlowStep.GameEnd) {
                    mapper.writeValue(path(serverGame.gameId + "-summary").outputStream(), serverGame.toDBSummary().serialize())
                }
            }
        }
        return CombinedListener(FileReplay(path(serverGame.gameId), replayListener), saveGeneric)
    }

    override fun cookieAuth(cookie: String): PlayerDatabaseInfo? {
        val tree = mapper.readTree(rootPath.resolve("cookies.json").inputStream())
        return mapper.convertValue(tree.get(cookie), PlayerDatabaseInfo::class.java)
    }

    override fun authenticate(event: ClientLoginEvent) {
        val id = "${event.provider}/${event.providerId}"
        // if exists - use playerId
        // if not exists - save UUID as playerId
        val tree = mapper.readTree(playersFile.inputStream()) as ObjectNode
        if (tree.has(id)) {
            val node = tree[id]
            event.client.updateInfo(node.get("name").asText(), UUID.fromString(node.get("playerId").asText()))
        } else {
            val node: ObjectNode = mapper.createObjectNode().also {
                it.put("name", event.client.name)
                it.put("playerId", event.client.playerId.toString())
            }
            tree.set<JsonNode>(id, node)
            mapper.writeValue(playersFile.outputStream(), tree)
        }
    }

}

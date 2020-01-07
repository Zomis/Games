package net.zomis.games.server2.db

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.*
import com.amazonaws.services.dynamodbv2.model.*
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.ClientLoginEvent
import net.zomis.games.server2.ais.ServerAIProvider
import net.zomis.games.server2.getTextOrDefault
import java.time.Instant
import java.util.UUID

class AuthTable(private val dynamoDB: AmazonDynamoDB) {

    private val logger = KLoggers.logger(this)

    private val tableName = "Server2-Players"
    private val playerId = "PlayerId"
    private val authType = "AuthType"
    private val authId = "AuthId"
    private val playerName = "PlayerName"
    private val timeLastConnected = "TimeLastConnected"
    private val secretSessionId = "SecretSessionId"

    private val myTable = MyTable(dynamoDB, tableName)
        .strings(playerId, authType, authId, playerName, secretSessionId)
    private val primaryIndex = myTable.primaryIndex(playerId)
    private val authIndex = myTable.index(ProjectionType.ALL, listOf(authType), listOf(authId))
    private val nameIndex = myTable.index(ProjectionType.KEYS_ONLY, listOf(playerName), emptyList())
    private val sessionIndex = myTable.index(ProjectionType.ALL, listOf(secretSessionId), emptyList())

    fun register(events: EventSystem): CreateTableRequest {
        events.listen("Auth", ClientLoginEvent::class, {true}, {
            authenticationDone(it)
        })
        events.listen("Github Authentication", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "Auth" &&
                    it.data.getTextOrDefault("provider", "") == "session"
        }, {
            fetchUserSession(events, it.client, it.data.getTextOrDefault("token", ""))
        })
        return myTable.createTableRequest()
    }

    private fun fetchUserSession(events: EventSystem, client: Client, session: String) {
        val hashKeyResult = sessionIndex.query(secretSessionId to session)
        val result = hashKeyResult.firstOrNull()
        if (result == null) {
            client.send(mapOf("type" to "AuthenticationError", "message" to "session id not found"))
            return
        }
        val authType = result.getString(authType)
        val authId = result.getString(authId)
        TODO("Check with github if authId token works. If it does, just update session. If it does not, invalidate session")

//        val identifier = result.getString(playerId)
//        client.name = result.getString(playerName)
//        events.execute(ClientLoginEvent(client, client.name!!, "session", session))
    }

    private fun authenticationDone(event: ClientLoginEvent) {
        // Check if user exists (authType + authId)
        // If not then create
        // Otherwise update TimeLastConnected and session and respond with session key to client
        val existing = authIndex.query(authType to event.provider, authId to event.token).singleOrNull()
        val timestamp = Instant.now().epochSecond
        if (existing != null) {
            val uuid = existing.getString(this.playerId)
            event.client.playerId = UUID.fromString(uuid)
            if (event.provider == ServerAIProvider) {
                return
            }
            val updateResult = myTable.table.updateItem(
                playerId, existing.getString(playerId), AttributeUpdate(timeLastConnected).put(timestamp))
            logger.info("Update ${event.client.name}. Found $uuid. Update result $updateResult")
            return
        }
        if (event.client.playerId == null) {
            throw IllegalStateException("Client should have playerId set: ${event.client.name}")
        }

        val uuid = event.client.playerId!!
        val putItemRequest = PutItemRequest(tableName, mapOf(
            playerId to AttributeValue(uuid.toString()),
            authType to AttributeValue(event.provider),
            authId to AttributeValue(event.token),
            timeLastConnected to timeStamp(),
            playerName to AttributeValue(event.loginName)
        ))
        val updateResult = dynamoDB.putItem(putItemRequest)
        logger.info("Update ${event.client.name}. Adding as $uuid. Update result $updateResult")
    }

    fun fetchPlayerView(playerId: String): GamesTables.PlayerView? {
        val item = this.myTable.table.query(this.playerId, playerId).firstOrNull() ?: return null
        return GamesTables.PlayerView(playerId, item.getString(this.playerName))
    }

}
package net.zomis.games.server2.invites

import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.dsl.GameSpec
import net.zomis.games.example.TestGames
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientDisconnected
import net.zomis.games.server2.doctools.DocEventSystem
import net.zomis.games.server2.doctools.DocWriter
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.games.*
import net.zomis.games.server2.testDocWriter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

val Client.idName get() = """{"id":"${this.playerId}","name":"${this.name}","picture":"${this.picture}"}"""

class LobbySystemTest {

    private lateinit var events: EventSystem
    private lateinit var features: Features
    private lateinit var clientAB2: FakeClient
    private lateinit var clientA1: FakeClient
    private lateinit var clientB1: FakeClient
    private lateinit var asker: FakeClient
    private lateinit var lobby: LobbySystem
    private val idGenerator: GameIdGenerator = { "1" }

    @RegisterExtension
    @JvmField
    val docWriter: DocWriter = testDocWriter()

    @BeforeEach
    fun setup() {
        events = DocEventSystem(docWriter)
        features = Features(events)
        lobby = LobbySystem(features)
        val gameSystem = GameSystem(lobby::gameClients, GameCallback({null}) {})
        features.add { f, e -> gameSystem.setup(f, e, idGenerator) }
        events.with(lobby::setup)
        clientAB2 = FakeClient().apply { updateInfo("AB2", UUID.fromString("11111111-1111-1111-1111-111111111111")) }
        clientA1 = FakeClient().apply { updateInfo("A1", UUID.fromString("22222222-2222-2222-2222-222222222222")) }
        clientB1 = FakeClient().apply { updateInfo("B1", UUID.fromString("33333333-3333-3333-3333-333333333333")) }
        asker = FakeClient().apply { updateInfo("Asker", UUID.fromString("44444444-4444-4444-4444-444444444444")) }

        events.execute(GameTypeRegisterEvent(TestGames.gameTypeA))
        events.execute(GameTypeRegisterEvent(TestGames.gameTypeB))
        setForClient(ClientInterestingGames(setOf("A", "B"), 2, mutableSetOf()), clientAB2)
        setForClient(ClientInterestingGames(setOf("A"), 1, mutableSetOf()), clientA1)
        setForClient(ClientInterestingGames(setOf("B"), 1, mutableSetOf()), clientB1)
        setForClient(ClientInterestingGames(setOf("A", "B"), 2, mutableSetOf()), asker)
    }

    private fun setForClient(value: ClientInterestingGames, client: FakeClient) {
        client.interestingGames = value
        value.interestingGames.forEach {
            val gameType = features[GameSystem.GameTypes::class]!!.gameTypes[it]!!
            lobby.gameClients(gameType.type)!!.add(client)
        }
    }

    @Test
    fun testDisconnectRemovesPlayer() {
        events.execute(ClientDisconnected(clientA1))

        events.apply {
            lobby.sendAvailableUsers(asker)
            Assertions.assertEquals("""{"type":"LobbyChange","player":${clientA1.idName},"action":"left"}""", asker.nextMessage())
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":[${clientAB2.idName}],"B":[${clientAB2.idName},${clientB1.idName}]}}""", asker.nextMessage())
        }
    }

    @Test
    fun testAB() {
        events.apply {
            lobby.sendAvailableUsers(asker)
            val result = asker.nextMessage()
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":[${clientAB2.idName},${clientA1.idName}],"B":[${clientAB2.idName},${clientB1.idName}]}}""", result)
        }
    }

    @Test
    fun testGameStartEnd() {
        val callback = GameCallback(
            gameLoader = { null },
            moveHandler = {}
        )
        val inviteOptions = InviteOptions(false, InviteTurnOrder.ORDERED, -1, Unit, false)
        val game = ServerGame(callback, GameType(callback, TestGames.gameTypeA as GameSpec<Any>, {null}, events, idGenerator, null), idGenerator(), inviteOptions)
        game.players[clientAB2] = ClientAccess(gameAdmin = false).addAccess(0, ClientPlayerAccessType.ADMIN)
        game.players[clientA1] = ClientAccess(gameAdmin = false).addAccess(1, ClientPlayerAccessType.ADMIN)
        events.execute(GameStartedEvent(game))

        events.apply {
            lobby.sendAvailableUsers(asker)
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":[${clientAB2.idName}],"B":[${clientAB2.idName},${clientB1.idName}]}}""", asker.nextMessage())
        }

        events.execute(GameEndedEvent(game))
        events.apply {
            lobby.sendAvailableUsers(asker)
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":[${clientAB2.idName},${clientA1.idName}],"B":[${clientAB2.idName},${clientB1.idName}]}}""", asker.nextMessage())
        }
    }

}
package net.zomis.games.server2.invites

import net.zomis.core.events.EventSystem
import net.zomis.games.Features
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

class LobbySystemTest {

    private lateinit var events: EventSystem
    private lateinit var features: Features
    private lateinit var clientAB2: FakeClient
    private lateinit var clientA1: FakeClient
    private lateinit var clientB1: FakeClient
    private lateinit var asker: FakeClient
    private val lobby = LobbySystem()
    private val idGenerator: GameIdGenerator = { "1" }

    @RegisterExtension
    @JvmField
    val docWriter: DocWriter = testDocWriter()

    @BeforeEach
    fun setup() {
        events = DocEventSystem(docWriter)
        features = Features(events)
        features.add { f, e -> GameSystem().setup(f, e, idGenerator) }
        features.add(lobby::setup)
        clientAB2 = FakeClient().apply { name = "AB2" }
        clientA1 = FakeClient().apply { name = "A1" }
        clientB1 = FakeClient().apply { name = "B1" }
        asker = FakeClient().apply { name = "Asker" }

        events.execute(GameTypeRegisterEvent("A"))
        events.execute(GameTypeRegisterEvent("B"))
        setForClient(ClientInterestingGames(setOf("A", "B"), 2, mutableSetOf()), clientAB2)
        setForClient(ClientInterestingGames(setOf("A"), 1, mutableSetOf()), clientA1)
        setForClient(ClientInterestingGames(setOf("B"), 1, mutableSetOf()), clientB1)
        setForClient(ClientInterestingGames(setOf("A", "B"), 2, mutableSetOf()), asker)
    }

    private fun setForClient(value: ClientInterestingGames, client: FakeClient) {
        client.features.addData(value)
        value.interestingGames.forEach {
            val gameType = features[GameSystem.GameTypes::class]!!.gameTypes[it]!!
            gameType.clients.add(client)
        }
    }

    @Test
    fun testDisconnectRemovesPlayer() {
        events.execute(ClientDisconnected(clientA1))

        events.apply {
            execute(ListRequest(asker))
            Assertions.assertEquals("""{"type":"LobbyChange","client":"A1","action":"left"}""", asker.nextMessage())
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":["AB2"],"B":["AB2","B1"]}}""", asker.nextMessage())
        }
    }

    @Test
    fun testAB() {
        events.apply {
            execute(ListRequest(asker))
            val result = asker.nextMessage()
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":["AB2","A1"],"B":["AB2","B1"]}}""", result)
        }
    }

    @Test
    fun testGameStartEnd() {
        val game = ServerGame(GameType("A", events, idGenerator), idGenerator(), ServerGameOptions(false))
        game.players.add(clientAB2)
        game.players.add(clientA1)
        events.execute(GameStartedEvent(game))

        events.apply {
            execute(ListRequest(asker))
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":["AB2"],"B":["AB2","B1"]}}""", asker.nextMessage())
        }

        events.execute(GameEndedEvent(game))
        events.apply {
            execute(ListRequest(asker))
            Assertions.assertEquals("""{"type":"Lobby","users":{"A":["AB2","A1"],"B":["AB2","B1"]}}""", asker.nextMessage())
        }
    }

}
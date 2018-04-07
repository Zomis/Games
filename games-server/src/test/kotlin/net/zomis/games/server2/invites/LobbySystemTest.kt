package net.zomis.games.server2.invites

import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ClientDisconnected
import net.zomis.games.server2.games.Game
import net.zomis.games.server2.games.GameEndedEvent
import net.zomis.games.server2.games.GameStartedEvent
import net.zomis.games.server2.games.GameType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LobbySystemTest {

    private lateinit var events: EventSystem
    private lateinit var clientAB2: FakeClient
    private lateinit var clientA1: FakeClient
    private lateinit var clientB1: FakeClient
    private lateinit var asker: FakeClient
    private val lobby = LobbySystem()

    @BeforeEach
    fun setup() {
        events = EventSystem().with(lobby::register)
        clientAB2 = FakeClient().apply { name = "AB2" }
        clientA1 = FakeClient().apply { name = "A1" }
        clientB1 = FakeClient().apply { name = "B1" }
        asker = FakeClient().apply { name = "Asker" }

        lobby.clientData.setForClient(ClientInterestingGames(setOf("A", "B"), 2, mutableSetOf()), clientAB2)
        lobby.clientData.setForClient(ClientInterestingGames(setOf("A"), 1, mutableSetOf()), clientA1)
        lobby.clientData.setForClient(ClientInterestingGames(setOf("B"), 1, mutableSetOf()), clientB1)
        lobby.clientData.setForClient(ClientInterestingGames(setOf("A", "B"), 2, mutableSetOf()), asker)
    }

    @Test
    fun testDisconnectRemovesPlayer() {
        events.execute(ClientDisconnected(clientA1))

        events.apply {
            execute(ListRequest(asker))
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
        val game = Game(GameType("A"), "game-a-1")
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
package net.zomis.games.server2.invites

import net.zomis.games.server2.*
import net.zomis.games.server2.doctools.DocEventSystem
import net.zomis.games.server2.doctools.DocWriter
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.games.GameTypeRegisterEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class LoginLobbyDocTest {

    @RegisterExtension
    @JvmField
    val docWriter: DocWriter = testDocWriter("LOGIN_AND_LOBBY")

    @Test
    fun document() {
        val events = DocEventSystem(docWriter)
        val server2 = Server2(events)
        server2.start(testServerConfig())
        events.execute(GameTypeRegisterEvent("TestGameType"))
        events.execute(GameTypeRegisterEvent("OtherGameType"))
        events.listen("fixed playerId", ClientLoginEvent::class, {true}) {
            it.client.updateInfo(it.client.name!!, UUID.fromString("00000000-0000-0000-0000-000000000000"))
        }

        fun authTest(message: ClientJsonMessage) {
            AuthorizationSystem(events).handleGuest(message.client, "12345")
        }
        server2.messageRouter.handler("auth/guest", ::authTest)

        val clientA = FakeClient()
        val clientB = FakeClient().apply { updateInfo("Client B", UUID.fromString("11111111-1111-1111-1111-111111111111")) }
        clientB.sendToServer(events, """{ "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")

        docWriter.document(events, "Authentication") {
            text(""""Use `"route": "auth/guest"` and don't send a `name`. You will be randomly given a guest name.""")
            send(clientA, "When you have not yet been given a name you can send", """{ "route": "auth/guest" }""")
            text("You will receive your name and your playerId")
            receive(clientA, """{"type":"Auth","playerId":"00000000-0000-0000-0000-000000000000","name":"guest-12345"}""")
        }
        docWriter.document(events, "Entering a lobby") {
            send(clientA, """{ "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")
        }
        docWriter.document(events, "Listing available players") {
            send(clientA, """{ "route": "lobby/list" }""")
            val textClientB = clientB.idName
            receive(clientA, """{"type":"Lobby","users":{"TestGameType":[$textClientB],"OtherGameType":[$textClientB]}}""")
        }
        docWriter.document(events, "When someone disconnects") {
            text("Whenever you are in a lobby and another client in the same lobby disconnects, you will be notified instantly.")
            events.execute(ClientDisconnected(clientB))
            receive(clientA, """{"type":"LobbyChange","player":${clientB.idName},"action":"left"}""")
        }
        docWriter.document(events, "When a new client joins") {
            val clientC = FakeClient().apply { updateInfo("Client C", UUID.fromString("22222222-2222-2222-2222-222222222222")) }
            clientC.sendToServer(events, """{ "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")

            text("Whenever you are in a lobby and another client joins the same lobby, you will be notified instantly.")
            receive(clientA, """{"type":"LobbyChange","player":${clientC.idName},"action":"joined","gameTypes":["TestGameType","OtherGameType"]}""")
        }
    }

}

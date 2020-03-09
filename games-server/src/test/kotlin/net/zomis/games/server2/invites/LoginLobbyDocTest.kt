package net.zomis.games.server2.invites

import net.zomis.games.server2.*
import net.zomis.games.server2.doctools.DocEventSystem
import net.zomis.games.server2.doctools.DocWriter
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.games.GameTypeRegisterEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

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

        fun authTest(message : ClientJsonMessage) {
            AuthorizationSystem(events).handleGuest(message.client, "12345")
        }
        server2.messageRouter.handler("auth/guest", ::authTest)

        val clientA = FakeClient()
        val clientB = FakeClient().apply { name = "Client B" }
        clientB.sendToServer(events, """{ "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")

        docWriter.document(events, "Authentication") {
            text(""""Use `"route": "auth/guest"` and don't send a `name`. You will be randomly given a guest name.""")
            send(clientA, "When you have not yet been given a name you can send", """{ "route": "auth/guest" }""")
            text("From now on you will be known as `guest-12345`")
            receive(clientA, """{"type":"Auth","name":"guest-12345"}""")
        }
        docWriter.document(events, "Entering a lobby") {
            send(clientA, """{ "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")
        }
        docWriter.document(events, "Listing available players") {
            send(clientA, """{ "route": "lobby/list" }""")
            receive(clientA, """{"type":"Lobby","users":{"TestGameType":["Client B"],"OtherGameType":["Client B"]}}""")
        }
        docWriter.document(events, "When someone disconnects") {
            text("Whenever you are in a lobby and another client in the same lobby disconnects, you will be notified instantly.")
            text("TODO: This is not implemented yet.")
//            receive(clientA, """""")
        }
        docWriter.document(events, "When a new client joins") {
            text("Whenever you are in a lobby and another client joins the same lobby, you will be notified instantly.")
            text("TODO: This is not implemented yet.")
//            receive(clientA, """""")
        }
    }

}

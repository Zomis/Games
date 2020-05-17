package net.zomis.games.server2.invites

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.ClientLoginEvent
import net.zomis.games.server2.MessageRouter
import net.zomis.games.server2.doctools.DocEventSystem
import net.zomis.games.server2.doctools.DocWriter
import net.zomis.games.server2.doctools.EventsExpect
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.games.*
import net.zomis.games.server2.testDocWriter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

private val mapper = jacksonObjectMapper()
fun stringMapExpect(actual: String, vararg expected: Pair<String, Any?>) {
    val documentationExcpected = mapOf(*expected).plus("..." to "...")
    val actualMap: Map<String, Any?> = mapper.readValue(actual)
    val match = expected.all { actualMap[it.first] == it.second }

    Assertions.assertTrue(match) { "Expected\n$documentationExcpected\nbut was\n$actualMap" }
}

class InviteSystemTest {

    lateinit var events: EventSystem
    lateinit var features: Features
    lateinit var host: FakeClient
    lateinit var invitee: FakeClient

    @RegisterExtension
    @JvmField
    val expect: EventsExpect = EventsExpect()

    @RegisterExtension
    @JvmField
    val docWriter: DocWriter = testDocWriter("INVITES")

    private val idGenerator: GameIdGenerator = { "1" }
    private lateinit var system: InviteSystem
    private val inviteOptions = InviteOptions(false, InviteTurnOrder.ORDERED, -1, Unit, false)

    @BeforeEach
    fun before() {
        events = DocEventSystem(docWriter)
        features = Features(events)

        val lobbySystem = LobbySystem(features)
        events.with(lobbySystem::setup)
        val router = MessageRouter(this).route("lobby", lobbySystem.router)
        events.listen("Route", ClientJsonMessage::class, {it.data.has("route")}) {
            router.handle(it.data["route"].asText(), it)
        }
        val gameSystem = GameSystem(lobbySystem::gameClients, GameCallback({null}) {})
        features.add { f, e -> gameSystem.setup(f, e, idGenerator) }

        fun createGameCallback(gameType: String, options: InviteOptions): ServerGame
            = gameSystem.getGameType(gameType)!!.createGame(options)

        system = InviteSystem(
                gameClients = lobbySystem::gameClients,
                createGameCallback = ::createGameCallback,
                startGameExecutor = { events.execute(it) },
                inviteIdGenerator = { "12345678-1234-1234-1234-123456789abc" })
        router.route("invites", system.router)
        // Don't need the LobbySystem feature here

        host = FakeClient().apply { updateInfo("Host", UUID.fromString("00000000-0000-0000-0000-000000000000")) }
        invitee = FakeClient().apply { updateInfo("Invited", UUID.fromString("11111111-1111-1111-1111-111111111111")) }
    }

    @Test
    fun fullInvite() {
        events.with(LobbySystem(features)::setup) // We need to lookup player by name
        events.execute(GameTypeRegisterEvent("TestGameType"))
        events.execute(GameTypeRegisterEvent("OtherGameType"))
        host = FakeClient().apply { updateInfo("TestClientA", UUID.fromString("00000000-0000-0000-0000-000000000000")) }
        invitee = FakeClient().apply { updateInfo("TestClientB", UUID.fromString("11111111-1111-1111-1111-111111111111")) }

//        events.execute(ClientLoginEvent(host, host.name!!, "tests", "token"))
//        events.execute(ClientLoginEvent(invitee, invitee.name!!, "tests", "token2"))
        host.sendToServer(events, """{ "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")
        invitee.sendToServer(events, """{ "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")
        Assertions.assertEquals("""{"type":"LobbyChange","player":${invitee.idName},"action":"joined","gameTypes":["TestGameType","OtherGameType"]}""", host.nextMessage())

        docWriter.document(events, "Inviting someone to play a game") {
            text("Inviting players is done by inviting their playerId, which will be unique")
            send(host, """{ "route": "invites/invite", "gameType": "TestGameType", "invite": ["11111111-1111-1111-1111-111111111111"] }""")
            receive(host, """{"type":"InviteWaiting","inviteId":"12345678-1234-1234-1234-123456789abc","playersMin":2,"playersMax":2}""")

            receive(invitee, """{"type":"Invite","host":"TestClientA","game":"TestGameType","inviteId":"12345678-1234-1234-1234-123456789abc"}""")
            receive(host, """{"type":"InviteStatus","playerId":"11111111-1111-1111-1111-111111111111","status":"pending","inviteId":"12345678-1234-1234-1234-123456789abc"}""")
        }

        docWriter.document(events, "Accepting an invite") {
            send(invitee, """{ "route": "invites/12345678-1234-1234-1234-123456789abc/respond", "accepted": true }""")
            receive(host, mapOf("type" to "InviteView"))
            receive(host, """{"type":"InviteResponse","inviteId":"12345678-1234-1234-1234-123456789abc","playerId":"11111111-1111-1111-1111-111111111111","accepted":true}""")

            text("When a user accepts an invite the game is started automatically and both players will receive a `GameStarted` message.")
            val playersString = """${host.idName},${invitee.idName}"""
            receive(invitee, mapOf("type" to "InviteView"))
            receive(invitee, mapOf("type" to "InviteView"))
            receive(invitee, """{"type":"GameStarted","gameType":"TestGameType","gameId":"1","yourIndex":1,"players":[$playersString]}""")
        }

        system.createInvite("TestGameType", "12345678-1234-1234-1234-123456789abc", inviteOptions, host, listOf(invitee))
        host.clearMessages()

        docWriter.document(events, "Declining an invite") {
            send(invitee, """{ "route": "invites/12345678-1234-1234-1234-123456789abc/respond", "accepted": false }""")
            receive(host, """{"type":"InviteResponse","inviteId":"12345678-1234-1234-1234-123456789abc","playerId":"11111111-1111-1111-1111-111111111111","accepted":false}""")
        }
    }

    @Test
    fun inviteAccepted() {
        events.execute(GameTypeRegisterEvent("MyGame"))
        events.execute(ClientLoginEvent(host, host.name!!, host.name!!, "tests", "token"))
        events.execute(ClientLoginEvent(invitee, invitee.name!!, invitee.name!!, "tests", "token2"))
        expect.event(events to GameStartedEvent::class).condition { true }

        val invite = system.createInvite("MyGame", "inv-1", inviteOptions, host, listOf(invitee))
        Assertions.assertEquals("""{"type":"Invite","host":"Host","game":"MyGame","inviteId":"inv-1"}""", invitee.nextMessage())
        Assertions.assertEquals("""{"type":"InviteWaiting","inviteId":"inv-1","playersMin":2,"playersMax":2}""", host.nextMessage())

        Assertions.assertEquals("""{"type":"InviteStatus","playerId":"11111111-1111-1111-1111-111111111111","status":"pending","inviteId":"inv-1"}""", host.nextMessage())
        stringMapExpect(host.nextMessage(), "type" to "InviteView")

        invite.respond(invitee, true)
        Assertions.assertEquals("""{"type":"InviteResponse","inviteId":"inv-1","playerId":"11111111-1111-1111-1111-111111111111","accepted":true}""", host.nextMessage())

        val playersString = """${host.idName},${invitee.idName}"""
        stringMapExpect(host.nextMessage(), "type" to "InviteView")
        Assertions.assertEquals("""{"type":"GameStarted","gameType":"MyGame","gameId":"1","yourIndex":0,"players":[$playersString]}""", host.nextMessage())
        stringMapExpect(invitee.nextMessage(), "type" to "InviteView")
        stringMapExpect(invitee.nextMessage(), "type" to "InviteView")
        Assertions.assertEquals("""{"type":"GameStarted","gameType":"MyGame","gameId":"1","yourIndex":1,"players":[$playersString]}""", invitee.nextMessage())
    }

    @Test
    fun inviteDeclined() {
        events.execute(GameTypeRegisterEvent("MyGame"))
        val invite = system.createInvite("MyGame", "inv-1", inviteOptions, host, listOf(invitee))
        Assertions.assertEquals("""{"type":"Invite","host":"Host","game":"MyGame","inviteId":"inv-1"}""", invitee.nextMessage())
        Assertions.assertEquals("""{"type":"InviteWaiting","inviteId":"inv-1","playersMin":2,"playersMax":2}""", host.nextMessage())

        Assertions.assertEquals("""{"type":"InviteStatus","playerId":"11111111-1111-1111-1111-111111111111","status":"pending","inviteId":"inv-1"}""", host.nextMessage())
        stringMapExpect(host.nextMessage(), "type" to "InviteView")

        invite.respond(invitee, false)
        Assertions.assertEquals("""{"type":"InviteResponse","inviteId":"inv-1","playerId":"11111111-1111-1111-1111-111111111111","accepted":false}""", host.nextMessage())
        stringMapExpect(host.nextMessage(), "type" to "InviteView")
    }

}
package net.zomis.games.server2.invites

import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.server2.ClientLoginEvent
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

    @BeforeEach
    fun before() {
        events = DocEventSystem(docWriter)
        features = Features(events)

        system = InviteSystem()
        features.add { f, e -> GameSystem().setup(f, e, idGenerator) }
        // Don't need the LobbySystem feature here
        features.add(system::setup)

        host = FakeClient().apply { name = "Host" }
        invitee = FakeClient().apply { name = "Invited" }
    }

    @Test
    fun fullInvite() {
        features.add(LobbySystem()::setup) // We need to lookup player by name
        events.execute(GameTypeRegisterEvent("TestGameType"))
        events.execute(GameTypeRegisterEvent("OtherGameType"))
        val host = FakeClient().apply { name = "TestClientA" }
        val invitee = FakeClient().apply { name = "TestClientB" }
        events.execute(ClientLoginEvent(host, host.name!!, "tests", "token"))
        events.execute(ClientLoginEvent(invitee, invitee.name!!, "tests", "token2"))
        host.sendToServer(events, """{ "type": "ClientGames", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")
        invitee.sendToServer(events, """{ "type": "ClientGames", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }""")
        Assertions.assertEquals("""{"type":"LobbyChange","client":"TestClientB","action":"joined","gameTypes":["TestGameType","OtherGameType"]}""", host.nextMessage())

        docWriter.document(events, "Inviting someone to play a game") {
            send(host, """{ "type": "Invite", "gameType": "TestGameType", "invite": ["TestClientB"] }""")
            receive(host, """{"type":"InviteWaiting","inviteId":"TestGameType-TestClientA-0","waitingFor":["TestClientB"]}""")
            receive(invitee, """{"type":"Invite","host":"TestClientA","game":"TestGameType","inviteId":"TestGameType-TestClientA-0"}""")
        }

        docWriter.document(events, "Accepting an invite") {
            send(invitee, """{ "type": "InviteResponse", "invite": "TestGameType-TestClientA-0", "accepted": true }""")
            receive(host, """{"type":"InviteResponse","user":"TestClientB","accepted":true,"inviteId":"TestGameType-TestClientA-0"}""")

            text("When a user accepts an invite the game is started automatically and both players will receive a `GameStarted` message.")
            receive(invitee, """{"type":"GameStarted","gameType":"TestGameType","gameId":"1","yourIndex":1,"players":["TestClientA","TestClientB"]}""")
        }

        val invite = Invite(host, mutableListOf(), mutableListOf(), GameType("TestGameType", events, idGenerator), "TestGameType-TestClientA-0")
        system.invites[invite.id] = invite
        events.execute(InviteEvent(host, invite, listOf(invitee)))
        host.clearMessages()

        docWriter.document(events, "Declining an invite") {
            send(invitee, """{ "type": "InviteResponse", "invite": "TestGameType-TestClientA-0", "accepted": false }""")
            receive(host, """{"type":"InviteResponse","user":"TestClientB","accepted":false,"inviteId":"TestGameType-TestClientA-0"}""")
        }
    }

    @Test
    fun inviteAccepted() {
        events.execute(ClientLoginEvent(host, host.name!!, "tests", "token"))
        events.execute(ClientLoginEvent(invitee, invitee.name!!, "tests", "token2"))

        val invite = Invite(host, mutableListOf(), mutableListOf(), GameType("MyGame", events, idGenerator), "inv-1")
        events.execute(InviteEvent(host, invite, listOf(invitee)))
        Assertions.assertEquals("""{"type":"Invite","host":"Host","game":"MyGame","inviteId":"inv-1"}""", invitee.nextMessage())
        Assertions.assertEquals("""{"type":"InviteWaiting","inviteId":"inv-1","waitingFor":["Invited"]}""", host.nextMessage())

        expect.event(events to GameStartedEvent::class).condition { true }
        events.execute(InviteResponseEvent(invitee, invite, true))

        Assertions.assertEquals("""{"type":"InviteResponse","user":"Invited","accepted":true,"inviteId":"inv-1"}""", host.nextMessage())
        Assertions.assertEquals("""{"type":"GameStarted","gameType":"MyGame","gameId":"1","yourIndex":0,"players":["Host","Invited"]}""", host.nextMessage())
        Assertions.assertEquals("""{"type":"GameStarted","gameType":"MyGame","gameId":"1","yourIndex":1,"players":["Host","Invited"]}""", invitee.nextMessage())
    }

    @Test
    fun inviteDeclined() {
        val invite = Invite(host, mutableListOf(), mutableListOf(), GameType("MyGame", events, idGenerator), "inv-1")
        events.execute(InviteEvent(host, invite, listOf(invitee)))
        Assertions.assertEquals("""{"type":"Invite","host":"Host","game":"MyGame","inviteId":"inv-1"}""", invitee.nextMessage())
        Assertions.assertEquals("""{"type":"InviteWaiting","inviteId":"inv-1","waitingFor":["Invited"]}""", host.nextMessage())

        events.execute(InviteResponseEvent(invitee, invite, false))
        Assertions.assertEquals("""{"type":"InviteResponse","user":"Invited","accepted":false,"inviteId":"inv-1"}""", host.nextMessage())

    }

}
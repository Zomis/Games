package net.zomis.games.server2.ecs

import com.fasterxml.jackson.databind.node.ObjectNode
import net.zomis.games.server2.Connect4Test
import net.zomis.games.server2.Server2
import net.zomis.games.server2.clients.ur.WSClient
import net.zomis.games.server2.clients.ur.getInt
import net.zomis.games.server2.clients.ur.getText
import net.zomis.games.server2.doctools.DocEventSystem
import net.zomis.games.server2.doctools.DocWriter
import net.zomis.games.server2.testDocWriter
import net.zomis.games.server2.testServerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URI

/**
 * Tests a generalized Entity-Component-System game to make sure that:
 * - The full game state is sent to both players (game, 2D-grid, 2D-grid, tile)
 * --- reason for this is a flexible component-based frontend-client
 * --- this will also send out an entity id for each entity, which will be used for performing actions
 * - It's possible to perform actions
 * - You get notified about updates on Entities
 * - You get notified when you win/lose the game
 * - You get notified when the game has ended
 */
class ECSGameTest {

    data class ECSAction(val performerId: String, val actionableId: String)

    private lateinit var server: Server2

    val GAMETYPE = "UTTT-ECS"

    @RegisterExtension
    @JvmField
    val docWriter: DocWriter = testDocWriter()

    val config = testServerConfig()

    @BeforeEach
    fun test() {
        // Maybe make the server also an extension one day
        server = Server2(DocEventSystem(docWriter))
        server.start(config)
    }

    @AfterEach
    fun stopServer() {
        server.stop()
    }

    private lateinit var player1_id: String
    private lateinit var player2_id: String

    @Test
    fun uttt() {
        val p1 = WSClient(URI("ws://127.0.0.1:${config.wsport}"))
        p1.connectBlocking()

        val p2 = WSClient(URI("ws://127.0.0.1:${config.wsport}"))
        p2.connectBlocking()

        p1.send("""v1:{ "game": "$GAMETYPE", "type": "matchMake" }""")
        Thread.sleep(100)
        p2.send("""v1:{ "game": "$GAMETYPE", "type": "matchMake" }""")
        p1.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == GAMETYPE &&
                    it.getInt("yourIndex") == 0
        }
        p2.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == GAMETYPE &&
                    it.getInt("yourIndex") == 1
        }

        println("Waiting for game data")

        val data = p1.expectJsonObject {
            it.getText("type") == "GameData"
        }
        p2.expectJsonObject { it.getText("type") == "GameData" }
        player1_id = data["game"]["players"][0]["id"].asText()
        player2_id = data["game"]["players"][1]["id"].asText()
        println("Found player ids: $player1_id and $player2_id")

        // Play some moves to make first player get the bottom left board
        sendAndExpect(data, p1, p2, listOf(
            ECSAction(player1_id, data["game"]["grid"][0][2]["grid"][1][0]["id"].asText()),
            ECSAction(player2_id, data["game"]["grid"][1][0]["grid"][0][2]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][0][2]["grid"][1][1]["id"].asText()),
            ECSAction(player2_id, data["game"]["grid"][1][1]["grid"][0][2]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][0][2]["grid"][1][2]["id"].asText())
        ))

        println("Make sure that board 0 2 is won (bottom left)")
        p1.expectJsonObject {
            //  { type: Update, id: xyz, component: { type: owner, value: 0 } }
            it.getText("type") == "Update" && it.getText("id") == data["game"]["grid"][0][2]["grid"]["id"].asText() &&
                it["component"]["type"].asText() == "owner" && it["component"]["value"].asInt() == 0
        }

        println("Prepare for winning")
        sendAndExpect(data, p1, p2, listOf(
            ECSAction(player1_id, data["game"]["grid"][1][2]["grid"][0][1]["id"].asText()), // TODO: Fix the values for winning
            ECSAction(player2_id, data["game"]["grid"][0][1]["grid"][2][2]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][2][2]["grid"][0][1]["id"].asText()),
            ECSAction(player2_id, data["game"]["grid"][0][1]["grid"][2][1]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][2][1]["grid"][0][1]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][0][1]["grid"][2][0]["id"].asText()),

            ECSAction(player1_id, data["game"]["grid"][2][0]["grid"][0][2]["id"].asText()), // Play anywhere after this
            ECSAction(player1_id, data["game"]["grid"][0][0]["grid"][0][0]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][0][0]["grid"][0][1]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][0][0]["grid"][1][1]["id"].asText()),
            ECSAction(player1_id, data["game"]["grid"][1][1]["grid"][0][0]["id"].asText())
        ))
        // Win the game
        val finalId = data["game"]["grid"][0][0]["grid"][2][2]["id"].asText()
        p1.sendAndExpectResponse("""v1:{ "game": "$GAMETYPE", "gameId": "1", "type": "action", "performer": "$player1_id", "action": "$finalId" }""")
        var obj = p1.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == GAMETYPE)
        assert(obj.getInt("player") == 0)
        assert(obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 1)

        obj = p1.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == GAMETYPE)
        assert(obj.getInt("player") == 1)
        assert(!obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 2)

        obj = p2.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == GAMETYPE)
        assert(obj.getInt("player") == 0)
        assert(obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 1)

        obj = p2.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == GAMETYPE)
        assert(obj.getInt("player") == 1)
        assert(!obj.get("winner").asBoolean())
        assert(obj.getInt("position") == 2)

        p1.close()
        p2.close()
    }

    private fun sendAndExpect(data: ObjectNode, p1: WSClient, p2: WSClient, pairs: List<ECSAction>) {
        pairs.forEach({ action ->
            val cl = if (action.performerId == this.player1_id) p1 else p2
            cl.send("""v1:{ "game": "$GAMETYPE", "gameId": "1", "type": "action", "performer": "${action.performerId}", "action": "${action.actionableId}" }""")
//            p1.expectJsonObject {
//                it.getText("type") == "GameMoveRequest" // or whatever it is called
//            }
//            p2.expectJsonObject {
//                it.getText("type") == "GameMoveRequest" // or whatever it is called
//            }

            val expectedOwner = if (action.performerId == this.player1_id) 0 else 1

            // Effects of the move
            val updateOwner: (ObjectNode) -> Boolean = {
                it.getText("type") == "Update" && it.getText("id") == action.actionableId &&
                        it["component"]["type"].asText() == "owner" && it["component"]["value"].asInt() == expectedOwner
            }
            val updatePlayer: (ObjectNode) -> Boolean = {
                it.getText("type") == "Update" && it.getText("id") == data["game"]["id"].asText() &&
                        it["component"]["type"].asText() == "currentPlayer" && it["component"]["value"].asInt() != expectedOwner
            }
            val updateDestination: (ObjectNode) -> Boolean = {
                it.getText("type") == "Update" && it.getText("id") == data["game"]["id"].asText() &&
                        it["component"]["type"].asText() == "activeBoard"
            }
            p1.expectJsonObject(updateOwner)
            p2.expectJsonObject(updateOwner)

            p1.expectJsonObject(updatePlayer)
            p2.expectJsonObject(updatePlayer)

            p1.expectJsonObject(updateDestination)
            p2.expectJsonObject(updateDestination)
        })
    }

}
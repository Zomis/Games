package net.zomis.games.server2

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.ecs.UTTT
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.debug.AIGames
import net.zomis.games.server2.games.GameListSystem
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.ObserverSystem
import net.zomis.games.server2.games.SimpleMatchMakingSystem
import net.zomis.games.server2.games.impl.ECSGameSystem
import net.zomis.games.server2.games.impl.RoyalGameOfUrSystem
import net.zomis.games.server2.games.impl.TTControllerSystem
import net.zomis.games.server2.invites.InviteSystem
import net.zomis.games.server2.invites.LobbySystem
import net.zomis.games.server2.javalin.auth.LinAuth
import net.zomis.games.server2.ws.Server2WS
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.games.TTClassicControllerWithGravity
import net.zomis.tttultimate.games.TTUltimateController
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import javax.script.ScriptEngineManager

fun JsonNode.getTextOrDefault(fieldName: String, default: String): String {
    return if (this.hasNonNull(fieldName)) this.get(fieldName).asText() else default
}

data class IllegalClientRequest(val client: Client, val error: String)

class ServerConfig {

    @Parameter(names = ["-wsport"], description = "Port number for WebSockets")
    var wsport: Int = 8081

    @Parameter(names = ["-httpPort"], description = "Port number for Authentication REST-server (0 to disable)")
    var httpPort: Int = 0

}

/*
* Server2 should specify which features it wants to include
* These features should hook themselves into Server2 -- store data and add event listeners
*
* Each feature can add its own state and store that in Server2 <-- also helps with debugging, as SimpleMatchMakingSystem is not accessible from everywhere in the code
* Features should rely on listening to and executing events for messaging/performing stuff
*
* Some features require data from other features - in particular the GameFeature
* Not all classes require dynamic data, primarily: Server, GameType, Game, Player, PlayerInGame
*
*/
class Server2(val events: EventSystem) {
    private val logger = KLoggers.logger(this)
    private val mapper = ObjectMapper()
    val features = Features(events)

    fun start(config: ServerConfig) {
        Runtime.getRuntime().addShutdownHook(Thread({ events.execute(ShutdownEvent("runtime shutdown hook")) }))
        logger.info("$this has features $features")
        events.listen("Start WebSocket Server", StartupEvent::class, {true}, {
            val ws = Server2WS(events, InetSocketAddress(config.wsport)).setup()
            logger.info("WebSocket server listening at ${ws.port}")
        })

        events.listen("v1: JsonMessage", ClientMessage::class, {it.message.startsWith("v1:")}, {
            events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message.substring("v1:".length))))
        })
        events.listen("JSON Message", ClientMessage::class, {
            it.message.trimStart().startsWith("{")
        }, {
            events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message)))
        })

        features.add(GameSystem()::setup)

        TTControllerSystem("Connect4", {TTClassicControllerWithGravity(TTFactories().classicMNK(7, 6, 4))}).register(events)
        TTControllerSystem("UTTT", {TTUltimateController(TTFactories().ultimate())}).register(events)
        RoyalGameOfUrSystem.init(events)
        features.add(ECSGameSystem("UTTT-ECS", { UTTT().setup() })::setup)

        features.add(SimpleMatchMakingSystem()::setup)
        events.with(ServerConsole()::register)
        features.add(ObserverSystem()::setup)
        features.add(GameListSystem()::setup)
        events.with(AuthorizationSystem()::register)
        features.add(LobbySystem()::setup)
        val executor = Executors.newScheduledThreadPool(2)
        events.with({ e -> ServerAIs().register(e, executor) })
        features.add(InviteSystem()::setup)
        if (config.httpPort != 0) {
            events.with(LinAuth(config.httpPort)::register)
        }
        features.add(AIGames()::setup)

        val engine = ScriptEngineManager().getEngineByExtension("kts")!!
        events.listen("Kotlin script", ConsoleEvent::class, {it.input.startsWith("kt ")}, {
            val result = engine.eval(it.input.substring("kt ".length))
            println(result)
        })
        events.execute(StartupEvent(System.currentTimeMillis()))
    }

    fun stop() {
        events.execute(ShutdownEvent("stop called"))
    }

}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = ServerConfig()
        val cmd = JCommander(config)
        try {
            cmd.parse(*args)
        } catch (e: ParameterException) {
            cmd.usage()
            System.exit(1)
        }

        // TODO: Run with auto-docs to print event chains.
        Server2(EventSystem()).start(config)
    }
}

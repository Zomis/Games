package net.zomis.games.server2

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import klogging.KLoggers
import net.zomis.core.events.EventHandler
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.games.GameListSystem
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.ObserverSystem
import net.zomis.games.server2.games.SimpleMatchMakingSystem
import net.zomis.games.server2.games.impl.Connect4
import net.zomis.games.server2.games.impl.RoyalGameOfUrSystem
import net.zomis.games.server2.invites.InviteSystem
import net.zomis.games.server2.invites.LobbySystem
import net.zomis.games.server2.javalin.auth.LinAuth
import net.zomis.games.server2.ws.Server2WS
import java.net.InetSocketAddress
import kotlin.reflect.KClass

fun JsonNode.getTextOrDefault(fieldName: String, default: String): String {
    return if (this.hasNonNull(fieldName)) this.get(fieldName).asText() else default
}

data class IllegalClientRequest(val client: Client, val error: String)

class ServerConfig {

    @Parameter(names = ["-wsport"], description = "Port number for WebSockets")
    var wsport: Int = 8081

    @Parameter(names = ["-httpPort"], description = "Port number for REST-server (0 to disable)")
    var httpPort: Int = 42638

}

class Server2 {
    private val logger = KLoggers.logger(this)
    private val events = EventSystem()
    private val mapper = ObjectMapper()

    fun start(config: ServerConfig) {
        Runtime.getRuntime().addShutdownHook(Thread({ events.execute(ShutdownEvent()) }))
        events.addListener(StartupEvent::class, {
            val ws = Server2WS(events, InetSocketAddress(config.wsport)).setup()
            logger.info("WebSocket server listening at ${ws.port}")
        })

        events.addListener(ClientMessage::class, {
            if (it.message.startsWith("v1:")) {
                events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message.substring("v1:".length))))
            }
        })
        events.addListener(ClientMessage::class, {
            if (it.message.trimStart().startsWith("{")) {
                events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message)))
            }
        })
        val gameSystem = GameSystem(events)

        Connect4.init(events)
        RoyalGameOfUrSystem.init(events)

        SimpleMatchMakingSystem(gameSystem, events)
        events.with(ServerConsole()::register)
        events.with(ObserverSystem(events, gameSystem)::register)
        events.with(GameListSystem(gameSystem)::register)
        events.with(AuthorizationSystem()::register)
        events.with(LobbySystem()::register)
        val clientsByName = ClientsByName()
        events.with(ClientsByName()::register)
        events.with { InviteSystem(gameSystem).register(it, clientsByName) }
        if (config.httpPort != 0) {
            events.with(LinAuth(config.httpPort)::register)
        }
        events.execute(StartupEvent())
    }

    fun <E : Any> register(clazz: KClass<E>, handler: EventHandler<E>) {
        return events.addListener(clazz, handler)
    }

    fun stop() {
        events.execute(ShutdownEvent())
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

        Server2().start(config)
    }
}

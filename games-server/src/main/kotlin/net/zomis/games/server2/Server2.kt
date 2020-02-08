package net.zomis.games.server2

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.dsl.DslTTT
import net.zomis.games.dsl.DslTTT3D
import net.zomis.games.dsl.DslUR
import net.zomis.games.dsl.GameSpec
import net.zomis.games.ecs.UTTT
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.ais.TTTQLearn
import net.zomis.games.server2.db.DBIntegration
import net.zomis.games.server2.debug.AIGames
import net.zomis.games.server2.games.*
import net.zomis.games.server2.games.impl.ECSGameSystem
import net.zomis.games.server2.handlers.games.ActionListRequestHandler
import net.zomis.games.server2.handlers.games.ViewRequestHandler
import net.zomis.games.server2.invites.InviteSystem
import net.zomis.games.server2.invites.LobbySystem
import net.zomis.games.server2.javalin.auth.JavalinFactory
import net.zomis.games.server2.javalin.auth.LinAuth
import net.zomis.games.server2.ws.Server2WS
import net.zomis.games.server2.ws.WebsocketMessageHandler
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import javax.script.ScriptEngineManager

fun JsonNode.getTextOrDefault(fieldName: String, default: String): String {
    return if (this.hasNonNull(fieldName)) this.get(fieldName).asText() else default
}

data class IllegalClientRequest(val client: Client, val error: String)
data class OAuthConfig(val clientId: String, val clientSecret: String)

class ServerConfig {
    fun githubConfig(): OAuthConfig {
        return OAuthConfig(this.githubClient, this.githubSecret)
    }

    @Parameter(names = arrayOf("-wsPort"), description = "Port for websockets and API")
    var webSocketPort = 8081

    @Parameter(names = ["-db"], description = "Use database")
    var database = false

    @Parameter(names = arrayOf("-wsPortSSL"), description = "Port for websockets and API with SSL (only used if certificate options are set)")
    var webSocketPortSSL = 0

    @Parameter(names = arrayOf("-certificate"), description = "Path to Let's Encrypt certificate. Leave empty if none.")
    var certificatePath: String? = null

    @Parameter(names = arrayOf("-keypassword"), description = "Password for Java keystore for certificate")
    var certificatePassword: String? = null

    @Parameter(names = ["-githubClient"], description = "Github Client Id")
    var githubClient: String = ""

    @Parameter(names = ["-githubSecret"], description = "Github Client Secret")
    var githubSecret: String = ""

    var idGenerator: GameIdGenerator = { UUID.randomUUID().toString() }

    fun useSecureWebsockets(): Boolean {
        return certificatePath != null
    }
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

    private val dslGames = ServerGames.games
    val gameSystem = GameSystem()
    private val messageHandler = MessageHandler(events, mapOf(
        "ActionListRequest" to ActionListRequestHandler(gameSystem),
        "ViewRequest" to ViewRequestHandler(gameSystem)
    ))

    fun start(config: ServerConfig): Server2 {
        val javalin = JavalinFactory.javalin(config)
            .enableCorsForOrigin("http://localhost:8080", "https://games.zomis.net")
        logger.info("Configuring Javalin at port ${config.webSocketPort} (SSL ${config.webSocketPortSSL})")

        Runtime.getRuntime().addShutdownHook(Thread { events.execute(ShutdownEvent("runtime shutdown hook")) })
        logger.info("$this has features $features")
        Server2WS(javalin, messageHandler).setup()

        events.listen("JSON Message", ClientMessage::class, {
            it.message.trimStart().startsWith("{")
        }, {
            events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message)))
        })

        features.add { feat, ev -> gameSystem.setup(feat, ev, config.idGenerator) }

        features.add(ECSGameSystem("UTTT-ECS") { UTTT().setup() }::setup)
        dslGames.forEach { name, spec -> events.with(DslGameSystem(name, spec as GameSpec<Any>)::setup) }

        features.add(SimpleMatchMakingSystem()::setup)
        events.with(ServerConsole()::register)
        features.add(ObserverSystem()::setup)
        features.add(GameListSystem()::setup)
        events.with(AuthorizationSystem()::register)
        features.add(LobbySystem()::setup)
        val executor = Executors.newScheduledThreadPool(2)
        events.with { e -> ServerAIs(dslGames.keys.toSet()).register(e, executor) }
        features.add(InviteSystem()::setup)
        if (config.githubClient.isNotEmpty()) {
            LinAuth(javalin, config.githubConfig()).register()
        }
        if (config.database) {
            val dbIntegration = DBIntegration()
            events.with(dbIntegration::register)
            LinReplay(dbIntegration, dslGames).setup(javalin)
        }
        features.add(AIGames()::setup)
        features.add(TVSystem()::register)

        val engine = ScriptEngineManager().getEngineByExtension("kts")!!
        events.listen("Kotlin script", ConsoleEvent::class, {it.input.startsWith("kt ")}, {
            val result = engine.eval(it.input.substring("kt ".length))
            println(result)
        })
        events.with(TTTQLearn(gameSystem)::setup)

        events.listen("Stop Javalin", ShutdownEvent::class, {true}, {javalin.stop()})
        events.listen("Start Javalin", StartupEvent::class, {true}, {javalin.start()})

        events.execute(StartupEvent(System.currentTimeMillis()))
        return this
    }

    fun stop() {
        events.execute(ShutdownEvent("stop called"))
    }

}

typealias IncomingMessageHandler = (ClientJsonMessage) -> Unit

class MessageHandler(private val backup: EventSystem, private val handlers: Map<String, IncomingMessageHandler>): WebsocketMessageHandler {
    private val mapper = ObjectMapper()

    override fun connected(client: Client) {
        backup.execute(ClientConnected(client))
    }

    override fun disconnected(client: Client) {
        backup.execute(ClientDisconnected(client))
    }

    override fun incomingMessage(client: Client, message: String) {
        val jsonMessage = mapper.readTree(message)
        val handler = handlers[jsonMessage.getTextOrDefault("type", "")]
        handler?.invoke(ClientJsonMessage(client, jsonMessage))
        backup.execute(ClientMessage(client, message))
    }

}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val logger = KLoggers.logger(this)
        val config = ServerConfig()
        val cmd = JCommander(config)
        val configFile = File("server2.conf")
        try {
            if (configFile.exists()) {
                logger.info("Using config file $configFile")
                val fileArgs = configFile.readLines(Charsets.UTF_8).joinToString(" ")
                    .trim().split(" ").toTypedArray()
                cmd.parse(*fileArgs)
            } else {
                logger.info("Using config from command line")
                cmd.parse(*args)
            }
        } catch (e: ParameterException) {
            logger.error(e, "Unable to parse config")
            cmd.usage()
            System.exit(1)
        }

        Server2(EventSystem()).start(config)
    }
}

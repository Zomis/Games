package net.zomis.games.server2

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.dsl.GameSpec
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.ais.TTTQLearn
import net.zomis.games.server2.db.DBIntegration
import net.zomis.games.server2.db.aurora.LinStats
import net.zomis.games.server2.db.aurora.StatsDB
import net.zomis.games.server2.debug.AIGames
import net.zomis.games.server2.games.*
import net.zomis.games.server2.invites.InviteOptions
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
    fun googleConfig(): OAuthConfig {
        return OAuthConfig(this.googleClientId, this.googleClientSecret)
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

    @Parameter(names = ["-googleClientId"], description = "Google OAuth Client Id")
    var googleClientId: String = ""

    @Parameter(names = ["-googleClientSecret"], description = "Google OAuth Client Secret")
    var googleClientSecret: String = ""

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

    private val uuidGenerator: () -> String = { UUID.randomUUID().toString() }
    private val dslGames = ServerGames.games
    private val lobbySystem = LobbySystem(features)
    private val gameCallback = GameCallback(
        gameLoader = { gameId -> dbIntegration?.loadGame(gameId) },
        moveHandler = { events.execute(it) }
    )
    val gameSystem = GameSystem(lobbySystem::gameClients, gameCallback)
    var dbIntegration: DBIntegration? = null

    val messageRouter = MessageRouter(this)
        .route("games", gameSystem.router)

    private val messageHandler = MessageHandler(events, this.messageRouter)

    fun start(config: ServerConfig): Server2 {
        val javalin = JavalinFactory.javalin(config)
            .enableCorsForOrigin("http://localhost:8080", "https://games.zomis.net")
        javalin.get("/ping") { ctx -> ctx.result("pong") }
        logger.info("Configuring Javalin at port ${config.webSocketPort} (SSL ${config.webSocketPortSSL})")

        Runtime.getRuntime().addShutdownHook(Thread { events.execute(ShutdownEvent("runtime shutdown hook")) })
        logger.info("$this has features $features")
        Server2WS(javalin, messageHandler).setup()

        events.listen("JSON Message", ClientMessage::class, {
            it.message.trimStart().startsWith("{")
        }, {
            events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message)))
        })

        features.add { feat, ev -> gameSystem.setup(feat, ev, config.idGenerator) { dbIntegration } }

        dslGames.values.forEach { spec ->
            val dslGameSystem = DslGameSystem(spec as GameSpec<Any>) { dbIntegration }
            events.with(dslGameSystem::setup)
        }

        features.add(SimpleMatchMakingSystem()::setup)
        events.with(ServerConsole()::register)
        features.add(GameListSystem()::setup)
        events.listen("Route", ClientJsonMessage::class, {it.data.has("route")}) {
            this.messageRouter.handle(it.data["route"].asText(), it)
        }
        val executor = Executors.newScheduledThreadPool(2)
        if (config.githubClient.isNotEmpty()) {
            LinAuth(javalin, config.githubConfig(), config.googleConfig()).register()
        }
        val aiRepository = AIRepository()
        if (config.database) {
            val dbIntegration = DBIntegration()
            this.dbIntegration = dbIntegration
            features.add(dbIntegration::register)
            LinReplay(aiRepository, dbIntegration).setup(javalin)
            LinStats(StatsDB(dbIntegration.superTable)).setup(events, javalin)
        }
        val authCallback = AuthorizationCallback { dbIntegration?.superTable?.cookieAuth(it) }
        messageRouter.route("auth", AuthorizationSystem(events, authCallback).router)

        events.with(lobbySystem::setup)
        messageRouter.route("lobby", lobbySystem.router)
        features.add(AIGames(lobbySystem::gameClients)::setup)
        features.add(TVSystem(lobbySystem::gameClients)::register)
        fun createGameCallback(gameType: String, options: InviteOptions): ServerGame
            = gameSystem.getGameType(gameType)!!.createGame(options)
        messageRouter.route("invites", InviteSystem(
            gameClients = lobbySystem::gameClients,
            createGameCallback = ::createGameCallback,
            startGameExecutor = { events.execute(it) },
            inviteIdGenerator = uuidGenerator
        ).router)

        events.with { e -> ServerAIs(aiRepository, dslGames.keys.toSet()).register(e, executor) }

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

class MessageHandler<T>(private val backup: EventSystem, router: MessageRouter<T>): WebsocketMessageHandler {
    private val mapper = ObjectMapper()

    override fun connected(client: Client) {
        backup.execute(ClientConnected(client))
    }

    override fun disconnected(client: Client) {
        backup.execute(ClientDisconnected(client))
    }

    override fun incomingMessage(client: Client, message: String) {
        val jsonMessage = mapper.readTree(message)
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
                logger.info("${configFile.name} not found, using config from command line")
                cmd.parse(*args)
            }
        } catch (e: ParameterException) {
            logger.error(e, "Unable to parse config")
            cmd.usage()
            System.exit(1)
        }

        try {
            Server2(EventSystem()).start(config)
        } catch (e: Exception) {
            logger.error(e) { "Unable to start server" }
            System.exit(2)
        }
    }
}

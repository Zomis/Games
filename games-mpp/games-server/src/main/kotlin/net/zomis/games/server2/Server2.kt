package net.zomis.games.server2

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson.*
import klog.KLoggers
import net.zomis.common.substr
import net.zomis.core.events.EventSystem
import net.zomis.games.Features
import net.zomis.games.dsl.GameSpec
import net.zomis.games.ktor.KtorApplication
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.TTTQLearn
import net.zomis.games.server2.db.DBIntegration
import net.zomis.games.server2.db.DBInterface
import net.zomis.games.server2.db.files.FileDB
import net.zomis.games.server2.debug.AIGames
import net.zomis.games.server2.games.*
import net.zomis.games.server2.invites.InviteOptions
import net.zomis.games.server2.invites.InviteSystem
import net.zomis.games.server2.invites.LobbySystem
import net.zomis.games.server2.ws.WebsocketMessageHandler
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import java.io.File
import java.util.UUID
import kotlin.io.path.Path
import kotlin.system.exitProcess

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

    var wait: Boolean = true

    @Parameter(names = ["-port"], description = "Port for websockets and API")
    var port = 8081

    @Parameter(names = ["-db"], description = "Use database (has priority over -dbfs")
    var database = false

    @Parameter(names = ["-dbfs"], description = "Use file database")
    var databaseFiles = false

    @Parameter(names = ["-statsDB"], description = "Use statistics database (requires database as well)")
    var statsDB = false

    @Parameter(names = ["-githubClient"], description = "Github Client Id")
    var githubClient: String = ""

    @Parameter(names = ["-githubSecret"], description = "Github Client Secret")
    var githubSecret: String = ""

    @Parameter(names = ["-googleClientId"], description = "Google OAuth Client Id")
    var googleClientId: String = ""

    @Parameter(names = ["-googleClientSecret"], description = "Google OAuth Client Secret")
    var googleClientSecret: String = ""

    @Parameter(names = ["-clients"], description = "Client URLs, can take multiple values separated by semicolon ';'")
    var clientURLs = "localhost:8080;games.zomis.net"

    var idGenerator: GameIdGenerator = { UUID.randomUUID().toString() }

    fun useOAuth(): Boolean = this.githubClient.isNotEmpty() || this.googleClientId.isNotEmpty()
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
    var dbIntegration: DBInterface? = null

    val messageRouter = MessageRouter(this)
        .route("games", gameSystem.router)

    private val messageHandler = MessageHandler(events, this.messageRouter)
    private val httpClientFactory: () -> HttpClient = {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson()
            }
            install(Logging)
        }
    }

    fun start(config: ServerConfig): Server2 {
        Runtime.getRuntime().addShutdownHook(Thread { events.execute(ShutdownEvent("runtime shutdown hook")) })
        logger.info("$this has features $features")

        events.listen("JSON Message", ClientMessage::class, {
            it.message.trimStart().startsWith("{")
        }, {
            events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message)))
        })

        features.add { feat, ev -> gameSystem.setup(feat, ev, config.idGenerator) }

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
        if (config.database) {
            val dbIntegration = DBIntegration()
            this.dbIntegration = dbIntegration.also { it.createTables() }
            if (config.statsDB) {
//                LinStats(StatsDB(dbIntegration.superTable)).setup(events, javalin)
            }
        } else if (config.databaseFiles) {
            val dbIntegration = FileDB().also { this.dbIntegration = it }
        }
        val authCallback = AuthorizationCallback { dbIntegration?.cookieAuth(it) }
        messageRouter.route("auth", AuthorizationSystem(events, httpClientFactory, authCallback).router)
        events.listen("Authenticate login with dbIntegration", ClientLoginEvent::class, {true}) { dbIntegration?.authenticate(it) }

        events.with(lobbySystem::setup)
        messageRouter.route("lobby", lobbySystem.router)
        features.add(AIGames(lobbySystem::gameClients)::setup)
        features.add(TVSystem(lobbySystem::gameClients)::register)
        fun createGameCallback(gameType: String, options: InviteOptions): ServerGame
            = gameSystem.getGameType(gameType)!!.createGame(options)

        val inviteSystem = InviteSystem(
            gameClients = lobbySystem::gameClients,
            createGameCallback = ::createGameCallback,
            startGameExecutor = { events.execute(it) },
            inviteIdGenerator = uuidGenerator
        )
        messageRouter.route("invites", inviteSystem.router)
        messageRouter.route("testGames", TestGamesRoute(inviteSystem).router)

        val kotlinScriptEngineFactory = KotlinJsr223JvmLocalScriptEngineFactory()
        events.listen("Kotlin script", ConsoleEvent::class, {it.input.startsWith("kt ")}, {
            val jarFile = "games-1.0-SNAPSHOT-all.jar"
            if (File(jarFile).exists()) {
                System.setProperty("kotlin.script.classpath", jarFile)
            }
            val script = it.input.substring("kt ".length)
            val result = kotlinScriptEngineFactory.scriptEngine.eval(script)
            println(result)
        })

        events.with(TTTQLearn(Path("db/QLearn-ttt.json"))::setup)
        events.execute(StartupEvent(System.currentTimeMillis()))
        AIRepository.createAIs(events, dslGames.values.map { it as GameSpec<Any> })
        val app = KtorApplication(messageHandler).main(config, dbIntegration, httpClientFactory)
        events.listen("Stop App", ShutdownEvent::class, {true}, {
            app.stop()
        })
        return this
    }

    fun stop() {
        events.execute(ShutdownEvent("stop called"))
    }

}

class MessageHandler<T>(private val backup: EventSystem, router: MessageRouter<T>): WebsocketMessageHandler {
    override fun connected(client: Client) {
        backup.execute(ClientConnected(client))
    }

    override fun disconnected(client: Client) {
        backup.execute(ClientDisconnected(client))
    }

    override fun incomingMessage(client: Client, message: String) {
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
                logger.info("Args (with substring 0..6) are ${fileArgs.map { it.substr(0, 6) }}")
                cmd.parse(*fileArgs)
            } else {
                logger.info("${configFile.name} not found, using config from command line")
                cmd.parse(*args)
            }
        } catch (e: ParameterException) {
            logger.error(e, "Unable to parse config")
            cmd.usage()
            exitProcess(1)
        }

        try {
            Server2(EventSystem()).start(config)
        } catch (e: Exception) {
            logger.error(e) { "Unable to start server" }
            exitProcess(2)
        }
    }
}

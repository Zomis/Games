package net.zomis.games.server2.games

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import net.zomis.core.events.EventSystem
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.*
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.dsl.listeners.CombinedListener
import net.zomis.games.listeners.NoOpListener
import net.zomis.games.listeners.ReplayListener
import net.zomis.games.server.GamesServer
import net.zomis.games.server2.StartupEvent
import net.zomis.games.server2.ais.AIDebugListener
import net.zomis.games.server2.db.DBGame
import net.zomis.games.server2.db.DBInterface
import java.lang.UnsupportedOperationException
import kotlin.reflect.full.cast

class DslGameSystem<T : Any>(val dsl: GameSpec<T>, private val dbIntegration: () -> DBInterface?) {
    private val gameTypeName = dsl.name

    private val mapper = jacksonObjectMapper()
    private val logger = KLoggers.logger(this)

    fun perform(events: EventSystem, it: PlayerGameMoveRequest) {
        val serverGame = it.game
        val game = serverGame.obj!! as Game<T>
        if (game.isGameOver()) {
            events.execute(it.illegalMove("Game already finished"))
            return
        }

        val actionType = game.actions.type(it.moveType)
        if (actionType == null) {
            events.execute(it.illegalMove("No such actionType: ${it.moveType}"))
            return
        }

        val action: Actionable<T, Any> = parseAction(actionType, it) ?: return
        if (!actionType.isAllowed(action)) {
            events.execute(it.illegalMove("Action is not allowed"))
            return
        }

        serverGame.coroutineScope.launch {
            try {
                game.actionsInput.send(action)
            } catch (e: Exception) {
                logger.error(e) { "Error in DSL System Coroutine: $it $game $action" }
            }
        }
    }

    private fun parseAction(actionType: ActionTypeImplEntry<T, Any>, it: PlayerGameMoveRequest): Actionable<T, Any>? {
        try {
            return if (actionType.parameterClass == Unit::class) {
                actionType.createAction(it.player, Unit)
            } else {
                if (actionType.actionType.parameterType.isInstance(it.move)) {
                    actionType.createAction(it.player, actionType.actionType.parameterType.cast(it.move))
                } else if (it.serialized) {
                    // it.move is a JsonNode
                    val serializedMove = mapper.convertValue(it.move, actionType.actionType.serializedType.java)
                    actionType.createActionFromSerialized(it.player, serializedMove)
                } else {
                    throw UnsupportedOperationException("Unknown object of type " + it.move.javaClass + " serialized? " + it.serialized)
                }
            }
        } catch (e: Exception) {
            logger.error(e, "Error reading move: $it")
            return null
        }
    }

    fun setup(events: EventSystem) {
        val entryPoint = GamesImpl.game(dsl)
        events.listen("DslGameSystem $gameTypeName Resume", GameResumedEvent::class, { it.game.gameType.type == gameTypeName }, { gameEvent ->
            val serverGame = gameEvent.game
            val dbIntegration = this.dbIntegration()

            serverGame.coroutineScope.launch {
                logger.info { "Creating game: ${gameEvent.game}" }
                val replayListener = ReplayListener(serverGame.gameType.type)
                val appropriateReplayListener =
                    if (serverGame.gameMeta.database && dbIntegration != null) {
                        dbIntegration.gameListener(serverGame, replayListener)
                    } else GamesImpl.listeners.noop

                val playerListeners = serverGame.players.mapValues { playerEntry ->
                    playerEntry.value.access.filter { it.value >= ClientPlayerAccessType.WRITE }.map { it.key }
                }.map { it.key.listenerFactory to it.value }

                fun listeners(game: Game<Any>, replayData: ReplayData?): List<GameListener> {
                    val aiDebug = serverGame.players.mapNotNull { it.key.name }.filter { it.lowercase().endsWith("_dev") }.mapNotNull { aiName ->
                        entryPoint.setup().findAI(aiName)?.let { AIDebugListener(it, game as Game<T>) }
                    }
                    return listOf(
                        DslGameSystemListener(serverGame, events).postReplay(replayData),
                        serverGameListener(serverGame, game).postReplay(replayData)
                    ) + playerListeners.flatMap { playerListener ->
                        playerListener.second.map { playerListener.first.createListener(game, it)?.postReplay(replayData) }
                    }.filterNotNull() + appropriateReplayListener + replayListener + aiDebug
                }

                if (gameEvent.dbGame != null) {
                    val replayData = gameEvent.dbGame.replayData()
                    println("DbGame contains ${replayData.actions}")
                    GamesImpl.game(dsl).replay(serverGame.coroutineScope, replayData, GamesServer.actionConverter) {
                        serverGame.obj = it as Game<Any>
                        listeners(it, replayData)
                    }.goToEnd().awaitCatchUp()
                    gameEvent.game.sendGameReady()
                    ConsoleView<T>().showView(serverGame.obj!! as Game<T>)
                } else {
                    val blocking = BlockingGameListener()
                    entryPoint.setup().startGameWithConfig(this, serverGame.playerCount, serverGame.gameMeta.gameOptions) {game ->
                        logger.info { "Initializing game $game of type ${game.gameType} serverGame ${serverGame.gameId}" }
                        serverGame.obj = game
                        listeners(game, null) + blocking
                    }
                    blocking.await()
                    gameEvent.game.sendGameReady()
                }
                logger.info { "Created game ${serverGame.gameId}" }
            }
        })
        events.listen("DslGameSystem $gameTypeName Setup", GameStartedEvent::class, {it.game.gameType.type == gameTypeName}, {
            events.execute(GameResumedEvent(it.game, null))
        })
        events.listen("DslGameSystem $gameTypeName Move", PlayerGameMoveRequest::class, {
            it.game.gameType.type == gameTypeName
        }, {
            runBlocking {
                it.game.mutex.withLock {
                    perform(events, it)
                }
            }
        })
        events.listen("DslGameSystem register $gameTypeName", StartupEvent::class, {true}, {
            events.execute(GameTypeRegisterEvent(dsl))
        })
    }

    private fun serverGameListener(serverGame: ServerGame, game: Game<Any>): GameListener {
        val performed = mutableListOf<Any>()
        return object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                when (step) {
                    is FlowStep.GameSetup<*> -> {
                        serverGame.broadcast {
                            serverGame.toJson("UpdateView")
                        }
                    }
                    is FlowStep.ProceedStep -> {
                        performed.forEach { e ->
                            serverGame.broadcast { e }
                        }
                        performed.clear()
                    }
                    is FlowStep.Log -> sendLogs(serverGame, step.log)
                    is FlowStep.ActionPerformed<*> -> {
                        performed.add(step.moveMessage(serverGame))
                    }
                    is FlowStep.Elimination -> {
                        serverGame.broadcast {
                            step.eliminatedMessage(serverGame)
                        }
                    }
                    else -> {}
                }
                if (step is FlowStep.GameEnd) { // needs to be separate because of ProceedStep being checked
                    serverGame.gameOver = true
                    serverGame.broadcast {
                        serverGame.toJson("GameEnded")
                    }
                }
            }
        }
    }
    private fun FlowStep.ActionPerformed<*>.moveMessage(serverGame: ServerGame): Map<String, Any?> {
        return serverGame.toJson("GameMove")
            .plus("player" to this.playerIndex)
            .plus("moveType" to this.action.actionType)
    }

    private fun FlowStep.Elimination.eliminatedMessage(serverGame: ServerGame): Map<String, Any?> {
        return serverGame.toJson("PlayerEliminated")
            .plus("player" to this.elimination.playerIndex)
            .plus("winner" to this.elimination.winResult.isWinner())
            .plus("winResult" to this.elimination.winResult.name)
            .plus("position" to this.elimination.position)
    }

    private fun sendLogs(serverGame: ServerGame, log: ActionLogEntry) {
        val yourLog = logEntryMessage(serverGame, log.secret ?: log.public)
        val othersLog = logEntryMessage(serverGame, log.public)
        serverGame.players.forEach { (client, access) ->
            val msg = if (access.index(log.playerIndex) >= ClientPlayerAccessType.READ) yourLog else othersLog
            msg?.let { client.send(it) }
        }
    }

    private fun logEntryMessage(serverGame: ServerGame, entry: LogEntry?): Map<String, Any>? {
        if (entry == null) return null
        return mapOf(
            "type" to "ActionLog",
            "gameType" to serverGame.gameType.type,
            "gameId" to serverGame.gameId,
            "private" to entry.private,
            "parts" to entry.parts
        )
    }

    class DslGameSystemListener(private val serverGame: ServerGame, private val events: EventSystem): GameListener {
        override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            if (step is FlowStep.GameEnd) events.execute(GameEndedEvent(serverGame))
        }
    }

}
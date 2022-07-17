package net.zomis.games.server2.games

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.*
import net.zomis.games.server.GamesServer
import net.zomis.games.server2.StartupEvent
import net.zomis.games.server2.db.DBIntegration
import java.lang.UnsupportedOperationException
import kotlin.IllegalStateException
import kotlin.reflect.full.cast

class DslGameSystem<T : Any>(val dsl: GameSpec<T>, private val dbIntegration: () -> DBIntegration?) {
    val gameTypeName = dsl.name

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
                events.execute(PreMoveEvent(serverGame, it.player, action.actionType, action.parameter))
                game.actionsInput.send(action)
            } catch (e: Exception) {
                logger.error(e) { "Error in DSL System Coroutine: $it $game $action" }
            }
        }
    }

    private fun handleFeedbacks(feedback: FlowStep, events: EventSystem, game: ServerGame, g: Game<Any>) {
        if (true) {
            logger.info("Feedback received: $feedback")
            when (feedback) {
                is FlowStep.GameEnd -> events.execute(GameEndedEvent(game))
                is FlowStep.ActionPerformed<*> -> events.execute(
                    MoveEvent(game, feedback.playerIndex, feedback.actionImpl.actionType, feedback.parameter, feedback.replayState)
                )
                else -> { /* ignored */ }
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
                val appropriateReplayListener =
                    if (serverGame.gameMeta.database && dbIntegration != null) {
                        val listener = GamesServer.Replays.database(dbIntegration, serverGame)
                        if (gameEvent.dbGame != null) {
                            PostReplayListener(gameEvent.dbGame.replayData(), listener)
                        } else {
                            listener
                        }
                    } else GamesServer.Replays.noReplays()

                val playerListeners = serverGame.players.mapValues { playerEntry ->
                    playerEntry.value.access.filter { it.value >= ClientPlayerAccessType.WRITE }.map { it.key }
                }.map { it.key.listenerFactory to it.value }
                val game = entryPoint.setup().startGameWithConfig(this, serverGame.playerCount, serverGame.gameMeta.gameOptions) {game ->
                    logger.info { "Initializing game $game of type ${game.gameType} serverGame ${serverGame.gameId}" }
                    listOf(
                        DslGameSystemListener(gameEvent.game, events, game),
                        serverGameListener(gameEvent.game, game),
                        appropriateReplayListener
                    ) + playerListeners.flatMap { playerListener ->
                        playerListener.second.map { playerListener.first.createListener(game, it) }
                    }.filterNotNull()
                } as Game<Any>
                serverGame.obj = game
                logger.info { "Created game: $game ${serverGame.gameId}" }
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
                }
                if (step is FlowStep.GameEnd) {
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

    inner class DslGameSystemListener(val serverGame: ServerGame, val events: EventSystem, val game: Game<Any>): GameListener {
        override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            handleFeedbacks(step, events, serverGame, game)
        }
    }

}
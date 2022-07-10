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
        val controller = it.game.obj!! as Game<T>
        if (controller.isGameOver()) {
            events.execute(it.illegalMove("Game already finished"))
            return
        }

        val actionType = controller.actions.type(it.moveType)
        if (actionType == null) {
            events.execute(it.illegalMove("No such actionType: ${it.moveType}"))
            return
        }

        val action: Actionable<T, Any> = parseAction(actionType, it) ?: return
        if (!actionType.isAllowed(action)) {
            events.execute(it.illegalMove("Action is not allowed"))
            return
        }
        handleGameFlow(events, it, controller, action)
    }

    private fun handleGameFlow(
        events: EventSystem, moveRequest: PlayerGameMoveRequest,
        gameFlow: Game<T>,
        action: Actionable<T, Any>
    ) {
        GlobalScope.launch {
            try {
                events.execute(PreMoveEvent(moveRequest.game, moveRequest.player, action.actionType, action.parameter))
                println("sending actionsInput to ${gameFlow.actionsInput}")
                gameFlow.actionsInput.send(action)
                println("Action sent, awaiting feedback")
            } catch (e: Exception) {
                logger.error(e) { "Error in DSL System Coroutine: $moveRequest $gameFlow $action" }
            }
        }
    }

    private fun handleFeedbacks(feedback: FlowStep, events: EventSystem, game: ServerGame, g: Game<Any>) {
        if (true) {
            logger.info("Feedback received: $feedback")
            when (feedback) {
                is FlowStep.GameEnd -> events.execute(GameEndedEvent(game))
                is FlowStep.Elimination -> events.execute(
                    PlayerEliminatedEvent(game, feedback.elimination.playerIndex,
                        feedback.elimination.winResult, feedback.elimination.position
                    ))
                is FlowStep.Log -> sendLogs(game, feedback.log)
                is FlowStep.ActionPerformed<*> -> events.execute(
                    MoveEvent(game, feedback.playerIndex, feedback.actionImpl.actionType, feedback.parameter, feedback.replayState)
                )
                is FlowStep.AwaitInput -> {
                    if (g.eliminations.isGameOver()) {
                        throw IllegalStateException("Game is over but AwaitInput was sent")
                    }
                    return
                }
                is FlowStep.GameSetup<*> -> { /* SuperTable also listens for GameStartedEvent with higher priority */ }
                is FlowStep.RuleExecution -> logger.debug { "Rule Execution: $feedback" }
                is FlowStep.IllegalAction -> logger.error { "Illegal action in feedback: $feedback" }
                is FlowStep.NextView -> logger.debug { "NextView: $feedback" } // TODO: Not implemented yet, should pause a bit and then continue
                is FlowStep.PreMove -> {}
                is FlowStep.PreSetup<*> -> {}
                else -> {
                    logger.warn(IllegalArgumentException("Unsupported feedback: $feedback"))
                }
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
                val game = entryPoint.setup().startGameWithConfig(this, serverGame.playerCount, serverGame.gameMeta.gameOptions) {game ->
                    listOf(
                        DslGameSystemListener(gameEvent.game, events, game),
                        serverGameListener(gameEvent.game, game),
                        appropriateReplayListener
                    )
                } as Game<Any>
                serverGame.obj = game
                logger.info { "Created game: $game" }
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
                    is FlowStep.ActionPerformed<*> -> {
                        performed.add(step.moveMessage(serverGame))
                    }
                    is FlowStep.Elimination -> {
                        serverGame.broadcast {
                            step.eliminatedMessage(serverGame)
                        }
                    }
                    is FlowStep.GameEnd -> {
                        serverGame.gameOver = true
                        serverGame.broadcast { _ ->
                            serverGame.toJson("GameEnded")
                        }
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
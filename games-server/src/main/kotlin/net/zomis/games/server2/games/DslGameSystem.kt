package net.zomis.games.server2.games

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.*
import net.zomis.games.server.GamesServer
import net.zomis.games.server2.StartupEvent
import net.zomis.games.server2.db.DBIntegration
import java.lang.IllegalStateException
import java.lang.UnsupportedOperationException
import kotlin.reflect.full.cast

class DslGameSystem<T : Any>(val dsl: GameSpec<T>, private val dbIntegration: () -> DBIntegration?) {
    val gameTypeName = dsl.name

    private val mapper = jacksonObjectMapper()
    private val logger = KLoggers.logger(this)

    fun perform(events: EventSystem, it: PlayerGameMoveRequest) {
        val serverGame = it.game
        val controller = it.game.obj!!.game as GameImpl<T>
        if (controller.isGameOver()) {
            events.execute(it.illegalMove("Game already finished"))
            return
        }

        val actionType = controller.actions.type(it.moveType)
        if (actionType == null) {
            events.execute(it.illegalMove("No such actionType: ${it.moveType}"))
            return
        }

        val action: Actionable<T, Any>
        try {
            action = if (actionType.parameterClass == Unit::class) {
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
            return
        }

        if (!actionType.isAllowed(action)) {
            events.execute(it.illegalMove("Action is not allowed"))
            return
        }

        val beforeMoveEliminated = controller.eliminationCallback.eliminations()
        try {
            controller.stateKeeper.clear() // TODO: Remove this and use replayable
            events.execute(PreMoveEvent(it.game, it.player, it.moveType, action.parameter))
            actionType.perform(action)
            if (controller.stateKeeper.logs().size > 20) { throw IllegalStateException("${controller.stateKeeper.logs().size}") }
            controller.stateKeeper.logs().forEach { log -> sendLogs(serverGame, log) }
        } catch (e: Exception) {
            logger.error(e) { "Error processing move $it" }
            events.execute(it.illegalMove("Error occurred while processing move: $e"))
        }
        val recentEliminations = controller.eliminationCallback.eliminations().minus(beforeMoveEliminated)

        events.execute(MoveEvent(it.game, it.player, it.moveType, action.parameter))
        for (elimination in recentEliminations) {
            events.execute(PlayerEliminatedEvent(it.game, elimination.playerIndex,
                    elimination.winResult, elimination.position))
        }

        if (controller.isGameOver()) {
            events.execute(GameEndedEvent(it.game))
        }
    }

    fun setup(events: EventSystem) {
        val entryPoint = GamesImpl.game(dsl)
        events.listen("DslGameSystem $gameTypeName Setup", GameStartedEvent::class, {it.game.gameType.type == gameTypeName}, {
            val dbIntegration = this.dbIntegration()
            val appropriateReplayListener =
                if (it.game.gameMeta.database && dbIntegration != null) GamesServer.replayStorage.database<T>(dbIntegration, it.game.gameId)
                else GameplayCallbacks()
            it.game.obj = entryPoint.replayable(
                it.game.players.size,
                it.game.gameMeta.gameOptions ?: Unit,
                serverGameListener(it.game),
                appropriateReplayListener
            ) as GameReplayableImpl<Any>
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

    private fun serverGameListener(game: ServerGame): GameplayCallbacks<T> {
        // TODO: Use callbacks here instead of methods in ServerGame class
        return GameplayCallbacks()
    }

    private fun sendLogs(serverGame: ServerGame, log: ActionLogEntry) {
        val yourLog = logEntryMessage(serverGame, log.secret ?: log.public)
        val othersLog = logEntryMessage(serverGame, log.public)
        serverGame.players.forEachIndexed { index, client ->
            val msg = if (index == log.playerIndex) yourLog else othersLog
            msg?.let { client.send(it) }
        }
        if (othersLog != null) {
            serverGame.observers.forEach { client -> othersLog.let { client.send(it) } }
        }
    }

    private fun logEntryMessage(serverGame: ServerGame, entry: LogEntry?): Map<String, Any>? {
        if (entry == null) return null
        return mapOf(
            "type" to "ActionLog",
            "gameType" to serverGame.gameType.type,
            "gameId" to serverGame.gameId,
            "highlights" to entry.highlights.associateWith { true },
            "parts" to entry.parts
        )
    }

}
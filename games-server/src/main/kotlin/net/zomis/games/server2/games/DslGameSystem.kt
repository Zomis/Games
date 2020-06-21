package net.zomis.games.server2.games

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.*
import net.zomis.games.server2.StartupEvent

class DslGameSystem<T : Any>(val name: String, val dsl: GameSpec<T>) {

    private val mapper = jacksonObjectMapper()
    private val logger = KLoggers.logger(this)
    fun setup(events: EventSystem) {
        val server2GameName = name
        val setup = GameSetupImpl(dsl)
        events.listen("DslGameSystem $name Setup", GameStartedEvent::class, {it.game.gameType.type == server2GameName}, {
            it.game.obj = setup.createGame(it.game.players.size, it.game.gameMeta.gameOptions)
        })
        events.listen("DslGameSystem $name Move", PlayerGameMoveRequest::class, {
            it.game.gameType.type == server2GameName
        }, {
            val serverGame = it.game
            val controller = it.game.obj as GameImpl<T>
            if (controller.isGameOver()) {
                events.execute(it.illegalMove("Game already finished"))
                return@listen
            }

            val actionType = controller.actions.type(it.moveType)
            if (actionType == null) {
                events.execute(it.illegalMove("No such actionType: ${it.moveType}"))
                return@listen
            }

            val action: Actionable<T, Any>
            try {
                action = if (actionType.parameterClass == Unit::class) {
                    actionType.createAction(it.player, Unit)
                } else {
                    // it.move is a JsonNode
                    val serializedMove = mapper.convertValue(it.move, actionType.actionType.serializedType.java)
                    actionType.createActionFromSerialized(ActionOptionsContext(controller.model,  actionType.name, it.player), serializedMove)
                }
            } catch (e: Exception) {
                logger.error(e, "Error reading move: $it")
                return@listen
            }

            if (!actionType.isAllowed(action)) {
                events.execute(it.illegalMove("Action is not allowed"))
                return@listen
            }

            val beforeMoveEliminated = controller.eliminationCallback.eliminations()
            try {
                events.execute(PreMoveEvent(it.game, it.player, it.moveType, action.parameter))
                actionType.perform(action)
                controller.stateKeeper.logs().forEach { log -> sendLogs(serverGame, log) }
                controller.stateCheck()
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
        })
        events.listen("DslGameSystem register $name", StartupEvent::class, {true}, {
            events.execute(GameTypeRegisterEvent(server2GameName))
        })
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
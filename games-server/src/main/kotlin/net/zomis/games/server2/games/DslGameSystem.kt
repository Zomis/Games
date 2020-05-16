package net.zomis.games.server2.games

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.WinResult
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.StartupEvent

class DslGameSystem<T : Any>(val name: String, val dsl: GameSpec<T>) {

    private val mapper = jacksonObjectMapper()
    private val logger = KLoggers.logger(this)
    fun setup(events: EventSystem) {
        val server2GameName = name
        val setup = GameSetupImpl(dsl)
        events.listen("DslGameSystem $name Setup", GameStartedEvent::class, {it.game.gameType.type == server2GameName}, {
            it.game.obj = setup.createGame(it.game.players.size, setup.getDefaultConfig())
        })
        events.listen("DslGameSystem $name Move", PlayerGameMoveRequest::class, {
            it.game.gameType.type == server2GameName
        }, {
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

            val parameter: Any
            try {
                val clazz = actionType.parameterClass
                parameter = if (clazz == Unit::class) {
                    Unit
                } else {
                    val moveJsonText = mapper.writeValueAsString(it.move)
                    mapper.readValue(moveJsonText, clazz.java)
                }
            } catch (e: Exception) {
                logger.error(e, "Error reading move: $it")
                return@listen
            }

            val action = actionType.createAction(it.player, parameter)
            if (!actionType.isAllowed(action)) {
                events.execute(it.illegalMove("Action is not allowed"))
                return@listen
            }

            val beforeMoveEliminated = controller.eliminationCallback.eliminations()
            try {
                events.execute(PreMoveEvent(it.game, it.player, it.moveType, parameter))
                actionType.perform(action)
                controller.stateCheck()
            } catch (e: Exception) {
                logger.error(e) { "Error processing move $it" }
                events.execute(it.illegalMove("Error occurred while processing move: $e"))
            }
            val recentEliminations = controller.eliminationCallback.eliminations().minus(beforeMoveEliminated)

            events.execute(MoveEvent(it.game, it.player, it.moveType, parameter))
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

}
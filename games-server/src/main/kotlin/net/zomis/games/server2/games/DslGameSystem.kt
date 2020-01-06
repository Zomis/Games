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
        val server2GameName = "DSL-$name"
        val setup = GameSetupImpl(dsl)
        events.listen("DslGameSystem $name Setup", GameStartedEvent::class, {it.game.gameType.type == server2GameName}, {
            it.game.obj = setup.createGame(setup.getDefaultConfig())
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

            events.execute(PreMoveEvent(it.game, it.player, it.moveType, parameter))
            actionType.perform(action)
            events.execute(MoveEvent(it.game, it.player, it.moveType, parameter))
//            events.execute(GameStateEvent(it.game, listOf(Pair("roll", rollResult)))) // TODO: Needs support for random results for serialization

            if (controller.isGameOver()) {
                val winner = controller.getWinner()!!
                it.game.players.indices.forEach { playerIndex ->
                    val winResult = if (winner < 0) WinResult.DRAW else WinResult.forWinner(winner, playerIndex)
                    val won = winner == playerIndex
                    val losePositionPenalty = if (won) 0 else 1
                    events.execute(PlayerEliminatedEvent(it.game, playerIndex, winResult, 1 + losePositionPenalty))
                }
                events.execute(GameEndedEvent(it.game))
            }
        })
        events.listen("DslGameSystem register $name", StartupEvent::class, {true}, {
            events.execute(GameTypeRegisterEvent(server2GameName))
        })
    }

}
package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.games.dsl.impl.ActionInfo
import net.zomis.games.dsl.impl.ActionTypeImplEntry
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

class FrontendActionInfo<T : Any>(val actionType: ActionTypeImplEntry<T, Any>, val actionInfo: ActionInfo) {
    fun toFrontend(): Map<String, Any?> {
        return mapOf(
            "actionType" to actionType.name,
            "nextOptions" to actionInfo.nextOptions.map { it.first ?: it.second },
            "parameters" to actionInfo.parameters.map { actionType.actionType.serialize(it) }
        )
    }
}
class ActionList<T : Any>(val playerIndex: Int, val game: ServerGame, val actions: List<FrontendActionInfo<T>>)
class ActionListRequestHandler(private val game: ServerGame?) {
    private val logger = KLoggers.logger(this)
    private val mapper = jacksonObjectMapper()

    fun <T: Any> availableActionsMessage(obj: GameImpl<T>, playerIndex: Int, moveType: String?, chosen: List<Any>?): List<FrontendActionInfo<T>> {
        if (moveType != null) {
            val actionType = obj.actions.type(moveType)
            return if (actionType != null) {
                val actionInfo = actionType.availableParameters(playerIndex, chosen ?: emptyList())
                listOf(FrontendActionInfo(actionType, actionInfo))
            } else {
                emptyList()
            }
        } else {
            return obj.actions.types().map {
                FrontendActionInfo(it, it.availableParameters(playerIndex, emptyList()))
            }
        }
    }

    fun sendActionList(message: ClientJsonMessage) {
        val actionParams = actionParams(message)
        this.sendActionParams(message.client, actionParams)
    }

    private fun <T: Any> sendActionParams(client: Client, actionParams: ActionList<T>) {
        val game = actionParams.game
        logger.info { "Sending action list data for ${game.gameId} of type ${game.gameType.type} to ${actionParams.playerIndex}" }
        client.send(mapOf(
            "type" to "ActionList",
            "gameType" to game.gameType.type,
            "gameId" to game.gameId,
            "playerIndex" to actionParams.playerIndex,
            "actions" to actionParams.actions.map { it.toFrontend() }
        ))
    }

    private fun actionParams(message: ClientJsonMessage): ActionList<Any> {
        if (game!!.obj !is GameImpl<*>) {
            throw IllegalArgumentException("Game ${game.gameId} of type ${game.gameType.type} is not a valid DSL game")
        }

        val obj = game.obj as GameImpl<Any>
        val playerIndex = message.data.getTextOrDefault("playerIndex", "-1").toInt()
        if (!game.verifyPlayerIndex(message.client, playerIndex)) {
            throw IllegalArgumentException("Client ${message.client} does not have index $playerIndex in Game ${game.gameId} of type ${game.gameType.type}")
        }

        val moveType = message.data.get("moveType")?.asText()
        val chosenJson = message.data.get("chosen") ?: emptyList<JsonNode>()
        val chosen = mutableListOf<Any>()

        for (choiceJson in chosenJson) {
            val actionParams = availableActionsMessage(obj, playerIndex, moveType, chosen)
            val actionInfo = actionParams.single().actionInfo
            val clazz = actionInfo.nextOptions.map { it.second }.map { it::class }.toSet().single()

            val parameter: Any
            try {
                parameter = if (clazz == Unit::class) {
                    Unit
                } else {
                    val useKeys = actionInfo.nextOptions.all { it.first != null }
                    if (useKeys) {
                        mapper.convertValue(choiceJson, String::class.java)
                    } else {
                        val moveJsonText = mapper.writeValueAsString(choiceJson)
                        mapper.readValue(moveJsonText, clazz.java)
                    }
                }
            } catch (e: Exception) {
                logger.error(e, "Error reading choice: $choiceJson")
                throw e
            }
            chosen.add(parameter)
        }
        return ActionList(playerIndex, game, availableActionsMessage(obj, playerIndex, moveType, chosen))
    }

    fun actionRequest(message: ClientJsonMessage, callback: GameCallback) {
        val actionParams = actionParams(message)
        val frontendActionInfo = actionParams.actions.single()

        val action = if (message.data.has("perform") && message.data["perform"].asBoolean()) frontendActionInfo.actionInfo.parameters[0]
            else frontendActionInfo.actionInfo.parameters.singleOrNull().takeIf { frontendActionInfo.actionInfo.nextOptions.isEmpty() }
        if (action != null) {
            val actionRequest = PlayerGameMoveRequest(actionParams.game, actionParams.playerIndex, frontendActionInfo.actionType.name, action, false)
            callback.moveHandler(actionRequest)
        } else {
            this.sendActionParams(message.client, actionParams)
        }
    }

}
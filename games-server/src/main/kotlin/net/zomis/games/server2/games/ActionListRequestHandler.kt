package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.games.dsl.impl.ActionInfo
import net.zomis.games.dsl.impl.ActionInfoByKey
import net.zomis.games.dsl.impl.ActionTypeImplEntry
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

class FrontendActionInfo(val keys: ActionInfoByKey)
class ActionList(val playerIndex: Int, val game: ServerGame, val actions: FrontendActionInfo)
class ActionListRequestHandler(private val game: ServerGame?) {
    private val logger = KLoggers.logger(this)
    private val mapper = jacksonObjectMapper()

    fun <T: Any> availableActionsMessage(obj: GameImpl<T>, playerIndex: Int, moveType: String?, chosen: List<Any>?): FrontendActionInfo {
        return if (moveType != null) {
            val actionType = obj.actions.type(moveType)!!
            val actionInfo = actionType.actionInfoKeys(playerIndex, chosen ?: emptyList())
            FrontendActionInfo(actionInfo)
        } else {
            FrontendActionInfo(obj.actions.allActionInfo(playerIndex, chosen ?: emptyList()))
        }
    }

    fun sendActionList(message: ClientJsonMessage) {
        val actionParams = actionParams(message)
        this.sendActionParams(message.client, actionParams)
    }

    private fun sendActionParams(client: Client, actionParams: ActionList) {
        val game = actionParams.game
        logger.info { "Sending action list data for ${game.gameId} of type ${game.gameType.type} to ${actionParams.playerIndex}" }
        client.send(mapOf(
            "type" to "ActionList",
            "gameType" to game.gameType.type,
            "gameId" to game.gameId,
            "playerIndex" to actionParams.playerIndex,
            "actions" to if (game.obj!!.game.isGameOver()) emptyMap<String, Any>() else actionParams.actions.keys.keys
        ))
    }

    private fun actionParams(message: ClientJsonMessage): ActionList {
        val obj = game!!.obj!!.game
        val playerIndex = message.data.getTextOrDefault("playerIndex", "-1").toInt()
        if (!game.verifyPlayerIndex(message.client, playerIndex)) {
            throw IllegalArgumentException("Client ${message.client} does not have index $playerIndex in Game ${game.gameId} of type ${game.gameType.type}")
        }

        val moveType = message.data.get("moveType")?.asText()
        val chosenJson = message.data.get("chosen") ?: emptyList<JsonNode>()
        val chosen = mutableListOf<Any>()

        for (choiceJson in chosenJson) {
            val actionParams = availableActionsMessage(obj, playerIndex, moveType, chosen)
            val actionInfo = actionParams.keys.keys.values.flatten()
            val nextChosenClazz = actionInfo.filter { !it.isParameter }.map { it.serialized::class }.toSet().let {
                if (it.size == 1) { it.single() } else throw IllegalStateException("Expected only one class but found $it in $actionInfo")
            }

            val parameter: Any
            try {
                parameter = mapper.convertValue(choiceJson, nextChosenClazz.java)
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
        val frontendActionInfo = actionParams.actions.keys.keys.values.flatten()

        val action = if (message.data.has("perform") && message.data["perform"].asBoolean()) frontendActionInfo[0]
            else frontendActionInfo.singleOrNull()?.takeIf { it.isParameter }
        if (action != null) {
            val actionRequest = PlayerGameMoveRequest(actionParams.game, actionParams.playerIndex,
                action.actionType, action.serialized, true)
            callback.moveHandler(actionRequest)
        } else {
            this.sendActionParams(message.client, actionParams)
        }
    }

}
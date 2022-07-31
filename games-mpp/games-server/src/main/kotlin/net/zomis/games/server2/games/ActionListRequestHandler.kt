package net.zomis.games.server2.games

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.games.dsl.impl.ActionInfoByKey
import net.zomis.games.server2.Client
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.getTextOrDefault

class FrontendActionInfo(val keys: ActionInfoByKey)
class ActionList(val playerIndex: Int, val game: ServerGame, val actions: FrontendActionInfo)
class ActionListRequestHandler(private val game: ServerGame) {
    private val logger = KLoggers.logger(this)
    private val mapper = jacksonObjectMapper()

    fun sendActionList(message: ClientJsonMessage) {
        val actionParams = actionParams(message)
        this.sendActionParams(message.client, actionParams)
    }

    private fun sendActionParams(client: Client, actionParams: ActionList) {
        val game = actionParams.game
        if (game.obj == null) {
            logger.warn { "Game object not initialized yet for game ${game.gameId}" }
            return
        }
        game.requireAccess(client, actionParams.playerIndex, ClientPlayerAccessType.READ)
        logger.info { "Sending action list data for ${game.gameId} of type ${game.gameType.type} to ${actionParams.playerIndex}" }
        client.send(mapOf(
            "type" to "ActionList",
            "gameType" to game.gameType.type,
            "gameId" to game.gameId,
            "playerIndex" to actionParams.playerIndex,
            "actions" to if (game.obj!!.isGameOver()) emptyMap<String, Any>() else actionParams.actions.keys.keys
        ))
    }

    private fun actionParams(message: ClientJsonMessage): ActionList {
        if (game.obj == null) {
            return ActionList(-1, game, FrontendActionInfo(ActionInfoByKey(emptyMap())))
        }
        val obj = game.obj!!
        val playerIndex = message.data.getTextOrDefault("playerIndex", "-1").toInt()
        game.requireAccess(message.client, playerIndex, ClientPlayerAccessType.WRITE)
        val moveType = message.data.get("moveType")?.asText()
        val chosen = JsonChoices.deserialize(obj, message.data.get("chosen") ?: mapper.createArrayNode(), playerIndex, moveType)
        obj.actions.choices.setChosen(playerIndex, moveType, chosen)
        return ActionList(playerIndex, game, JsonChoices.availableActionsMessage(obj, playerIndex, moveType, chosen))
    }

    fun actionRequest(message: ClientJsonMessage, callback: GameCallback): Boolean {
        val actionParams = actionParams(message)
        val frontendActionInfo = actionParams.actions.keys.keys.values.flatten()

        val action = if (message.data.has("perform") && message.data["perform"].asBoolean()) frontendActionInfo[0]
            else frontendActionInfo.singleOrNull()?.takeIf { it.isParameter }
        return if (action != null) {
            val actionRequest = PlayerGameMoveRequest(message.client, actionParams.game, actionParams.playerIndex,
                action.actionType, action.serialized, true)
            callback.moveHandler(actionRequest)
            true
        } else {
            this.sendActionParams(message.client, actionParams)
            false
        }
    }

}
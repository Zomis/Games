package net.zomis.games.server2.games

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.games.dsl.impl.Game

object JsonChoices {

    private val mapper = jacksonObjectMapper()
    private val logger = KLoggers.logger(this)

    fun <T: Any> availableActionsMessage(obj: Game<T>, playerIndex: Int, moveType: String?, chosen: List<Any>?): FrontendActionInfo {
        return if (moveType != null) {
            val actionType = obj.actions.type(moveType) ?: throw IllegalArgumentException("actionType not available: $moveType. Available actions are ${obj.actions.actionTypes}")
            val actionInfo = actionType.actionInfoKeys(playerIndex, chosen ?: emptyList())
            FrontendActionInfo(actionInfo)
        } else {
            FrontendActionInfo(obj.actions.allActionInfo(playerIndex, chosen ?: emptyList()))
        }
    }

    fun <T: Any> deserialize(game: Game<T>, node: JsonNode?, playerIndex: Int?, actionType: String?): List<Any> {
        if (playerIndex == null) return emptyList()
        if (node == null || node.isNull) return emptyList()
        require(node.isArray)

        val chosen = mutableListOf<Any>()
        for (choiceJson in node) {
            val actionParams = availableActionsMessage(game, playerIndex, actionType, chosen)
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
        return chosen
    }

}
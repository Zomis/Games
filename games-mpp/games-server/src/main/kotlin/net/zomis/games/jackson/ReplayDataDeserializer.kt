package net.zomis.games.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import net.zomis.games.dsl.*
import net.zomis.games.server2.db.DBGameSummary
import net.zomis.games.server2.db.PlayerInGame

object ReplayDataDeserializer {
    private val mapper = jacksonObjectMapper()

    fun deserialize(tree: ObjectNode, gameSpecLookup: (String) -> GameSpec<out Any>?): ReplayData {
        val gameType = tree["gameType"].asText()
        val playerCount = tree["playerCount"].asInt()
        val configNode = tree["config"]
        val initialState = mapper.convertValue(tree["initialState"], jacksonTypeRef<GameSituationState>())
        val actions = mapper.convertValue(tree["actions"], jacksonTypeRef<List<ActionReplay>>())

        val gameSpec = gameSpecLookup.invoke(gameType) ?: throw IllegalArgumentException("Unable to find gameSpec for '$gameType'")
        val configs = GamesImpl.game(gameSpec).setup().configs()
        deserializeConfigs(configs, configNode)
        return ReplayData(gameType, playerCount, configs, initialState, actions)
    }

    private fun deserializeConfigs(configs: GameConfigs, config: JsonNode) {
        if (configs.configs.isNotEmpty() && configs.isOldStyle()) {
            configs.configs.single().let {
                it.value = mapper.convertValue(config, it.clazz.java)
            }
        } else {
            configs.configs.forEach {
                it.value = mapper.convertValue(config[it.key], it.clazz.java)
            }
        }
    }

    fun deserializeDBSummary(tree: ObjectNode, gameSpecLookup: (String) -> GameSpec<out Any>?): DBGameSummary {
        val gameType = tree["gameType"].asText()
        val players = mapper.convertValue(tree["playersInGame"], jacksonTypeRef<List<PlayerInGame>>())
        val initialState = mapper.convertValue(tree["startingState"], jacksonTypeRef<GameSituationState>())
        val gameId = tree["gameId"].asText()
        val gameState = tree["gameState"].asInt()
        val timeStarted = tree["timeStarted"].asLong()

        val gameSpec = gameSpecLookup.invoke(gameType) ?: throw IllegalArgumentException("Unable to find gameSpec for '$gameType'")
        val configNode = tree["config"]
        val configs = GamesImpl.game(gameSpec).setup().configs()
        deserializeConfigs(configs, configNode)
        return DBGameSummary(gameSpec as GameSpec<Any>, configs, gameId, players, gameType, gameState, initialState, timeStarted)
    }

}

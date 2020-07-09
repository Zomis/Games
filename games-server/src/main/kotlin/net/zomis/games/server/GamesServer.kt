package net.zomis.games.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.GameplayCallbacks
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.ReplayData
import net.zomis.games.server2.ServerGames
import java.io.File

object GamesServer {
    private val mapper = jacksonObjectMapper()
    fun readReplayFromFile(file: File): ReplayData {
        val data = mapper.readValue(file, ReplayData::class.java)
        return cleanReplayData(data, ServerGames.games[data.gameType] as GameSpec<Any>)
    }

    private fun cleanReplayData(data: ReplayData, gameSpec: GameSpec<Any>): ReplayData {
        val setup = GamesImpl.game(gameSpec).setup()
        val game = setup.createGame(data.playerCount, setup.getDefaultConfig())
        return data.copy(config = mapper.convertValue(data.config, setup.configClass().java),
            actions = data.actions.map {
                val serializedType = game.actions.type(it.actionType)!!.actionType.serializedType.java
                it.copy(serializedParameter = mapper.convertValue(it.serializedParameter, serializedType))
            }
        )
    }

    object replayStorage {
        fun <T: Any> fileRecordReplay(fileName: String): GameplayCallbacks<T> = TODO()
        fun <T: Any> database(): GameplayCallbacks<T> = TODO()
    }

}
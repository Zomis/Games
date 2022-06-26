package net.zomis.games.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.db.DBIntegration
import java.io.File

object GamesServer {
    private val mapper = jacksonObjectMapper()
    fun readReplayFromFile(file: File): ReplayData {
        val data = mapper.readValue(file, ReplayData::class.java)
        return cleanReplayData(data, ServerGames.games[data.gameType] as GameSpec<Any>)
    }

    private fun cleanReplayData(data: ReplayData, gameSpec: GameSpec<Any>): ReplayData {
        val setup = GamesImpl.game(gameSpec).setup()
        val game = setup.createGame(data.playerCount, setup.configs())
        return data.copy(config = data.config,
            actions = data.actions.map {
                val serializedType = game.actions.type(it.actionType)!!.actionType.serializedType.java
                it.copy(serializedParameter = mapper.convertValue(it.serializedParameter, serializedType))
            }
        )
    }

    object Replays {
        fun fileRecordReplay(fileName: String): GameListener = TODO()
        fun database(dbIntegration: DBIntegration, gameId: String): GameListener = object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            }
        }
        fun noReplays(): GameListener = object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            }
        }
    }

}
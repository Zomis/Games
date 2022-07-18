package net.zomis.games.server2.ais

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameAI

class AIDebugListener<T: Any>(private val gameAI: GameAI<T>, private val game: Game<T>): GameListener {

    private val mapper = jacksonObjectMapper()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (step !is FlowStep.AwaitInput) return

        game.playerIndices.forEach {
            println("Query $game as $it using ${gameAI.name}:")
            val query = gameAI.query(game, it)
            val str = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(query)
            println(str)
        }
    }

}
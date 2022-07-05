package net.zomis.games.dsl

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.scorers.ScorerFactory
import kotlin.reflect.KClass

interface GameListener {
    suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep)
}
class GameEntryPoint<T : Any>(private val gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    fun setup() = GameSetupImpl(gameSpec)
    fun scorers() = ScorerFactory(gameSpec)

    suspend fun replay(coroutineScope: CoroutineScope, replay: ReplayData, actionConverter: (KClass<*>, Any) -> Any = { _, it -> it }): Replay<T>
        = Replay.initReplay(coroutineScope, gameSpec, replay, actionConverter)

    suspend fun runTests() {
        setup().context.testCases.forEach {
            it.runTests(this)
        }
    }

    override fun toString(): String = "EntryPoint:$gameType"

}

object GamesImpl {

    val api = GamesApi
    fun <T : Any> game(gameSpec: GameSpec<T>) = GameEntryPoint(gameSpec)

}

package net.zomis.games.dsl

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.scorers.ScorerFactory
import kotlin.reflect.KClass

interface GameListener {
    suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep)
}
fun GameListener.postReplay(replayData: ReplayData): GameListener
    = PostReplayListener(replayData, this)
class PostReplayListener(replayData: ReplayData, private val delegate: GameListener): GameListener {
    private var actionIndex = 0
    private var targetAction = replayData.actions.size

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (actionIndex >= targetAction) {
            delegate.handle(coroutineScope, step)
        }
        if (step is FlowStep.ActionPerformed<*>) {
            actionIndex++
        }
    }
    override fun toString(): String = "${this::class.simpleName}($delegate)"
}

class GameEntryPoint<T : Any>(private val gameSpec: GameSpec<T>) {

    val gameType: String = gameSpec.name
    fun setup() = GameSetupImpl(gameSpec)
    fun scorers() = ScorerFactory(gameSpec)

    suspend fun replay(
        coroutineScope: CoroutineScope, replay: ReplayData,
        actionConverter: (KClass<*>, Any) -> Any = { _, it -> it },
        gameListeners: (Game<T>) -> List<GameListener> = { emptyList() }
    ): Replay<T>
        = Replay.initReplay(coroutineScope, gameSpec, replay, actionConverter, gameListeners)

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

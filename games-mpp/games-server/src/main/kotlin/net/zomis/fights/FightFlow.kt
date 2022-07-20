package net.zomis.fights

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.dsl.listeners.BlockingGameListener

class MetricGameContext<T: Any>(
    override val game: T,
    override val eliminations: PlayerEliminationsRead
): MetricGameScope<T>
class MetricGamePlayerContext<T: Any>(
    override val game: T, override val eliminations: PlayerEliminationsRead,
    override val playerIndex: Int
): MetricPlayerScope<T>
class MetricActionContext<T: Any, A: Any>(
    override val game: T,
    override val eliminations: PlayerEliminationsRead,
    override val action: Actionable<T, A>
): MetricActionScope<T, A> {
    override val playerIndex: Int get() = action.playerIndex
}

interface MetricGameScope<T: Any> {
    val game: T
    val eliminations: PlayerEliminationsRead
}
interface MetricPlayerScope<T: Any>: MetricGameScope<T> {
    val playerIndex: Int
}
interface MetricActionScope<T: Any, A: Any>: MetricPlayerScope<T> {
    val action: Actionable<T, A>
}
interface FightSourceScope<T: Any> {
    val gameType: GameEntryPoint<T>
    fun fightEvenly(playersCount: Int, gamesPerCombination: Int, ais: List<GameAI<T>>)
}

interface FightScope<T: Any> {
    val gameType: GameEntryPoint<T>
    fun gameSource(block: FightSourceScope<T>.() -> Unit)
    fun <A: Any, E> actionMetric(actionType: ActionType<T, A>, block: MetricActionScope<T, A>.() -> E): FightActionMetric<T, A, E>
    fun <E> endGameMetric(block: MetricGameScope<T>.() -> E): FightMetric<T, E>
    fun <E> endGamePlayerMetric(block: MetricPlayerScope<T>.() -> E): FightPlayerMetric<T, E>
    fun grouping(function: FightGroupingScope<T>.() -> Unit)
}

class Fight<T: Any>(val fightSetup: FightSetup<T>, val metricsListener: MetricsListener<T>) {
    lateinit var game: Game<T>
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val blockingGameListener = BlockingGameListener()

    suspend fun start() {
        this.game = fightSetup.gameType.setup().startGame(coroutineScope, fightSetup.players.size) { game ->
            fightSetup.players.mapIndexed { index: Int, gameAI: GameAI<T> ->
                gameAI.gameListener(game as Game<T>, index, delayOverride = 0)
            } + blockingGameListener + metricsListener.fight(fightSetup, game as Game<T>)
        }
    }

    suspend fun awaitEnd() {
        blockingGameListener.awaitGameEnd()
        coroutineScope.cancel()
    }

}

typealias MetricDsl<T, E> = MetricGameScope<T>.() -> E
typealias MetricPlayerDsl<T, E> = MetricPlayerScope<T>.() -> E
typealias MetricActionDsl<T, A, E> = MetricActionScope<T, A>.() -> E

class FightContext<T: Any>(override val gameType: GameEntryPoint<T>): FightScope<T> {
    lateinit var results: FightGroupingScope<T>.() -> Unit
    lateinit var sourceContext: FightSourceContext<T>
    val metricsListener = MetricsListener<T>()

    override fun gameSource(block: FightSourceScope<T>.() -> Unit) {
        this.sourceContext = FightSourceContext(gameType).also(block)
    }

    override fun <A : Any, E> actionMetric(
        actionType: ActionType<T, A>,
        block: MetricActionScope<T, A>.() -> E
    ): FightActionMetric<T, A, E>
        = metricsListener.actionMetric(actionType, block)

    override fun <E> endGameMetric(block: MetricDsl<T, E>): FightMetric<T, E>
        = metricsListener.endGameMetric(block)

    override fun <E> endGamePlayerMetric(block: MetricPlayerScope<T>.() -> E): FightPlayerMetric<T, E>
        = metricsListener.endGamePlayerMetric(block)

    fun fights(): Flow<Fight<T>> {
        return sourceContext.flow.map {
            Fight(it, metricsListener)
        }
    }

    override fun grouping(function: FightGroupingScope<T>.() -> Unit) {
        this.results = function
    }

    fun produceResults() = metricsListener.produceResults(results)
}

class FightFlow<T: Any>(val gameType: GameEntryPoint<T>) {

    /*
    * Flow<Game>
    *   Flow<Actionable>
    *     metrics for action or game state. Int/Double/Map<Any, Double> collectors
    *   end metrics
    */
    fun fight(block: FightScope<T>.() -> Unit): Map<String, Any> {
        val context = FightContext(gameType)
        block.invoke(context)
        return runBlocking {
            context.fights().collect {
                it.start()
                it.awaitEnd()
                println(it.game.view(0))

            }
            context.produceResults()
        }
    }

}
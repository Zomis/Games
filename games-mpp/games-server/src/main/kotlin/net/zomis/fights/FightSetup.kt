package net.zomis.fights

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.zomis.games.dsl.GameEntryPoint
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.metrics.FightSetup

suspend fun <E> recursiveAdd(source: List<E>, count: Int, destination: List<E>, onCompleted: suspend (List<E>) -> Unit) {
    check(destination.size <= count)
    if (destination.size == count) {
        onCompleted.invoke(destination)
        return
    }
    for (e in source) {
        recursiveAdd(source.minus(e), count, destination.plus(e), onCompleted)
    }
}

class FightSourceContext<T: Any>(override val gameType: GameEntryPoint<T>): FightSourceScope<T> {
    lateinit var flow: Flow<FightSetup<T>>
    override fun fightEvenly(playersCount: Int, gamesPerCombination: Int, ais: List<GameAI<T>>) {
        val config = gameType.setup().configs()
        this.flow = flow {
            recursiveAdd(ais, playersCount, emptyList()) { players ->
                repeat(gamesPerCombination) {
                    println("FIGHT! $players iteration $it")
                    emit(FightSetup(gameType, config, players, it))
                }
            }
        }
    }
}

package net.zomis.games.metrics

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.GameEntryPoint
import net.zomis.games.dsl.impl.GameAI

data class FightSetup<T: Any>(val gameType: GameEntryPoint<T>, val config: GameConfigs, val players: List<GameAI<T>>, val iteration: Int)

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

class MetricData<T: Any, E>(val fight: FightSetup<T>, val data: E)

class FightMetric<T: Any, E>(val block: MetricDsl<T, E>) {
    internal val values = mutableListOf<MetricData<T, E>>()
}
class FightPlayerMetric<T: Any, E>(val block: MetricPlayerDsl<T, E>) {
    internal val values = mutableListOf<MetricData<T, List<E>>>()
}
class FightActionMetric<T: Any, A: Any, E>(val actionType: ActionType<T, A>, val block: MetricActionDsl<T, A, E>) {
    internal val byPlayer = mutableMapOf<Int, MutableList<MetricData<T, E>>>()
}

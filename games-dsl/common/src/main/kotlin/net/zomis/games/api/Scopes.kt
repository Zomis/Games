package net.zomis.games.api

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.dsl.GameConfig
import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.impl.Game

interface Scope
interface PrimitiveScope: Scope
interface CompoundScope: Scope
interface UsageScope: Scope

interface GameModelScope<T: Any>: PrimitiveScope {
    val model: T
}
interface MetaScope<T: Any>: PrimitiveScope {
    val meta: GameMetaScope<T>
}
interface PlayerCountScope: PrimitiveScope {
    val playerCount: Int
    val playerIndices get() = (0 until playerCount)
}
interface GameScope<T: Any>: PrimitiveScope {
    val game: Game<T>
}
interface ReplayableScope: PrimitiveScope {
    val replayable: net.zomis.games.dsl.ReplayStateI
}
interface EventScope<E>: PrimitiveScope {
    val event: E
}
interface ValueScope<V>: PrimitiveScope {
    val value: V
}
interface EliminationsScope: PrimitiveScope {
    val eliminations: PlayerEliminationsRead
}
interface MutableEliminationsScope: PrimitiveScope {
    val eliminations: PlayerEliminationsWrite
}
interface ActionScope<A: Any>: PrimitiveScope {
    val action: A
}
interface ConfigScope: PrimitiveScope {
    fun <C: Any> config(config: GameConfig<C>): C
}
interface PlayerIndexScope: PrimitiveScope {
    val playerIndex: Int
}
/*
interface LogScope<T: Any>: PrimitiveScope {
    suspend fun log(logging: LoggingScope<T>.() -> String)
    suspend fun logSecret(player: PlayerIndex, logging: LoggingScope<T>.() -> String): LogSecretScope<T>
}
*/

/*
Categories:
- Events / Triggers
- Rules
- Actions
  - ActionsChosen
- Game setup
  - GameFlow
  - GameFlowStep
- Metrics
- Scorers

*/
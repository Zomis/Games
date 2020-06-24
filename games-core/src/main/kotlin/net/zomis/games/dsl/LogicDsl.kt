package net.zomis.games.dsl

import net.zomis.games.PlayerEliminations

interface Replayable {
    fun toStateString(): String
}
interface ReplayableScope {
    fun map(key: String, default: () -> Map<String, Any>): Map<String, Any>
    fun int(key: String, default: () -> Int): Int
    fun ints(key: String, default: () -> List<Int>): List<Int>
    fun string(key: String, default: () -> String): String
    fun strings(key: String, default: () -> List<String>): List<String>
    fun list(key: String, default: () -> List<Map<String, Any>>): List<Map<String, Any>>
}
@Deprecated("Use ReplayableScope instead")
interface ReplayScope {
    fun state(key: String): Any
    fun fullState(key: String): Any?
}
interface EffectScope : GameUtils {
    override val playerEliminations: PlayerEliminations
    override val replayable: ReplayableScope
    fun state(key: String, value: Any)
}

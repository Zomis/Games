package net.zomis.games

import net.zomis.games.api.RandomData
import net.zomis.games.api.RandomDataInts
import net.zomis.games.api.RandomDataStrings
import net.zomis.games.common.times
import net.zomis.games.dsl.ReplayStateI

data class Die<T>(val faces: List<T>) {
    operator fun plus(other: Die<T>): DicePool<T> = DicePool(listOf(this, other))
    operator fun times(count: Int): DicePool<T> = DicePool(listOf(this) * count)
    fun <R> map(mapping: (T) -> R): Die<R> = Die(faces.map(mapping))
}

data class DicePool<T>(val dice: List<Die<T>>) {
    operator fun plus(other: Die<T>): DicePool<T> = DicePool(dice + other)
    operator fun plus(other: DicePool<T>): DicePool<T> = DicePool(dice + other.dice)
    operator fun times(count: Int): DicePool<T> = DicePool(dice * count)
    fun <R> map(mapping: (T) -> R): DicePool<R> = DicePool(dice.map { it.map(mapping) })

    fun randomiser(key: String): RandomData<List<T>> {
        val randomiser = RandomDataInts(key) { dice.map { it.faces.indices.random() } }
        return object : RandomData<List<T>> {
            override val key: String = key
            override fun random(replayable: ReplayStateI): List<T> {
                return randomiser.random(replayable).mapIndexed { index, r ->
                    dice[index].faces[r]
                }
            }
        }
    }
    fun randomiser(key: String, stringMapping: (T) -> String): RandomData<List<T>> {
        val randomiser = RandomDataStrings(key) {
            dice.map { stringMapping.invoke(it.faces.random()) }
        }
        return object : RandomData<List<T>> {
            override val key: String = key
            override fun random(replayable: ReplayStateI): List<T> {
                return randomiser.random(replayable).mapIndexed { index, r ->
                    dice[index].faces.first { stringMapping.invoke(it) == r }
                }
            }
        }
    }

}

object Dice {

    fun d(faces: Int) = Die((1..faces).toList())

    val d6 = d(6)

}
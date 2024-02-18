package net.zomis.games.api

import net.zomis.games.dsl.ReplayStateI

interface RandomData<T> {
    val key: String
    fun random(replayable: ReplayStateI): T
}

class RandomDataInt(
    override val key: String,
    private val defaultFunction: () -> Int
) : RandomData<Int> {
    override fun random(replayable: ReplayStateI): Int = replayable.int(key, defaultFunction)
}

class RandomDataInts(
    override val key: String,
    private val defaultFunction: () -> List<Int>
) : RandomData<List<Int>> {
    override fun random(replayable: ReplayStateI): List<Int> = replayable.ints(key, defaultFunction)
}

class RandomDataString(
    override val key: String,
    private val defaultFunction: () -> String
) : RandomData<String> {
    override fun random(replayable: ReplayStateI): String = replayable.string(key, defaultFunction)
}

class RandomDataStrings(
    override val key: String,
    private val defaultFunction: () -> List<String>
) : RandomData<List<String>> {
    override fun random(replayable: ReplayStateI): List<String> = replayable.strings(key, defaultFunction)
}

class RandomFromList<T>(
    override val key: String,
    private val list: List<T>,
    private val count: Int,
    private val stringMapper: (T) -> String,
) : RandomData<List<T>> {
    override fun random(replayable: ReplayStateI): List<T> = replayable.randomFromList(key, list, count, stringMapper)
}

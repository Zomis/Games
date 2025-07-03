package net.zomis.games.components

import kotlin.math.log2

fun entropy(solutions: List<Int>): Double {
    // https://en.wikipedia.org/wiki/Entropy_(information_theory)
    val total = solutions.sum().toDouble()
    return -solutions.map { it / total }.sumOf { it * log2(it) }
}

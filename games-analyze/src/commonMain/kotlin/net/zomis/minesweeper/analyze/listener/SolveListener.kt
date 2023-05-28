package net.zomis.minesweeper.analyze.listener

import net.zomis.minesweeper.analyze.FieldGroup

fun interface SolveListener<T> {
    fun onValueSet(analyze: Analyze<T>, group: FieldGroup<T>, value: Int)
}
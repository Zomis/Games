package net.zomis.minesweeper.analyze.listener

import net.zomis.minesweeper.analyze.FieldGroup

fun interface RuleListener<T> {
    fun onValueSet(group: FieldGroup<T>, value: Int)
}
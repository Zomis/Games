package net.zomis.minesweeper.analyze.listener

import net.zomis.minesweeper.analyze.GroupValues
import net.zomis.minesweeper.analyze.RuleConstraint

interface Analyze<T> {
    val depth: Int
    fun addRule(rule: RuleConstraint<T>)
    val knownValues: GroupValues<T>
}
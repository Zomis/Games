package zomis.alberi

import net.zomis.minesweeper.analyze.FieldGroup
import net.zomis.minesweeper.analyze.FieldRule
import net.zomis.minesweeper.analyze.listener.Analyze
import net.zomis.minesweeper.analyze.listener.SolveListener

class AlberiListener : SolveListener<String> {
    override fun onValueSet(
        analyze: Analyze<String>,
        group: FieldGroup<String>, value: Int
    ) {
        println("onValueSet for depth " + analyze!!.depth + ": " + group + " = " + value)
        val radix: Int = 36
        if (value > 0) {
            for (str in group!!) {
                val x = str[0].toString().toInt(radix)
                val y = str[1].toString().toInt(radix)
                val fields: MutableList<String> = mutableListOf()
                for (xx in x - 1..x + 1) {
                    for (yy in y - 1..y + 1) {
                        if (xx == x && yy == y) {
                            continue
                        }
                        val field: String = AlberiTest.str(xx, yy)
                        fields.add(field)
                    }
                }
                val rule = FieldRule(str, fields, 0)
                analyze.addRule(rule)
                println("Adding rule dynamically: " + rule + " to depth " + analyze.depth)
            }
        }
    }
}
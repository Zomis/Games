package net.zomis.games.analyze

import com.google.common.truth.Truth
import net.zomis.games.components.Point
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeAnalyze
import net.zomis.games.impl.minesweeper.NeighborStyle
import net.zomis.games.impl.minesweeper.Neighbors
import net.zomis.minesweeper.analyze.FieldGroup
import net.zomis.minesweeper.analyze.sanityCheck
import org.junit.jupiter.api.Test

class MfeAnalyzeTest {

    @Test
    fun test() {
        val input = arrayOf(
            "xxxxx_xxxxx_____",
            "xxxxx_xxxxx_____",
            "xxxxx_xxxxx_____",
            "xxxxx___________",
            "________________",
            "_____x__________",
            "_____________xxx",
            "____________1232",
            "___2222x__x_1000",
            "___1001112xx1000",
            "___2000001221011",
            "__x201110000001x",
            "___202x20001111_",
            "___213x20002x___",
            "____x2110113x___",
            "_____10001x_____",
        )
        val model = Flags.Model(2, Point(16, 16))
        Neighbors.configure(model, NeighborStyle.NORMAL)
        model.grid.all().forEach {
            val f = it.value
            when (val ch = input[it.y][it.x]) {
                'x' -> f.mineValue = 1
                '_' -> {}
                in '0'..'9' -> { f.value = ch.digitToInt(); f.clicked = true }
                else -> TODO(ch.toString())
            }
        }
        Truth.assertThat(model.remainingMines()).isEqualTo(51)
        val analyze = MfeAnalyze.analyze(model)
        println("Rules:")
        analyze.rules.forEach {
            println(it)
        }
        println("Solutions:")
        analyze.solutions.forEach {
            println(it)
        }
        println("Groups:")
        analyze.groups.forEach {
            println(it + " = " + it.probability)
        }

        analyze.sanityCheck()
        Truth.assertThat(analyze.getGroupFor(model.fieldAt(Point(4, 7)))?.probability).isAtLeast(0.999)

    }

}
@file:OptIn(ExperimentalJsExport::class)

import net.zomis.games.common.asIndexRange
import net.zomis.games.components.Point
import net.zomis.games.impl.grids.Ricochet
import net.zomis.games.impl.logic.TuringMachine
import net.zomis.games.impl.logic.TuringMachineGame
import kotlin.time.measureTimedValue

/*
 * These tools are for comparing your performance to the computer.
 * Not to be used for cheating.
 * To build run `gradlew :games-impl:jsBrowserDevelopmentLibraryDistribution`
 */

private fun String.asPos(): Point {
    check(this.length == 2)
    val x = this.first().digitToInt(16)
    val y = this.last().digitToInt(16)
    return Point(x, y)
}

@JsExport
data class RicochetResult(val timeTaken: String, val distance: Int, val path: String)

@JsExport
fun ricochetTool(mapConfig: String, pieces: String, goalColor: String, goalSymbol: String): RicochetResult {
    val map = Ricochet.createMap()

    val keyPositions = mapConfig.split(" ")
    map.place(Ricochet.Color.Red.cog, keyPositions[0].asPos())
    map.place(Ricochet.Color.Red.halfMoon, keyPositions[1].asPos())
    map.place(Ricochet.Color.Red.planet, keyPositions[2].asPos())
    map.place(Ricochet.Color.Red.starSign, keyPositions[3].asPos())

    val piecePositions = pieces.split(" ")
    map.piece(Ricochet.Color.Blue, piecePositions[0].asPos())
    map.piece(Ricochet.Color.Green, piecePositions[1].asPos())
    map.piece(Ricochet.Color.Red, piecePositions[2].asPos())
    map.piece(Ricochet.Color.Yellow, piecePositions[3].asPos())
    map.piece(Ricochet.Color.Wildcard, piecePositions[4].asPos())

    val str = StringBuilder()
    str.append(map.toString())

    val color = Ricochet.Color.entries.single { it.name.contains(goalColor, ignoreCase = true) }
    val symbol = Ricochet.Symbol.entries.single { it.name.contains(goalSymbol, ignoreCase = true) }
    val target = Ricochet.Target(color, symbol, emptySet())

    val path = measureTimedValue {
        map.findPath(target).first()
    }
    str.append("Target $target: ${path.value.size} (${path.duration})")
    return RicochetResult(path.duration.toString(), path.value.size, path.value.toString())
}

@JsExport
data class TuringMachineAnswer(val process: String, val verifierOptions: List<List<Any>>)

@JsExport
fun turingMachineTool(cards: String, answer: String, verifierOptions: Array<Int>): TuringMachineAnswer {
    val cardList = cards.split(" ").map { it.toInt() }
    val level = TuringMachine.level(answer.toInt(), *cardList.toIntArray())
    val verifiers = level.verifiers
    val criteria: List<TuringMachine.Criterion> = if (verifierOptions.isEmpty()) {
        if (level.allPossibleCriteria().size == 1) {
            level.criteria
        } else return TuringMachineAnswer("", verifiers.map { verifier ->
            verifier.options.asIndexRange().map { index -> verifier.option(index).name ?: index }
        })
    } else {
        verifierOptions.mapIndexed { index, i -> verifiers[index].option(i) }
    }

    val ai = TuringMachineGame.AI(verifiers)
    ai.disqualifyImplies()
    val rounds = ai.playFullGame(criteria)
    val str = StringBuilder()
    rounds.forEach {
        str.append(it.text())
    }
    str.append(ai.resultsText(rounds))
    return TuringMachineAnswer(str.toString(), emptyList())
}

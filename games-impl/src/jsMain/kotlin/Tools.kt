@file:OptIn(ExperimentalJsExport::class)

import net.zomis.games.components.Point
import net.zomis.games.impl.grids.Ricochet
import net.zomis.games.impl.logic.TuringMachine
import net.zomis.games.impl.logic.TuringMachineGame
import net.zomis.games.impl.logic.TuringNumber
import kotlin.time.measureTimedValue

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
        map.findPath(target)
    }
    str.append("Target $target: ${path.value.size} (${path.duration})")
    return RicochetResult(path.duration.toString(), path.value.size, path.value.toString())
}

@JsExport
fun turingMachineTool(cards: String, answer: String): String {
    val cardNames = cards.split(" ")
    val cardList = cardNames.map { it.toInt() }
    val level = TuringMachine.level(answer.toInt(), *cardList.toIntArray())

    val checkers = level.checkers()
    val verifiers = level.verifiers()
    val ai = TuringMachineGame.AI(checkers)
    ai.disqualifyImplies()
    var questionsAsked = 0
    var number: TuringNumber? = null
    val str = StringBuilder()
    while (ai.needsMoreInformation()) {
        str.appendLine("AI Needs more information.")
        str.appendLine(ai.infoToString())
        str.appendLine("Possible proposals: " + ai.pickBestProposal())
        if (number == null || questionsAsked >= 3) {
            questionsAsked = 0
            number = ai.pickBestProposal().random()
        }

        val checker = ai.pickBestQuestion(number)
        val indexAsk = checkers.indexOf(checker)
        val result = verifiers[indexAsk].check(number)
        questionsAsked++

        str.appendLine("result was $result when checking $number criteria index $indexAsk")
        ai.learn(number, indexAsk, result)
        str.appendLine()
    }
    str.appendLine(ai.infoToString())
    return str.toString()
}

private fun TuringMachineGame.AI.infoToString(): String {
    val str = StringBuilder()
    val potentialSolutions = possibleSolutions()
    str.appendLine("${potentialSolutions.size} possible numbers: $potentialSolutions")
    str.appendLine("Options: ${criteriaCards.map { it.options }}")
    for (i in criteriaCards.indices) {
        str.appendLine("$i: " + solutionsForChecker(criteriaCards[i]))
    }
    return str.toString()
}

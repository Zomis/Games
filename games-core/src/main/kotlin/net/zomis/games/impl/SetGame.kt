package net.zomis.games.impl

import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.ReplayableScope
import kotlin.random.Random

data class SetPiece(val count: Int, val shape: String, val filling: String, val color: String) {
    fun toStateString(): String {
        return "$count-$shape-$filling-$color"
    }
}
data class SetAction(val set: List<String>)
data class SetConfig(val losePoints: Boolean)
data class SetGameModel(val config: SetConfig, val players: Int) {

    val scores: MutableList<Int> = (1..players).map { 0 }.toMutableList()

    val counts = setOf(1, 2, 3)
    val shapes = setOf("ellipse", "squiggly", "diamond")
    val fillings = setOf("filled", "clear", "striped")
    val colors = setOf("green", "red", "purple")
    val deck: CardZone<SetPiece> = CardZone(counts.flatMap {count ->
        shapes.flatMap { shape ->
            fillings.flatMap { filling ->
                colors.map { color -> SetPiece(count, shape, filling, color) }
            }
        }
    }.shuffled(Random(42)).toMutableList())

    val board = CardZone<SetPiece>()

    fun findSets(sett: Iterable<SetPiece>): Sequence<Set<SetPiece>> = sequence {
        for (first in sett) {
            for (second in sett) {
                if (first == second) {
                    continue
                }

                for (third in sett) {
                    if (third == second || third == first) {
                        continue
                    }
                    if (isMatch(first, second, third)) {
                        yield(setOf(first, second, third))
                    }
                }
            }
        }
    }

    private fun propertyMatch(a: Any, b: Any, c: Any): Boolean {
        return if (a == b) b == c else b != c && a != c
    }

    private fun isMatch(first: SetPiece, second: SetPiece, third: SetPiece): Boolean {
        return propertyMatch(first.count, second.count, third.count) &&
                propertyMatch(first.shape, second.shape, third.shape) &&
                propertyMatch(first.filling, second.filling, third.filling) &&
                propertyMatch(first.color, second.color, third.color)
    }

    fun convertToPiece(line: String): SetPiece {
        val values = line.split("-").toSet()

        val count = values.map { it.toIntOrNull() }.intersect(counts).first()!!
        val shape = values.intersect(shapes).first()
        val filling = values.intersect(fillings).first()
        val color = values.intersect(colors).first()

        return SetPiece(count, shape, filling, color)
    }

    private fun isMatch(set: List<String>): List<SetPiece> {
        val pieces = set.map { convertToPiece(it) }
        return if (pieces.size == 3 && isMatch(pieces[0], pieces[1], pieces[2])) pieces else emptyList()
    }

    fun playerChooseSet(playerIndex: Int, parameter: SetAction) {
        val setFound = isMatch(parameter.set)
        if (setFound.isEmpty() && config.losePoints) {
            scores[playerIndex]--
        }
        if (setFound.isNotEmpty()) {
            scores[playerIndex]++
            setFound.asSequence().forEach { board.card(it).remove() }
        }
    }

    fun setExists(extras: List<SetPiece>): Boolean {
        return this.findSets(this.board.cards + extras).any()
    }

}
object SetGame {
    fun setCheck(game: SetGameModel, replayableScope: ReplayableScope): Boolean {
        val additionalCardsIncrement = 3

        var allExtra = listOf<SetPiece>()
        var extraNeeded = 0
        while (!game.setExists(allExtra) || game.board.size + allExtra.size < 12) {
            extraNeeded += additionalCardsIncrement
            if (game.deck.size < extraNeeded) {
                // If we're running out of cards, we can continue as long as a set exists with the cards we already have
                return game.setExists(emptyList())
            }
            allExtra = game.deck.top(extraNeeded)
        }
        val states = replayableScope.strings("extra") { allExtra.map { c -> c.toStateString() } }
        val cards = game.deck.findStates(states) { c -> c.toStateString() }
        game.deck.deal(cards, listOf(game.board))
        return true
    }

    val factory = GameCreator(SetGameModel::class)
    val callSet = factory.action("set", SetAction::class)
    val game = factory.game("Set") {
        setup(SetConfig::class) {
            players(1..16)
            defaultConfig {
                SetConfig(false)
            }
            init {
                SetGameModel(config, this.playerCount)
            }
            onStart {
                val states = this.strings("cards") { it.deck.top(12).map { c -> c.toStateString() } }
                val cards = it.deck.findStates(states) { c -> c.toStateString() }
                it.deck.deal(cards, listOf(it.board))
                setCheck(it, this)
            }
        }
        rules {
            action(callSet) {
                choose {
                    options({ game.board.map { c -> c.toStateString() } }) {first ->
                        options({ game.board.map { c -> c.toStateString() }.minus(first) }) {second ->
                            options({ game.board.map { c -> c.toStateString() }.minus(first).minus(second) }) {third ->
                                parameter(SetAction(listOf(first, second, third)))
                            }
                        }
                    }
                }
                requires { action.parameter.set.distinct().size == action.parameter.set.size && action.parameter.set.size == 3 }
                effect {
                    game.playerChooseSet(action.playerIndex, action.parameter)
                    if (!setCheck(game, this.replayable)) {
                        playerEliminations.eliminateBy(game.scores.mapIndexed { index, i -> index to i }, Comparator { a, b -> a - b })
                    }
                }
            }
        }
        view {
            value("deck") { it.deck.size }
            value("scores") { it.scores }
            value("cards") {
                it.board.map { c -> c.toStateString() }
            }
        }
    }

}

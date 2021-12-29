package net.zomis.games.impl

import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.ReplayableScope

data class SetPiece(val count: Int, val shape: String, val filling: String, val color: String) {
    fun toStateString(): String {
        return "$count-$shape-$filling-$color"
    }

    fun toMap() = mapOf(
        "count" to this.count,
        "shape" to this.shape,
        "filling" to this.filling,
        "color" to this.color,
        "key" to this.toStateString()
    )

}
data class SetAction(val set: List<String>)
data class SetConfig(val losePoints: Boolean)
class SetPropertyResult(val values: List<Any>) {
    private val distinct = values.distinct().size
    val valid = distinct == 1 || distinct == values.size
    private val counts = values.associateWith { a -> values.count { a == it } }
    val majorityValue: Any? = if (valid) null else counts.entries.single { it.value == 2 }.key
    val minorityValue: Any? = if (valid) null else counts.entries.single { it.value == 1 }.key

    fun toMap(): Map<String, Any?> = mapOf("valid" to valid, "uniqueCount" to distinct).let {
        if (!valid) {
            it.plus("majorityValue" to majorityValue).plus("minorityValue" to minorityValue)
        } else it
    }
}
private val properties = mutableMapOf<String, (SetPiece) -> Any>().also { props ->
    props["count"] = { it.count }
    props["shape"] = { it.shape }
    props["filling"] = { it.filling }
    props["color"] = { it.color }
}
data class SetCardsResult(val cards: List<SetPiece>) {
    private fun match(pieces: Set<SetPiece>, mapping: (SetPiece) -> Any): SetPropertyResult {
        return SetPropertyResult(pieces.map(mapping))
    }

    val propertiesMatching: Map<String, SetPropertyResult> = properties.entries.associate {
        it.key to match(cards.toSet(), it.value)
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "cards" to cards.map { it.toMap() },
        "valid" to validSet,
        "properties" to propertiesMatching.mapValues { it.value.toMap() }
    )

    val validSet = propertiesMatching.all { it.value.valid }
}
class SetPlayer(val playerIndex: Int) {
    fun chooseSet(cardsResult: SetCardsResult, config: SetConfig) {
        this.lastResult = cardsResult
        this.tries++
        if (!cardsResult.validSet) {
            if (config.losePoints) {
                this.points--
            }
        }
        if (cardsResult.validSet) {
            this.points++
            this.setsFound++
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "lastResult" to lastResult?.toMap(),
        "points" to points
    )

    var lastResult: SetCardsResult? = null
    var points: Int = 0
    var setsFound: Int = 0
    var tries: Int = 0
}
data class SetGameModel(val config: SetConfig, val playersCount: Int) {
    val players = (0 until playersCount).map { SetPlayer(it) }

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
    }.shuffled().toMutableList())

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

    fun setExists(extras: List<SetPiece>): Boolean {
        return this.findSets(this.board.cards + extras).any()
    }

    fun stringsToCards(strings: List<String>): List<SetPiece> = strings.map { convertToPiece(it) }

    fun setCardsResult(cards: List<SetPiece>): SetCardsResult = SetCardsResult(cards)
}
object SetGame {
    fun convertToPiece(line: String): SetPiece {
        val values = line.split(" ").toSet()
        val counts = arrayOf("1", "2", "3").toSet()
        val shapes = arrayOf("ellipse", "squiggly", "diamond").toSet()
        val fillings = arrayOf("filled", "clear", "striped").toSet()
        val colors = arrayOf("green", "red", "purple").toSet()

        val count = values.intersect(counts).first().toInt()
        val shape = values.intersect(shapes).first()
        val filling = values.intersect(fillings).first()
        val color = values.intersect(colors).first()

        return SetPiece(count, shape, filling, color)
    }

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
                // Place cards on board
                val states = replayable.strings("cards") { game.deck.top(12).map { c -> c.toStateString() } }
                val cards = game.deck.findStates(states) { c -> c.toStateString() }
                game.deck.deal(cards, listOf(game.board))
                setCheck(game, replayable)
            }
        }
        actionRules {
            action(callSet) {
                choose {
                    recursive(emptyList<SetPiece>()) {
                        until { chosen.size == 3 }
                        parameter { SetAction(chosen.map { it.toStateString() }) }
                        optionsWithIds({ (game.board.cards - chosen.toSet()).map { it.toStateString() to it } }) { card ->
                            recursion(card) { list, c -> list + c }
                        }
                    }
                }
                requires { action.parameter.set.distinct().size == action.parameter.set.size && action.parameter.set.size == 3 }
                effect {
                    val cardsResult = game.setCardsResult(game.stringsToCards(action.parameter.set))
                    game.players[action.playerIndex].chooseSet(cardsResult, game.config)
                    if (cardsResult.validSet) {
                        cardsResult.cards.asSequence().forEach { game.board.card(it).remove() }
                    }
                    if (!setCheck(game, this.replayable)) {
                        eliminations.eliminateBy(game.players.mapIndexed { index, i -> index to i.points }, Comparator { a, b -> a - b })
                    }
                }
            }
            view("deck") { game.deck.size }
            view("players") {
                game.players.map { it.toMap() }
            }
            view("chosen") {
                actionsChosen().chosen()?.chosen?.filterIsInstance<String>()?.associate { it to true } ?: emptyMap<String, Boolean>()
            }
            view("cards") {
                game.board.map { it.toMap() }
            }
        }
    }

}

package net.zomis.games.dsl

data class Player(var chips: Money = Money(), val owned: MutableSet<Card> = mutableSetOf(),
                  val reserved: MutableSet<Card> = mutableSetOf()) {
    fun total(): Money = this.discounts() + this.chips

    fun canBuy(card: Card): Boolean = this.total().has(card.costs)

    fun buy(card: Card) {
        val actualCost = card.costs.minus(this.discounts())
        this.chips -= actualCost
        this.owned.add(card)
    }

    fun discounts(): Money {
        return this.owned.map { it.discounts }.fold(Money()) { acc, money -> acc + money }
    }

}

data class Card(val level: Int, val discounts: Money, val costs: Money, val points: Int) {
    // Possible to support multiple discounts on the same card. Because why not!
    constructor(level: Int, discount: MoneyType, costs: Money, points: Int):
        this(level, Money(discount to 1), costs, points)
}

fun <K, V> Map<K, V>.mergeWith(other: Map<K, V>, merger: (V?, V?) -> V): Map<K, V> {
    return (this.keys + other.keys).associateWith {
        merger(this[it], other[it])
    }
}

enum class MoneyType {
    BLACK, WHITE, RED, BLUE, GREEN;

    fun toMoney(count: Int): Money {
        return Money(mutableMapOf(this to count))
    }
}
data class Money(val moneys: MutableMap<MoneyType, Int> = mutableMapOf()) {
    // TODO: Add wildcards as a separate field
    constructor(vararg money: Pair<MoneyType, Int>) : this(mutableMapOf<MoneyType, Int>(*money))

    operator fun plus(other: Money): Money {
        val result = moneys.mergeWith(other.moneys) {a, b -> (a ?: 0) + (b ?: 0)}
        return Money(result.toMutableMap())
    }

    fun has(costs: Money): Boolean = costs.moneys.entries.all { it.value <= this.moneys[it.key] ?: 0 }

    operator fun minus(other: Money): Money {
        val result = moneys.mergeWith(other.moneys) {a, b -> (a ?: 0) - (b ?: 0)}.mapValues {
            if (it.value < 0) 0 else it.value
        }
        return Money(result.toMutableMap())
    }
}

data class MoneyChoice(val moneys: List<MoneyType>) {
    fun toMoney(): Money {
        return Money(moneys.groupBy { it }.mapValues { it.value.size }.toMutableMap())
    }
}

fun startingStockForPlayerCount(playerCount: Int): Int {
    return 10
}

class SplendorGame(playerCount: Int = 2) {

    // TODO: Add nobles
    val cardLevels = 1..3
    private fun randomType(): MoneyType {
        return MoneyType.values().toList().shuffled().first()
    }
    private fun randomCard(level: Int): Card {
        return Card(level, randomType(), Money(randomType() to level * 2), level)
    }
    val players: List<Player> = (1..playerCount).map { Player() }
    val board: MutableList<Card> = mutableListOf()
    val deck: MutableList<Card> = cardLevels.flatMap { level ->
        (0 until 30).map { randomCard(level) }
    }.toMutableList()
    var stock: Money = MoneyType.values().fold(Money()) {money, type -> money + type.toMoney(startingStockForPlayerCount(playerCount))}
    var currentPlayerIndex: Int = 0

    init {
        cardLevels.forEach {level ->
            (1..4).forEach { _ ->
                val card = deck.find { it.level == level }
                if (card != null) {
                    deck.remove(card)
                    board.add(card)
                }
            }
        }
    }

    val currentPlayer: Player
        get() = this.players[this.currentPlayerIndex]

    fun replaceCard(card: Card) {
        val index = board.indexOf(card)
        if (index < 0) {
            throw IllegalArgumentException("Card does not exist on board: $card")
        }
        val nextCard = deck.firstOrNull { it.level == card.level }
        if (nextCard != null) {
            board[index] = nextCard
        } else {
            board.removeAt(index)
        }
    }

}

class DslSplendor {

    val buy = createActionType("buy", Card::class)
    val takeMoney = createActionType("takeMoney", MoneyChoice::class)
    val reserve = createActionType("reserve", Card::class)
    val discardMoney = createActionType("discardMoney", MoneyType::class)
    val splendorGame = createGame<SplendorGame>("Splendor") {
        setup {
//            players(2..4)
            init {
                SplendorGame()
            }
//            effect {
//                state("cards", game.board)
//            }
//            replay {
//                game.board = state("cards")
//            }
        }
        logic {
            singleTarget(buy, {it.board}) { // Is it possible to use API as `buy.singleTarget(game.board)` ?
                allowed { it.game.currentPlayer.canBuy(it.parameter) }
                effect {
                    it.game.currentPlayer.buy(it.parameter)
                    state("nextCard", it.game.replaceCard(it.parameter))
                }
//                replayEffect {
//                    it.game.currentPlayer.buy(it.parameter)
//                    it.game.replaceCard(it.parameter, state("nextCard") as Card)
//                }
            }
//            singleTarget(discardMoney, {MoneyType.values().toList()}) {
//                allowed { it.game.currentPlayer.chips.count > 10 }
//                effect { it.game.currentPlayer.money -= choice }
//            }
//            singleTarget(reserve, {it.board}) {
//                allowed { it.game.currentPlayer.reserved.size < 3 }
//                effect {
//                    it.game.currentPlayer.reserve(it.parameter)
//                    state("nextCard", replaceCard(it.parameter))
//                }
//                replayEffect {
//                    it.game.currentPlayer.reserve(it.parameter)
//                    replaceCard(it.parameter, state("nextCard"))
//                }
//            }
            action(takeMoney) {
                options {
                    option(MoneyType.values().asIterable()) { first ->
                        option(MoneyType.values().asIterable()) {second ->
                            if (first == second) {
                                actionParameter(MoneyChoice(listOf(first, second)))
                            } else {
                                option(MoneyType.values().asIterable()) {third ->
                                    actionParameter(MoneyChoice(listOf(first, second, third)))
                                }
                            }
                        }
                    }
                }
                allowed {
                    val moneyChosen = it.parameter.toMoney()
                    if (!it.game.stock.has(moneyChosen)) {
                        return@allowed false
                    }
                    val chosen = it.parameter.moneys
                    return@allowed when {
                        chosen.size == 2 -> chosen.distinct().size == 1 && it.game.stock.has(moneyChosen.plus(moneyChosen))
                        chosen.size == 3 -> chosen.distinct().size == chosen.size
                        else -> false
                    }
                }
                effect {
                    it.game.stock -= it.parameter.toMoney()
                    it.game.currentPlayer.chips += it.parameter.toMoney()
                }
            }
        }
        fun viewMoney(money: Money): List<Pair<String, Int>> {
            return money.moneys.entries.sortedBy { it.key.name }.map { it.key.name to it.value }
        }
        fun viewCard(card: Card): Map<String, Any?> {
            // TODO: Test without this special mapping function. Perhaps it works anyway?
            return mapOf(
                "level" to card.level,
                "discount" to card.discounts,
                "costs" to card.costs,
                "points" to card.points
            )
        }
        view {
            currentPlayer { it.currentPlayerIndex }
//            winner { game -> game.winner.takeIf { game.isFinished } }
            value("board") {game ->
                game.board.map { viewCard(it) }
            }
            value("stock") {game ->
                game.stock
            }
            value("players") {game ->
                game.players.map {
                    mapOf(
                        "money" to it.chips,
                        "cards" to it.owned,
                        "reserved" to it.reserved.size // TODO: Add possibility for that player to see this card
                    )
                }
            }
        }
    }
}

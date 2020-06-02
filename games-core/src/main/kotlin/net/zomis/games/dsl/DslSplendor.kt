package net.zomis.games.dsl

import net.zomis.games.PlayerEliminationCallback
import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.sourcedest.next
import kotlin.math.absoluteValue
import kotlin.math.max

data class SplendorPlayer(var chips: Money = Money(), val owned: CardZone<SplendorCard> = CardZone(),
                          val reserved: CardZone<SplendorCard> = CardZone()) {
    val nobles = CardZone<SplendorNoble>()
    val points: Int get() = owned.cards.sumBy { it.points } + nobles.cards.sumBy { it.points }

    fun total(): Money = this.discounts() + this.chips

    fun canBuy(card: SplendorCard): Boolean = this.total().hasWithWildcards(card.costs)

    fun pay(costs: Money): Money {
        val chipCosts = (costs - this.discounts()).map({ it.first to max(it.second, 0) }) { it }
        val remaining = (this.chips - chipCosts)
        val wildcardsNeeded = remaining.negativeAmount()
        val actualCosts = this.chips - remaining.map({ it.first to max(it.second, 0) }) { it - wildcardsNeeded }

        val tokensExpected = chipCosts.count
        val oldMoney = this.chips

        this.chips -= actualCosts
        if (actualCosts.negativeAmount() > 0) {
            throw IllegalStateException("Actual costs has negative: $oldMoney --> ${this.chips}. Cost was $chipCosts. Actual $actualCosts")
        }
        if (oldMoney.count - this.chips.count != tokensExpected) {
            throw IllegalStateException("Wrong amount of tokens were taken: $oldMoney --> ${this.chips}. Cost was $chipCosts")
        }
        return actualCosts
    }

    fun discounts(): Money {
        return this.owned.map { it.discounts }.fold(Money()) { acc, money -> acc + money }
    }

}

data class SplendorCard(val level: Int, val discounts: Money, val costs: Money, val points: Int) {
    // Possible to support multiple discounts on the same card. Because why not!
    constructor(level: Int, discount: MoneyType, costs: Money, points: Int):
        this(level, Money(discount to 1), costs, points)

    val id: String get() = toStateString()

    fun toStateString(): String {
        return "$level:$points:${discounts.toStateString()}.${costs.toStateString()}"
    }
}

data class SplendorNoble(val points: Int, val requirements: Money) {
    fun requirementsFulfilled(player: SplendorPlayer): Boolean {
        return player.discounts().hasWithoutWildcards(requirements)
    }

    fun toStateString(): String {
        return "$points:${requirements.toStateString()}"
    }
}

fun <K, V> Map<K, V>.mergeWith(other: Map<K, V>, merger: (V?, V?) -> V): Map<K, V> {
    return (this.keys + other.keys).associateWith {
        merger(this[it], other[it])
    }
}

enum class MoneyType(val char: Char) {
    WHITE('W'), BLUE('U'), BLACK('B'), RED('R'), GREEN('G');

    fun toMoney(count: Int): Money {
        return Money(mutableMapOf(this to count))
    }
}
data class Money(val moneys: MutableMap<MoneyType, Int> = mutableMapOf(), val wildcards: Int = 0) {
    val count: Int = moneys.values.sum() + wildcards

    constructor(vararg money: Pair<MoneyType, Int>) : this(mutableMapOf<MoneyType, Int>(*money))

    operator fun plus(other: Money): Money {
        val result = moneys.mergeWith(other.moneys) {a, b -> (a ?: 0) + (b ?: 0)}
        return Money(result.toMutableMap(), wildcards + other.wildcards)
    }

    fun hasWithWildcards(costs: Money): Boolean {
        val diff = this - costs
        val wildcardsNeeded = diff.negativeAmount()
        return this.wildcards >= wildcardsNeeded
    }

    fun hasWithoutWildcards(costs: Money): Boolean {
        val diff = this - costs
        return diff.wildcards >= 0 && diff.moneys.all { it.value >= 0 }
    }

    fun map(mapping: (Pair<MoneyType, Int>) -> Pair<MoneyType, Int>, wildcardsMapping: (Int) -> Int): Money {
        var result = Money()
        moneys.forEach { pair -> result += Money(mutableMapOf(mapping(pair.key to pair.value))) }
        return Money(result.moneys.toMutableMap(), wildcardsMapping(wildcards))
    }

    fun negativeAmount(): Int = moneys.values.filter { it < 0 }.sum().absoluteValue

    operator fun minus(other: Money): Money {
        val result = moneys.mergeWith(other.moneys) {a, b -> (a ?: 0) - (b ?: 0)}
        return Money(result.toMutableMap(), wildcards - other.wildcards)
    }

    fun toStateString(): String {
        return moneys.entries.sortedBy { it.key.char }.joinToString("") { it.key.char.toString().repeat(it.value) }
    }
}

data class MoneyChoice(val moneys: List<MoneyType>) {
    fun toMoney(): Money {
        return Money(moneys.groupBy { it }.mapValues { it.value.size }.toMutableMap())
    }
}

fun startingStockForPlayerCount(playerCount: Int): Int {
    return when (playerCount) {
        2 -> 4
        3 -> 5
        4 -> 7
        else -> throw IllegalArgumentException("Invalid number of players: $playerCount")
    }
}

object SplendorCardFactory {
    private fun costs(costString: String): Money {
        return costString.map { cost -> MoneyType.values().find { it.char == cost } }.fold(Money()) { money, moneyType -> money + moneyType!!.toMoney(1) }
    }
    fun createCardLevel(level: Int, data: String): List<SplendorCard> {
        return data.trim().split("\n").map { it.trim().split(" ") }.map { it[0] to it.drop(1) }.map {colorAndCards ->
            val discountColor = MoneyType.values().find { it.char == colorAndCards.first.single() }!!
            val pointsAndCost = colorAndCards.second.map { cardString ->
                cardString.takeWhile { it.toString().toIntOrNull() != null }.toIntOrNull() to cardString.dropWhile { it.toString().toIntOrNull() != null }
            }
            pointsAndCost.map { SplendorCard(level, discountColor, costs(it.second), it.first ?: 0) }
        }.flatten()
    }
}

class SplendorGame(val config: SplendorConfig, val eliminations: PlayerEliminationCallback, playerCount: Int) {

    val allNobles = CardZone(listOf("BR", "UW", "UG", "RG", "BW", "BRG", "BUW", "BRW", "GUW", "GUR").map {string ->
        val moneyTypes = string.map { ch -> MoneyType.values().first { it.char == ch } }
        val requiredPerType = if (moneyTypes.size == 2) 4 else 3
        val money = moneyTypes.map { it.toMoney(requiredPerType) }.reduce { acc, money -> acc + money }
        SplendorNoble(3, money)
    }.shuffled().toMutableList())

    val nobles = CardZone<SplendorNoble>()
    val deck = CardZone(listOf("""
W UGRB UGGRB WWWUB UUGGB UUBB RRB 1GGGG UUU
U WGRB WGRRB UGGGR WGGRR GGBB WBB 1RRRR BBB
B WUUGR WUGR GRRRB WWUUR WWGG GGR GGG 1UUUU
R WWUGB WUGB WRBBB WWRR UUG 1WWWW WWW WWGBB
G 1BBBB WURB WURBB WUUUG URRBB UURR WWU RRR
""", """
W 1GGGRRBB 1WWUUURRR 2GRRRRBB 2RRRRRBBB 2RRRRR 3WWWWWW
U 1UUGGRRR 1UUGGGBBB 2WWWWWUUU 2WWRBBBB 3UUUUUU 2UUUUU
B 1WWWGGGBB 2UGGGGRR 1WWWUUGG 2GGGGGRRR 2WWWWW 3BBBBBB
R 1WWRRBBB 1UUURRBBB 2WUUUUGG 2WWWBBBBB 2BBBBB 3RRRRRR
G 2GGGGG 2UUUUUGGG 3GGGGGG 1WWUUUBB 1WWWGGRR 2WWWWUUB
""", """
W 4WWWRRRBBBBBB 4BBBBBBB 5WWWBBBBBBB 3UUUGGGRRRRRBBB
U 5WWWWWWWUUU 4WWWWWWUUUBBB 3WWWGGGRRRBBBBB 4WWWWWWW
B 4RRRRRRR 4GGGRRRRRRBBB 5RRRRRRRBBB 3WWWUUUGGGGGRRR
R 4GGGGGGG 3WWWUUUUUGGGBBB 5GGGGGGGRRR 4UUUGGGGGGRRR
G 4WWWUUUUUUGGG 4UUUUUUU 5UUUUUUUGGG 3WWWWWUUURRRBBB
""").mapIndexed { index, data -> SplendorCardFactory.createCardLevel(index + 1, data) }.flatten().shuffled().toMutableList())
    var turnsLeft = -1
    var roundNumber: Int = 1

    private fun randomType(): MoneyType {
        return MoneyType.values().toList().shuffled().first()
    }
    private fun randomCard(level: Int): SplendorCard {
        return SplendorCard(level, randomType(), Money(randomType() to level * 2), level)
    }

    fun endTurnCheck() {
        if (this.stock.negativeAmount() > 0) {
            throw IllegalStateException("Stock is negative")
        }
        if (this.currentPlayer.chips.negativeAmount() > 0) {
            throw IllegalStateException("Player has negative amount of chips")
        }
        if (this.currentPlayer.discounts().negativeAmount() > 0) throw IllegalStateException("Player has negative amount of discounts")
        if (this.currentPlayer.chips.wildcards < 0) throw IllegalStateException("Player has negative amount of wildcards")
        val totalChipsInGame = this.stock + this.players.fold(Money()) { a, b -> a.plus(b.chips) }
        if (totalChipsInGame.wildcards != 5) throw IllegalStateException("Wrong amount of total wildcards: $totalChipsInGame")
        if (totalChipsInGame.moneys.any { it.value != startingStockForPlayerCount(players.size) }) {
            throw IllegalStateException("Wrong amount of total chips: $totalChipsInGame")
        }

        // Check money count > maxMoney
        if (this.currentPlayer.chips.count > config.maxMoney) return // Need to discard some money

        // Check noble conditions
        val noble = this.nobles.cards.find { it.requirementsFulfilled(currentPlayer) }
        if (noble != null) {
            this.nobles.card(noble).moveTo(this.currentPlayer.nobles)
        }

        // Check game winning conditions
        if (turnsLeft > 0) {
            turnsLeft--
        } else if (this.currentPlayer.points >= config.targetPoints) {
            turnsLeft = this.players.size - 1 - this.currentPlayerIndex
        }

        this.currentPlayerIndex = this.currentPlayerIndex.next(players.size)

        if (this.currentPlayerIndex == 0) {
            this.roundNumber++
        }

        // End game
        if (turnsLeft == 0) {
            eliminations.eliminateBy(players.mapIndexed { index, splendorPlayer -> index to splendorPlayer }, Comparator { a, b -> a.points - b.points })
        }
    }

    val players: List<SplendorPlayer> = (1..playerCount).map { SplendorPlayer() }
    val board: CardZone<SplendorCard> = CardZone(mutableListOf())
    var stock: Money = MoneyType.values().fold(Money()) {money, type -> money + type.toMoney(startingStockForPlayerCount(playerCount))}.plus(Money(mutableMapOf(), 5))
    var currentPlayerIndex: Int = 0

    val currentPlayer: SplendorPlayer
        get() = this.players[this.currentPlayerIndex]

}

data class SplendorConfig(
    val useNobles: Boolean,
    val maxMoney: Int,
    val targetPoints: Int,
    val showReservedCards: Boolean
)

object DslSplendor {

    val buy = createActionType("buy", String::class)
    val buyReserved = createActionType("buyReserved", String::class)
    val takeMoney = createActionType("takeMoney", MoneyChoice::class)
    val takeSingle = createActionType("takeMoneySingle", MoneyType::class)
    val reserve = createActionType("reserve", String::class)
    val discardMoney = createActionType("discardMoney", MoneyType::class)
    val splendorGame = createGame<SplendorGame>("Splendor") {
        setup(SplendorConfig::class) {
            players(2..4)
            defaultConfig { SplendorConfig(
                useNobles = true,
                maxMoney = 10,
                targetPoints = 15,
                showReservedCards = false
            )}
            init {
                SplendorGame(config, eliminationCallback, playerCount)
            }
        }
        rules {
            gameStart {
                val dealCards = (1..3).map { level -> game.deck.first(4) { card -> card.level == level } }.flatten()
                val cardStates = replayable.strings("cards") {
                    dealCards.map { c -> c.toStateString() }
                }
                val cards = game.deck.findStates(cardStates) { c -> c.toStateString() }
                game.deck.deal(cards, listOf(game.board))

                // Nobles
                if (game.config.useNobles) {
                    val nobles = game.allNobles.top(game.players.size + 1)
                    val nobleStates = replayable.strings("nobles") { nobles.map { c -> c.toStateString() } }
                    game.allNobles.deal(game.allNobles.findStates(nobleStates) { c -> c.toStateString() }, listOf(game.nobles))
                }
            }

            allActions.precondition { game.currentPlayerIndex == playerIndex }


            action(buy).options { game.board.map { c -> c.id } }
            action(buy).requires { game.currentPlayer.canBuy(game.board.cards.first { c -> c.id == action.parameter }) }
            action(buy).effect {
                val card = game.board.cards.first { c -> c.id == action.parameter }
                val actualCost = game.currentPlayer.pay(card.costs)
                game.currentPlayer.owned.cards.add(card)
                game.stock += actualCost
                replaceCard(replayable, game, card)
            }

            action(buyReserved).options { game.currentPlayer.reserved.map { c -> c.id } }
            action(buyReserved).requires { game.currentPlayer.canBuy(game.currentPlayer.reserved.cards.first { c -> c.id == action.parameter }) }
            action(buyReserved).effect {
                val param = game.currentPlayer.reserved.cards.first { c -> c.id == action.parameter }
                val card = game.currentPlayer.reserved.card(param)
                val actualCost = game.currentPlayer.pay(card.card.costs)
                game.stock += actualCost
                card.moveTo(game.currentPlayer.owned)
            }

            action(discardMoney).forceUntil { game.currentPlayer.chips.count <= game.config.maxMoney }
            action(discardMoney).options { MoneyType.values().toList() }
            action(discardMoney).requires { game.currentPlayer.chips.hasWithoutWildcards(action.parameter.toMoney(1)) }
            action(discardMoney).effect {
                val money = action.parameter.toMoney(1)
                game.currentPlayer.chips -= money
                game.stock += money
            }

            action(reserve).options { game.board.map { c -> c.id } }
            action(reserve).requires { game.currentPlayer.reserved.size < 3 }
            action(reserve).effect {
                val card = game.board.card(game.board.cards.first { c -> c.id == action.parameter })
                game.currentPlayer.reserved.cards.add(card.card)
                val wildcardIfAvailable = if (game.stock.wildcards > 0) Money(mutableMapOf(), 1) else Money()
                game.stock -= wildcardIfAvailable
                game.currentPlayer.chips += wildcardIfAvailable
                replaceCard(replayable, game, card.card)
            }

            action(takeMoney).choose {
                options({MoneyType.values().asIterable()}) { first ->
                    parameter(MoneyChoice(listOf(first))) // TODO: How to perform this action of only taking one or two?
                    options({MoneyType.values().asIterable()}) {second ->
                        parameter(MoneyChoice(listOf(first, second)))
                        if (first != second) {
                            options({MoneyType.values().asIterable().minus(first).minus(second)}) {third ->
                                parameter(MoneyChoice(listOf(first, second, third)))
                            }
                        }
                    }
                }
            }
            action(takeMoney).requires {
                val moneyChosen = action.parameter.toMoney()
                if (!game.stock.hasWithoutWildcards(moneyChosen)) {
                    return@requires false
                }
                val chosen = action.parameter.moneys
                return@requires when (chosen.size) {
                    1 -> true
                    2 -> chosen.distinct().size != 1 || game.stock.hasWithoutWildcards(moneyChosen.plus(moneyChosen))
                    3 -> chosen.distinct().size == chosen.size
                    else -> false
                }
            }
            action(takeMoney).effect {
                game.stock -= action.parameter.toMoney()
                game.currentPlayer.chips += action.parameter.toMoney()
            }

            allActions.after { game.endTurnCheck() }
        }
        fun viewMoney(money: Money): Map<String, Int> {
            return money.moneys.entries.sortedBy { it.key.name }.map { it.key.name to it.value }
                .let { if (money.wildcards > 0) it.plus("wildcards" to money.wildcards) else it }.toMap()
        }
        fun viewNoble(noble: SplendorNoble): Map<String, Any> = mapOf("points" to 3, "requirements" to viewMoney(noble.requirements))
        fun viewCard(card: SplendorCard): Map<String, Any?> {
            return mapOf(
                "id" to card.id,
                "level" to card.level,
                "discount" to viewMoney(card.discounts),
                "costs" to viewMoney(card.costs),
                "points" to card.points
            )
        }
        view {
            currentPlayer { it.currentPlayerIndex }
            eliminations()
            value("viewer") { viewer }
            value("round") { it.roundNumber }
            value("cardLevels") {game ->
                game.board.cards.sortedBy { -it.level }.groupBy { it.level }.mapValues {
                    mapOf(
                        "level" to it.key,
                        "remaining" to game.deck.cards.count { c -> c.level == it.key },
                        "board" to it.value.map { c -> viewCard(c) }
                    )
                }.values.toList()
            }
            value("stock") {game ->
                MoneyType.values().associate { it to game.stock.moneys[it] }.plus("wildcards" to game.stock.wildcards)
            }
            value("nobles") {
                it.nobles.map {noble -> viewNoble(noble) }
            }
            value("players") {game ->
                game.players.mapIndexed { index, player ->
                    val reservedPair = if (index == viewer || game.config.showReservedCards)
                        "reservedCards" to player.reserved.map { viewCard(it) }
                            else "reserved" to player.reserved.size
                    mapOf(
                        "points" to player.points,
                        "money" to viewMoney(player.chips).filter { it.value > 0 },
                        "nobles" to player.nobles.cards.map { viewNoble(it) },
                        "discounts" to viewMoney(player.discounts()),
                        reservedPair
                    )
                }
            }
        }
    }

    private fun replaceCard(replayable: ReplayableScope, game: SplendorGame, card: SplendorCard) {
        game.board.card(card).remove()
        if (game.deck.cards.none { it.level == card.level }) return
        val state = replayable.string("card") {
            game.deck.cards.first { it.level == card.level }.toStateString()
        }
        val replacementCard = game.deck.findState(state) { it.toStateString() }
        game.deck.card(replacementCard).moveTo(game.board)
    }

    private fun isCurrentPlayer(action: Action<SplendorGame, *>): Boolean {
        return action.game.currentPlayerIndex == action.playerIndex
    }
}

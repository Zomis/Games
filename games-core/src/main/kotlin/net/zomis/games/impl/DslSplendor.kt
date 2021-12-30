package net.zomis.games.impl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.cards.CardZone
import net.zomis.games.common.mergeWith
import net.zomis.games.common.next
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.ReplayableScope
import kotlin.math.absoluteValue
import kotlin.math.max

data class SplendorPlayer(val index: Int) {
    var chips: Money = Money()
    val owned: CardZone<SplendorCard> = CardZone()
    val reserved: CardZone<SplendorCard> = CardZone()

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
    fun toMoney(): Money = Money(moneys.groupBy { it }.mapValues { it.value.size }.toMutableMap())
}

fun startingStockForPlayerCount(playerCount: Int): Int {
    return when (playerCount) {
        1 -> 4
        2 -> 4
        3 -> 5
        4 -> 7
        else -> throw IllegalArgumentException("Invalid number of players: $playerCount")
    }
}

class SplendorGame(val config: SplendorConfig, val eliminations: PlayerEliminationsWrite) {

    val allNobles = CardZone(listOf("BR", "UW", "UG", "RG", "BW", "BRG", "BUW", "BRW", "GUW", "GUR").map {string ->
        val moneyTypes = string.map { ch -> MoneyType.values().first { it.char == ch } }
        val requiredPerType = if (moneyTypes.size == 2) 4 else 3
        val money = moneyTypes.map { it.toMoney(requiredPerType) }.reduce { acc, money -> acc + money }
        SplendorNoble(3, money)
    }.toMutableList())

    val nobles = CardZone<SplendorNoble>()
    val deck = splendorCards.withIndex().flatMap { level -> splendorCardsFromMultilineCSV(level.index + 1, level.value) }
        .let { CardZone(it.shuffled().toMutableList()) }

    var turnsLeft = -1
    var roundNumber: Int = 1

    fun endTurnCheck(): SplendorNoble? {
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
        if (this.currentPlayer.chips.count > config.maxMoney) return null // Need to discard some money

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
            eliminations.eliminateBy(players.mapIndexed { index, splendorPlayer -> index to splendorPlayer }, compareBy({ it.points }, { -it.owned.size }))
        }
        return noble
    }

    val playerCount = eliminations.playerCount
    val players: List<SplendorPlayer> = (0 until playerCount).map { SplendorPlayer(it) }
    val board: CardZone<SplendorCard> = CardZone(mutableListOf())
    var stock: Money = MoneyType.values().fold(Money()) { money, type -> money + type.toMoney(startingStockForPlayerCount(playerCount))}.plus(Money(mutableMapOf(), 5))
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

    fun viewMoney(money: Money): Map<String, Int> {
        return money.moneys.entries.sortedBy { it.key.name }.map { it.key.name to it.value }
                .let { if (money.wildcards > 0) it.plus("wildcards" to money.wildcards) else it }.toMap()
    }
    fun viewNoble(game: SplendorGame, noble: SplendorNoble): Map<String, Any?> = mapOf(
        "points" to 3,
        "requirements" to viewMoney(noble.requirements),
        "id" to noble.toStateString(),
        "owner" to game.players.find { it.nobles.cards.contains(noble) }?.index
    )
    fun viewCard(card: SplendorCard): Map<String, Any?> {
        return mapOf(
                "id" to card.id,
                "level" to card.level,
                "discount" to viewMoney(card.discounts),
                "costs" to viewMoney(card.costs),
                "points" to card.points
        )
    }

    val factory = GameCreator(SplendorGame::class)

    val buy = factory.action("buy", SplendorCard::class).serializer { it.toStateString() }
    val buyReserved = factory.action("buyReserved", SplendorCard::class).serializer { it.toStateString() }
    val takeMoney = factory.action("takeMoney", MoneyChoice::class)
    val reserve = factory.action("reserve", SplendorCard::class).serializer { it.toStateString() }
    val discardMoney = factory.action("discardMoney", MoneyType::class)
    val splendorGame = factory.game("Splendor") {
        setup(SplendorConfig::class) {
            players(1..4)
            defaultConfig {
                SplendorConfig(
                        useNobles = true,
                        maxMoney = 10,
                        targetPoints = 15,
                        showReservedCards = false
                )
            }
            init {
                SplendorGame(config, eliminationCallback)
            }
        }
        actionRules {
            gameStart {
                val dealCards = (1..3).map { level -> game.deck.first(4) { card -> card.level == level } }.flatten()
                val cardStates = replayable.strings("cards") {
                    dealCards.map { c -> c.toStateString() }
                }
                val cards = game.deck.findStates(cardStates) { c -> c.toStateString() }
                game.deck.deal(cards, listOf(game.board))

                // Nobles
                if (game.config.useNobles) {
                    game.allNobles.random(replayable, game.players.size + 1, "nobles") { it.toStateString() }.forEach {
                        it.moveTo(game.nobles)
                    }
                }
            }

            allActions.precondition { game.currentPlayerIndex == playerIndex }

            action(buy) {
                options { game.board.cards }
                requires { game.currentPlayer.canBuy(action.parameter) }
                effect {
                    val card = action.parameter
                    val actualCost = game.currentPlayer.pay(card.costs)
                    game.currentPlayer.owned.cards.add(card)
                    game.stock += actualCost
                    replaceCard(replayable, game, card)
                    log { "$player bought ${viewLink("card", "card", viewCard(card))}" }
                }
            }

            action(buyReserved).options { game.currentPlayer.reserved.cards }
            action(buyReserved).requires { game.currentPlayer.canBuy(action.parameter) }
            action(buyReserved).effect {
                val param = action.parameter
                val card = game.currentPlayer.reserved.card(param)
                val actualCost = game.currentPlayer.pay(card.card.costs)
                game.stock += actualCost
                card.moveTo(game.currentPlayer.owned)
                log { "$player bought ${viewLink("a reserved card", "card", viewCard(card.card))}" }
            }

            action(discardMoney) {
                forceWhen { game.currentPlayer.chips.count > game.config.maxMoney }
                options { MoneyType.values().toList() }
                requires { game.currentPlayer.chips.hasWithoutWildcards(action.parameter.toMoney(1)) }
                effect {
                    val money = action.parameter.toMoney(1)
                    game.currentPlayer.chips -= money
                    game.stock += money
                    log { "$player discards $action" }
                }
            }

            action(reserve).options { game.board.cards }
            action(reserve).requires { game.currentPlayer.reserved.size < 3 }
            action(reserve).effect {
                val card = game.board.card(action.parameter)
                game.currentPlayer.reserved.cards.add(card.card)
                val wildcardIfAvailable = if (game.stock.wildcards > 0) Money(mutableMapOf(), 1) else Money()
                game.stock -= wildcardIfAvailable
                game.currentPlayer.chips += wildcardIfAvailable
                replaceCard(replayable, game, card.card)
                logSecret(action.playerIndex) { "$player reserved ${viewLink("card", "card", viewCard(card.card))}" }
                    .publicLog { "$player reserved a level ${card.card.level} card" }
            }

            action(takeMoney).choose {
                recursive(emptyList<MoneyType>()) {
                    options({
                        val all = MoneyType.values().asIterable()
                        if (chosen.distinct().size == 2) all - chosen.toSet() else all
                    }) { moneyType ->
                        recursion(moneyType) { list, e -> list + e }
                    }
                    until { chosen.size == 3 || (chosen.size == 2 && chosen.distinct().size == 1) }
                    parameter { MoneyChoice(chosen) }
                    intermediateParameter { chosen.size in 1..3 }
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
                log { "$player took ${action.moneys}" }
            }

            allActions.after {
                val noble = game.endTurnCheck()
                if (noble != null) {
                    log { "$player got ${viewLink("a noble", "noble", viewNoble(game, noble))}" }
                }
            }
            view("currentPlayer") { game.currentPlayerIndex }
            view("viewer") { viewer }
            view("round") { game.roundNumber }
            view("cardLevels") {
                game.board.cards.sortedBy { -it.level }.groupBy { it.level }.mapValues {
                    mapOf(
                            "level" to it.key,
                            "remaining" to game.deck.cards.count { c -> c.level == it.key },
                            "board" to it.value.map { c -> viewCard(c) }
                    )
                }.values.toList()
            }
            view("stock") {
                MoneyType.values().associate { it to game.stock.moneys[it] }.plus("wildcards" to game.stock.wildcards)
            }
            view("nobles") {
                val allNobles = game.players.flatMap { pl -> pl.nobles.cards } + game.nobles.cards
                allNobles.map { noble -> viewNoble(game, noble) }
            }
            view("players") {
                game.players.map { player ->
                    val reservedPair = if (player.index == viewer || game.config.showReservedCards)
                        "reservedCards" to player.reserved.map { viewCard(it) }
                    else "reserved" to player.reserved.size
                    mapOf(
                            "points" to player.points,
                            "money" to viewMoney(player.chips).filter { it.value > 0 },
                            "nobles" to player.nobles.cards.map { viewNoble(game, it) },
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

}

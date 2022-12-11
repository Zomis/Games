package net.zomis.games.impl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
import net.zomis.games.components.resources.GameResource
import net.zomis.games.components.resources.MutableResourceMap
import net.zomis.games.components.resources.ResourceMap
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.ReplayStateI
import net.zomis.games.metrics.MetricBuilder
import kotlin.math.max

data class SplendorPlayer(val index: Int) {
    var chips: MutableResourceMap = ResourceMap.empty().toMutableResourceMap()
    val owned: CardZone<SplendorCard> = CardZone()
    val reserved: CardZone<SplendorCard> = CardZone()

    val nobles = CardZone<SplendorNoble>()
    val points: Int get() = owned.cards.sumOf { it.points } + nobles.cards.sumOf { it.points }

    fun total(): ResourceMap = this.discounts() + this.chips

    fun canBuy(card: SplendorCard): Boolean {
        val have = this.total()
        val diff = have - card.costs
        val wildcardsNeeded = diff.negativeAmount()
        return have.getOrDefault(MoneyType.WILDCARD) >= wildcardsNeeded
    }

    fun pay(costs: ResourceMap): ResourceMap {
        val chipCosts = (costs - this.discounts()).map { it.resource to max(it.value, 0) }
        val remaining = (this.chips - chipCosts)
        val wildcardsNeeded = remaining.negativeAmount()
        val actualCosts = this.chips - remaining.map { it.resource to max(it.value, 0) } + MoneyType.WILDCARD.toMoney(wildcardsNeeded)

        val tokensExpected = chipCosts.count()
        val oldMoney = this.chips.toMutableResourceMap()

        this.chips -= actualCosts
        if (actualCosts.negativeAmount() > 0) {
            throw IllegalStateException("Actual costs has negative: $oldMoney --> ${this.chips}. Cost was $chipCosts. Actual $actualCosts")
        }
        if (oldMoney.count() - this.chips.count() != tokensExpected) {
            throw IllegalStateException("Wrong amount of tokens were taken: $oldMoney --> ${this.chips}. Cost was $chipCosts")
        }
        return actualCosts
    }

    fun discounts(): ResourceMap = this.owned.map { it.discounts }.fold(ResourceMap.empty(), ResourceMap::plus)

}

private fun ResourceMap.negativeAmount(): Int = this.filter { it.value < 0 }.unaryMinus().count()

data class SplendorCard(val level: Int, val discounts: ResourceMap, val costs: ResourceMap, val points: Int) {
    // Possible to support multiple discounts on the same card. Because why not!
    constructor(level: Int, discount: MoneyType, costs: ResourceMap, points: Int):
            this(level, ResourceMap.of(discount to 1), costs, points)

    val id: String get() = toStateString()

    fun toStateString(): String {
        return "$level:$points:${discounts.toStateString()}.${costs.toStateString()}"
    }
}

data class SplendorNoble(val points: Int, val requirements: ResourceMap) {
    fun requirementsFulfilled(player: SplendorPlayer): Boolean {
        return player.discounts().has(requirements)
    }

    fun toStateString(): String {
        return "$points:${requirements.toStateString()}"
    }
}

enum class MoneyType(val char: Char): GameResource {
    WHITE('W'), BLUE('U'), BLACK('B'), RED('R'), GREEN('G'),
    WILDCARD('*');

    fun toMoney(count: Int) = this.toResourceMap(count)

    companion object {
        fun withoutWildcard(): List<MoneyType> = MoneyType.values().toList().minus(WILDCARD)
    }
}

data class MoneyChoice(val moneys: List<MoneyType>) {
    fun toMoney() = ResourceMap.fromList(moneys)
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
        val totalChipsInGame = this.stock + this.players.fold(ResourceMap.empty()) { a, b -> a.plus(b.chips) }
        if (totalChipsInGame[MoneyType.WILDCARD] != 5) throw IllegalStateException("Wrong amount of total wildcards: $totalChipsInGame")
        if (totalChipsInGame.entries().any { it.resource != MoneyType.WILDCARD && it.value != startingStockForPlayerCount(players.size) }) {
            throw IllegalStateException("Wrong amount of total chips: $totalChipsInGame")
        }

        // Check money count > maxMoney
        if (this.currentPlayer.chips.count() > config.maxMoney) return null // Need to discard some money

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
    var stock: ResourceMap = MoneyType.withoutWildcard().fold(ResourceMap.empty()) { money, type -> money + type.toMoney(startingStockForPlayerCount(playerCount))}.plus(MoneyType.WILDCARD.toMoney(5))
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

    fun viewMoney(money: ResourceMap): Map<String, Int> {
        return money.entries().sortedBy { it.resource.name }.associate { it.resource.name to it.value }
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
                forceWhen { game.currentPlayer.chips.count() > game.config.maxMoney }
                options { MoneyType.values().toList() }
                requires { game.currentPlayer.chips.has(action.parameter.toMoney(1)) }
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
                val wildcardIfAvailable = if (game.stock.getOrDefault(MoneyType.WILDCARD) > 0) MoneyType.WILDCARD.toMoney(1) else ResourceMap.empty()
                game.stock -= wildcardIfAvailable
                game.currentPlayer.chips += wildcardIfAvailable
                replaceCard(replayable, game, card.card)
                logSecret(action.playerIndex) { "$player reserved ${viewLink("card", "card", viewCard(card.card))}" }
                    .publicLog { "$player reserved a level ${card.card.level} card" }
            }

            action(takeMoney).choose {
                recursive(emptyList<MoneyType>()) {
                    options({
                        val all = MoneyType.withoutWildcard().asIterable()
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
                if (moneyChosen.getOrDefault(MoneyType.WILDCARD) >= 1) return@requires false
                if (!game.stock.has(moneyChosen)) {
                    return@requires false
                }
                val chosen = action.parameter.moneys
                return@requires when (chosen.size) {
                    1 -> true
                    2 -> chosen.distinct().size != 1 || game.stock.has(moneyChosen.plus(moneyChosen))
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
                MoneyType.values().associateWith { game.stock.getOrDefault(it) }
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

        val buyCard = scorers.isAction(buy)
        val buyReserved = scorers.isAction(buyReserved)
        val takeMoneyNeeded = scorers.action(takeMoney) { action.parameter.moneys.size.toDouble() }
        val reserve = scorers.isAction(reserve)
        val discard = scorers.isAction(discardMoney)

        data class CardDesiredness(val round: Int, val points: Int, val discountUsefulness: Int, val nobles: Int) {
            val value = (points - 0.5) * (0.05 * round) + discountUsefulness * 0.2 + nobles * 0.3
        }
        val cardDesiredness = scorers.provider { ctx ->
            // card desiredness -- points, discount needed, usefulness for nobles
            val player = ctx.model.players[ctx.playerIndex]
            ctx.model.board.cards.associateWith { card ->
                val discount = card.discounts.entries().single()
                check(discount.value == 1)
                val discountType = discount.resource
                val playerHas = player.owned.cards.sumOf { it.discounts.getOrDefault(discountType) }
                val otherCardsRequiresDiscountType = ctx.model.board.cards.minus(card).count {
                    val money = player.discounts() + player.chips
                    val remaining = it.costs - money
                    remaining.getOrDefault(discountType) > 0
                }
                val nobles = ctx.model.nobles.cards.count { noble ->
                    val requires = noble.requirements.getOrDefault(discountType)
                    requires > playerHas
                }
                CardDesiredness(ctx.model.roundNumber, card.points, otherCardsRequiresDiscountType, nobles)
            }
        }

        data class CardObtainability(val remainingCost: ResourceMap) {
            fun turnsLeftUntilBuy(): Int {
                val remainingTotal = remainingCost.count() / 3
                val remainingMax = remainingCost.entries().maxOfOrNull { it.value } ?: 0
                return maxOf(remainingTotal, remainingMax)
            }
            val value = (1.0 - (turnsLeftUntilBuy() / 10.0)).let { it * it }
        }
        val cardObtainability = scorers.provider { ctx ->
            // card obtainability. number of money remaining. number of money totally available (how much of the money will I be able to obtain?). requires wildcards?
            val player = ctx.model.players[ctx.playerIndex]
            val playerHas = player.discounts() + player.chips
            ctx.model.board.cards.associateWith { card ->
                CardObtainability(card.costs.minus(playerHas).filter { it.value >= 0 })
            }
        }
        val buyObtainable = scorers.action(buy) {
            val desiredness = require(cardDesiredness)!![action.parameter]!!
            val obtainability = require(cardObtainability)!![action.parameter]!!
            desiredness.value * obtainability.value
        }
        val takeMoneyToObtain = scorers.action(takeMoney) {
            // Check obtainability and desiredness of cards, and calculate which money to take from that.
            val desiredness = require(cardDesiredness)!!
            val obtainability = require(cardObtainability)!!
            val chosen = action.parameter.toMoney()
            this.model.board.cards.sumOf {
                CardObtainability(obtainability.getValue(it).remainingCost - chosen).value * desiredness.getValue(it).value
            }
        }
        scorers.ai("#AI_Hard_Dev", buyObtainable.weight(100), takeMoneyToObtain, reserve.weight(-1), discard)

        // wildcards desiredness combined with card desiredness, combined with sabotage potential, leads to possible reservation
        // have lots of money --> less desire to take more money
        // have little money --> want money

        scorers.ai("#AI_BuyFirst",
            buyCard, buyReserved, reserve.weight(-1), takeMoneyNeeded.weight(0.1), discard)
    }

    class Metrics(builder: MetricBuilder<SplendorGame>) {
        val pointsDiff = builder.endGameMetric { game.players.maxOf { it.points } - game.players.minOf { it.points } }
        val points = builder.endGamePlayerMetric {
            game.players[playerIndex].points
        }
        val moneyTaken = builder.actionMetric(takeMoney) {
            action.parameter.toMoney().toMap()
        }
        val cardCosts = builder.actionMetric(buy) {
            action.parameter.costs.toMap()
        }
        val moneyPaid = builder.actionMetric(buy) {
            (action.parameter.costs - game.players[action.playerIndex].discounts()).filter { it.value >= 0 }.toMap()
        } // + actionMetric(DslSplendor.buyReserved)...?
        val noblesGotten = builder.endGamePlayerMetric {
            game.players[playerIndex].nobles.size
        }
        val gamesWon = builder.endGamePlayerMetric {
            eliminations.eliminationFor(playerIndex)!!
        }
        val discarded = builder.actionMetric(discardMoney) {
            action.parameter.toMoney(1).toMap()
        }
    }

    private fun replaceCard(replayable: ReplayStateI, game: SplendorGame, card: SplendorCard) {
        game.board.card(card).remove()
        if (game.deck.cards.none { it.level == card.level }) return
        val state = replayable.string("card") {
            game.deck.cards.first { it.level == card.level }.toStateString()
        }
        val replacementCard = game.deck.findState(state) { it.toStateString() }
        game.deck.card(replacementCard).moveTo(game.board)
    }

}

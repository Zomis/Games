package net.zomis.games.impl

import net.zomis.games.cards.CardZone
import net.zomis.games.common.mergeWith
import net.zomis.games.common.next
import net.zomis.games.dsl.*
import kotlin.math.absoluteValue

object SpiceRoadDsl {
    data class PlayParameter(val card: SpiceRoadGameModel.ActionCard, val remove: SpiceRoadGameModel.Caravan, val add: SpiceRoadGameModel.Caravan)
    data class AcquireParameter(val card: SpiceRoadGameModel.ActionCard, val payArray: List<SpiceRoadGameModel.Spice>)

    val factory = GameCreator(SpiceRoadGameModel::class)
    val play = factory.action("play", PlayParameter::class).serializer(String::class) { "Play Card " + it.card.toStateString() + " Remove " + it.remove.toStateString() + " Add " + it.add.toStateString() }
    val claim = factory.action("claim", SpiceRoadGameModel.PointCard::class).serializer(String::class) { "Claim " + it.toStateString()}
    val rest = factory.action("rest", Unit::class)
    val acquire = factory.action("acquire", AcquireParameter::class).serializer(String::class) {
        "Acquire Card " + it.card.toStateString() + " PayArray " + it.payArray.joinToString("") { x -> x.char.toString() }
    }
    val discard = factory.action("discard", SpiceRoadGameModel.Spice::class).serializer(String::class){"Discard " + it.char}
    val game = factory.game("Spice Road") {
        this.setup {
            this.players(2..5)
            this.init {
                SpiceRoadGameModel(this.playerCount)
            }
        }
        this.rules {
            this.gameStart {
                game.actionDeck.random(this.replayable, 6, "DealActionCards") { it.toStateString() }.forEach { it.moveTo(game.visibleActionCards) }
                game.pointsDeck.random(this.replayable, 5, "DealPointCards") { it.toStateString() }.forEach { it.moveTo(game.visiblePointCards) }
            }
            this.view("players") { game.players.map(SpiceRoadGameModel.Player::toViewable) }
            this.view("actionDeck") { game.actionDeck.size }
            this.view("pointsDeck") { game.pointsDeck.size }
            this.view("actionCards") { game.visibleActionCards.cards.map(SpiceRoadGameModel.ActionCard::toViewable) }
            this.view("pointCards") { game.visiblePointCards.cards.map(SpiceRoadGameModel.PointCard::toViewable) }
            this.view("goldCoins") { game.goldCoins.size }
            this.view("silverCoins") { game.silverCoins.size }
            this.action(claim).requires { game.currentPlayer.caravan.has(this.action.parameter.cost) }
            this.action(claim).effect {
                game.currentPlayer.points += when (game.visiblePointCards.cards.indexOf(this.action.parameter)) {
                    0 -> if (game.goldCoins.isNotEmpty()) 3 else if (game.silverCoins.isNotEmpty()) 1 else 0
                    1 -> if (game.goldCoins.isNotEmpty() && game.silverCoins.isNotEmpty()) 1 else 0
                    else -> 0
                }
                game.currentPlayer.caravan -= this.action.parameter.cost
                game.currentPlayer.points += this.action.parameter.points
                game.currentPlayer.pointCards++
                game.visiblePointCards.card(this.action.parameter).remove()
                if (game.pointsDeck.size > 0) {
                    game.pointsDeck.random(this.replayable, 1, "NewPointCard") { it.toStateString() }.forEach { it.moveTo(game.visiblePointCards) }
                }
            }
            this.action(claim).options { game.visiblePointCards.cards }
            this.action(rest).requires { game.currentPlayer.discard.size > 0 }
            this.action(rest).effect { game.currentPlayer.discard.moveAllTo(game.currentPlayer.hand) }
            this.action(play).effect {
                game.currentPlayer.caravan -= this.action.parameter.remove
                game.currentPlayer.caravan += this.action.parameter.add
                game.currentPlayer.hand.card(this.action.parameter.card).moveTo(game.currentPlayer.discard)
            }
            this.action(play).choose {
                options({ game.currentPlayer.hand.cards }) { card ->
                    when {
                        card.gain != null -> parameter(PlayParameter(card, SpiceRoadGameModel.Caravan(), card.gain))
                        card.upgrade != null -> {
                            fun rec(scope: ActionChoicesNextScope<SpiceRoadGameModel, PlayParameter>,
                                    remaining: SpiceRoadGameModel.Caravan,
                                    upgrades: Int,
                                    remove: SpiceRoadGameModel.Caravan = SpiceRoadGameModel.Caravan(),
                                    add: SpiceRoadGameModel.Caravan = SpiceRoadGameModel.Caravan()) {
                                scope.parameter(PlayParameter(card, remove, add))
                                if (upgrades <= 0) {
                                    return
                                }
                                scope.options({ remaining.spice.keys - SpiceRoadGameModel.Spice.BROWN }) { spiceToUpgrade ->
                                    this.options({ 1..(minOf(SpiceRoadGameModel.Spice.BROWN.ordinal - spiceToUpgrade.ordinal, upgrades)) }) { times ->
                                        rec(this, remaining - spiceToUpgrade.toCaravan(),
                                                upgrades - times,
                                                remove + spiceToUpgrade.toCaravan(),
                                                add + (spiceToUpgrade + times).toCaravan())
                                    }
                                }
                            }
                            rec(this, context.game.currentPlayer.caravan, card.upgrade)
                        }
                        card.trade != null -> options({ 1..(game.currentPlayer.caravan / card.trade.first) }) { times ->
                            parameter(PlayParameter(card, card.trade.first * times, card.trade.second * times))
                        }
                    }
                }
            }
            this.action(acquire).effect {
                game.currentPlayer.caravan -= action.parameter.payArray.fold(SpiceRoadGameModel.Caravan(), { acc, x -> acc + x.toCaravan() })
                game.visibleActionCards.cards.mapIndexed { index, card -> card.addSpice(action.parameter.payArray.getOrNull(index)) }

                game.currentPlayer.caravan += action.parameter.card.takeAllSpice()
                game.visibleActionCards.card(action.parameter.card).moveTo(game.currentPlayer.hand)

                if (game.actionDeck.size > 0) {
                    game.actionDeck.random(this.replayable, 1, "NewActionCard") { it.toStateString() }.forEach { it.moveTo(game.visibleActionCards) }
                }
            }
            this.action(acquire).choose {
                options({ game.visibleActionCards.cards.filterIndexed { index, _ -> index <= game.currentPlayer.caravan.count } }) { card ->
                    fun rec(scope: ActionChoicesNextScope<SpiceRoadGameModel, AcquireParameter>,
                            remaining: SpiceRoadGameModel.Caravan,
                            leftToPay: Int,
                            payList: List<SpiceRoadGameModel.Spice> = emptyList()
                    ) {
                        if (leftToPay <= 0) {
                            scope.parameter(AcquireParameter(card, payList))
                            return
                        }
                        scope.options({ remaining.spice.keys }) { payWith ->
                            rec(this, remaining - payWith.toCaravan(), leftToPay - 1, payList + payWith)
                        }
                    }
                    rec(this, context.game.currentPlayer.caravan, context.game.visibleActionCards.card(card).index)
                }
            }
            this.action(discard).forceUntil { game.currentPlayer.caravan.count <= 10 }
            this.action(discard).precondition { game.currentPlayer.caravan.count > 10 }
            this.action(discard).options { game.currentPlayer.caravan.spice.keys }
            this.action(discard).effect {
                game.currentPlayer.caravan -= this.action.parameter.toCaravan()
            }
            this.allActions.precondition { game.currentPlayer.index == playerIndex }
            this.allActions.after {
                val gameEnd = when (game.playerCount) {
                    1, 2, 3 -> game.currentPlayer.pointCards == 6
                    else -> game.currentPlayer.pointCards == 5
                }
                if (gameEnd) {
                    when (game.turnsLeft) {
                        -1 -> game.turnsLeft = game.playerCount - (game.currentPlayerIndex + 1)
                        0 -> this.eliminations.eliminateBy(game.players.mapIndexed { index, player -> index to player }, compareBy({ it.points }, { +it.index }))
                        else -> game.turnsLeft--
                    }
                }
            }
            this.allActions.after { game.currentPlayerIndex = game.currentPlayerIndex.next(game.playerCount) }
        }
    }
}

class SpiceRoadGameModel(val playerCount: Int) {
    //Turn: Action -> Caravan Limit (discard to hand size) (-> Game end trigger check)
    //Actions: acquire, claim, rest, play
    var turnsLeft = -1
    var currentPlayerIndex = 0
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val players = (0 until playerCount).map { Player(it) }
    val pointsDeck = CardZone<PointCard>(pointsCards.split("\n").map { x -> x.split(",") }.map { (x, y) -> PointCard(x.toInt(), y.toCaravan()!!) }.toMutableList())
    val actionDeck = CardZone<ActionCard>(actionCards.split("\n").map { x -> x.split(",") }
            .map { (x, y, z) ->
                ActionCard(
                        x.toIntOrNull(),
                        y.toCaravan(),
                        z.takeIf { it.isNotEmpty() }?.split("->")?.let { (a, b) -> Pair(a.toCaravan()!!, b.toCaravan()!!) }
                )
            }.toMutableList()
    )
    val visiblePointCards = CardZone<PointCard>()
    val visibleActionCards = CardZone<ActionCard>()
    val goldCoins = (0 until playerCount * 2).map { Coin(3) }
    val silverCoins = (0 until playerCount * 2).map { Coin(1) }

    class ActionCard(val upgrade: Int?, val gain: Caravan?, val trade: Pair<Caravan, Caravan>?) {
        var spiceOnMe = Caravan()
        fun toStateString(): String = "$upgrade $gain ${trade?.first}->${trade?.second}"

        fun toViewable(): Map<String, Any?> = mapOf("upgrade" to upgrade, "gain" to gain?.toViewable(), "trade" to if (trade == null) null else mapOf("give" to trade.first.toViewable(), "get" to trade.second.toViewable()), "bonusSpice" to spiceOnMe.toViewable())

        fun addSpice(spice: Spice?) {
            if (spice != null) {
                spiceOnMe += spice.toCaravan()
            }
        }

        fun takeAllSpice(): Caravan {
            val tmp = spiceOnMe
            spiceOnMe = Caravan()
            return tmp
        }
    }

    class PointCard(val points: Int, val cost: Caravan) {
        fun toStateString(): String = "$points $cost"

        fun toViewable(): Map<String, Any> = mapOf("points" to points, "cost" to cost.toViewable())
    }

    class Coin(val points: Int)

    class Player(val index: Int) {
        var caravan = when (index) {
            0 -> Caravan(Spice.YELLOW to 3)
            1, 2 -> Caravan(Spice.YELLOW to 4)
            3, 4 -> Caravan(Spice.YELLOW to 3, Spice.RED to 1)
            else -> throw Exception("You have created a game with too many players")
        }
        val discard = CardZone<ActionCard>()
        val hand = CardZone<ActionCard>(mutableListOf(
                ActionCard(2, null, null),
                ActionCard(null, Spice.YELLOW.toCaravan(2), null))
        )
        var points = 0
        var pointCards = 0

        fun toViewable(): Map<String, Any?> {
            return mapOf(
                    "caravan" to caravan.toViewable(),
                    "discard" to discard.cards.map { it.toViewable() },
                    "hand" to hand.cards.map { it.toViewable() },
                    "points" to points,
                    "index" to index
            )
        }
    }

    enum class Spice(val char: Char) {
        YELLOW('Y'), RED('R'), GREEN('G'), BROWN('B');

        operator fun plus(upgrade: Int): Spice {
            return Spice.values()[minOf(this.ordinal + upgrade, Spice.BROWN.ordinal)]
        }

        fun toCaravan(count: Int = 1): Caravan {
            return Caravan(mutableMapOf(this to count))
        }
    }

    private fun String.toCaravan(): Caravan? {
        return if (this.isEmpty()) null else
            this.groupBy { it }.mapValues { it.value.size }.map { Spice.values().find { x -> it.key == x.char }!!.toCaravan(it.value) }.fold(Caravan(), Caravan::plus)
    }

    data class Caravan(val spice: MutableMap<Spice, Int> = mutableMapOf()) {

        val count: Int = spice.values.sum()

        constructor(vararg spices: Pair<Spice, Int>) : this(mutableMapOf<Spice, Int>(*spices))

        operator fun plus(other: Caravan): Caravan {
            val result = spice.mergeWith(other.spice) { a, b -> (a ?: 0) + (b ?: 0) }
            return Caravan(result.toMutableMap())
        }

        fun has(costs: Caravan): Boolean {
            val diff = this - costs
            return diff.spice.all { it.value >= 0 }
        }

        fun map(mapping: (Pair<Spice, Int>) -> Pair<Spice, Int>): Caravan {
            var result = Caravan()
            spice.forEach { pair -> result += Caravan(mutableMapOf(mapping(pair.key to pair.value))) }
            return Caravan(result.spice.toMutableMap())
        }

        operator fun times(times: Int): Caravan {
            var tmp = Caravan()
            for (i in 1..times) {
                tmp += this
            }
            return tmp
        }

        fun negativeAmount(): Int = spice.values.filter { it < 0 }.sum().absoluteValue

        operator fun minus(other: Caravan): Caravan {
            val result = spice.mergeWith(other.spice) { a, b -> (a ?: 0) - (b ?: 0) }
            return Caravan(result.toMutableMap())
        }

        operator fun div(other: Caravan): Int {
            var times = 0
            var tmp = this
            while (tmp.has(other)) {
                times += 1
                tmp -= other
            }
            return times
        }

        fun toStateString(): String {
            return spice.entries.sortedBy { it.key.char }.joinToString("") { it.key.char.toString().repeat(it.value) }
        }

        fun toViewable(): Map<String, Int> {
            return this.spice.entries.sortedBy { it.key.name }.map { it.key.name to it.value }.toMap()
        }
    }
}

package net.zomis.games.impl

import net.zomis.games.cards.CardZone
import net.zomis.games.common.mergeWith
import net.zomis.games.common.next
import net.zomis.games.dsl.*
import kotlin.math.absoluteValue

object SpiceRoadDsl {
    data class PlayParameter(val card: SpiceRoadGameModel.ActionCard, val remove: SpiceRoadGameModel.Caravan, val add: SpiceRoadGameModel.Caravan)
    data class AcquireParameter(val card: SpiceRoadGameModel.ActionCard, val payArray: List<SpiceRoadGameModel.Spice>)

    private fun List<SpiceRoadGameModel.Spice>.toCaravan(): SpiceRoadGameModel.Caravan
        = this.fold(SpiceRoadGameModel.Caravan()) { acc, spice -> acc + spice.toCaravan() }

    val factory = GameCreator(SpiceRoadGameModel::class)
    val play = factory.action("play", PlayParameter::class).serializer {
        "Play Card " + it.card.toStateString() + " Remove " + it.remove.toStateString() + " Add " + it.add.toStateString()
    }
    val claim = factory.action("claim", SpiceRoadGameModel.PointCard::class).serializer { it.toStateString() }
    val rest = factory.action("rest", Unit::class)
    val acquire = factory.action("acquire", AcquireParameter::class).serializer {
        "Acquire Card " + it.card.toStateString() + " PayArray " + it.payArray.joinToString("") { x -> x.char.toString() }
    }
    val discard = factory.action("discard", SpiceRoadGameModel.Spice::class).serializer {"Discard " + it.char}
    val game = factory.game("Spice Road") {
        this.setup {
            this.players(2..5)
            this.init {
                SpiceRoadGameModel(this.playerCount)
            }
        }
        this.actionRules {
            this.gameStart {
                game.actionDeck.random(this.replayable, 6, "DealActionCards") { it.toStateString() }.forEach { it.moveTo(game.visibleActionCards) }
                game.pointsDeck.random(this.replayable, 5, "DealPointCards") { it.toStateString() }.forEach { it.moveTo(game.visiblePointCards) }
            }
            this.view("currentPlayer") { game.currentPlayerIndex }
            this.view("players") { game.players.map(SpiceRoadGameModel.Player::toViewable) }
            this.view("actionDeck") { game.actionDeck.size }
            this.view("pointsDeck") { game.pointsDeck.size }
            this.view("actionCards") { game.visibleActionCards.cards.map(SpiceRoadGameModel.ActionCard::toViewable) }
            this.view("pointCards") {
                game.visiblePointCards.cards.mapIndexed { index, pointCard ->
                    val coinMap = when (index) {
                        0 -> mapOf("goldCoins" to game.goldCoins.size)
                        1 -> mapOf("silverCoins" to game.silverCoins.size)
                        else -> emptyMap()
                    }
                    pointCard.toViewable() + coinMap
                }
            }
            view("round") { game.round }
            action(claim) {
                requires { game.currentPlayer.caravan.has(this.action.parameter.cost) }
                options { game.visiblePointCards.cards }
                effect {
                    val coin = when (game.visiblePointCards.cards.indexOf(this.action.parameter)) {
                        0 -> game.goldCoins.firstOrNull() ?: game.silverCoins.firstOrNull()
                        1 -> game.silverCoins.firstOrNull()?.takeIf { game.goldCoins.isNotEmpty() }
                        else -> null
                    }
                    game.goldCoins.remove(coin)
                    game.silverCoins.remove(coin)
                    if (coin != null) game.currentPlayer.coins.add(coin)

                    game.currentPlayer.caravan -= this.action.parameter.cost
                    game.visiblePointCards.card(this.action.parameter).moveTo(game.currentPlayer.pointCards)
                    if (game.pointsDeck.size > 0) {
                        game.pointsDeck.random(this.replayable, 1, "NewPointCard") { it.toStateString() }.forEach { it.moveTo(game.visiblePointCards) }
                    }
                }
            }
            action(rest) {
                requires { game.currentPlayer.discard.size > 0 }
                effect { game.currentPlayer.discard.moveAllTo(game.currentPlayer.hand) }
            }
            action(play) {
                effect {
                    game.currentPlayer.caravan -= this.action.parameter.remove
                    game.currentPlayer.caravan += this.action.parameter.add
                    game.currentPlayer.hand.card(this.action.parameter.card).moveTo(game.currentPlayer.discard)
                }
                requires {
                    game.currentPlayer.caravan.has(action.parameter.remove)
                }
                fun removeUpgrades(chosen: Pair<SpiceRoadGameModel.Caravan, Int>): SpiceRoadGameModel.Caravan {
                    return chosen.first.filter { it.value < 0 }.map { it.first to it.second.times(-1) }
                }

                choose {
                    optionsWithIds({ game.currentPlayer.hand.cards.map { it.toStateString() to it } }) { card ->
                        when {
                            card.gain != null -> parameter(PlayParameter(card, SpiceRoadGameModel.Caravan(), card.gain))
                            card.upgrade != null -> {
                                val upgrades = card.upgrade
                                recursive(SpiceRoadGameModel.Caravan() to upgrades) {
                                    intermediateParameter { true }
                                    parameter { PlayParameter(card, removeUpgrades(chosen), chosen.first.filter { it.value > 0 }) }
                                    until { chosen.second == 0 }
                                    options({ (game.currentPlayer.caravan - removeUpgrades(chosen)).remainingKeys() - SpiceRoadGameModel.Spice.BROWN }) { spiceToUpgrade ->
                                        options({ 1..(minOf(SpiceRoadGameModel.Spice.BROWN.ordinal - spiceToUpgrade.ordinal, chosen.second)) }) { times ->
                                            val caravan = spiceToUpgrade.toCaravan(-1) + spiceToUpgrade.upgrade(times)
                                            recursion(caravan to times) { previous, e ->
                                                (previous.first + e.first) to (previous.second - e.second)
                                            }
                                        }
                                    }
                                }
                            }
                            card.trade != null -> options({ 1..(game.currentPlayer.caravan / card.trade.first) }) { times ->
                                parameter(PlayParameter(card, card.trade.first * times, card.trade.second * times))
                            }
                        }
                    }
                }
            }
            action(acquire) {
                requires {
                    game.currentPlayer.caravan.has(action.parameter.payArray.fold(SpiceRoadGameModel.Caravan(), { acc, x -> acc + x.toCaravan() }))
                }
                effect {
                    game.currentPlayer.caravan -= action.parameter.payArray.fold(SpiceRoadGameModel.Caravan(), { acc, x -> acc + x.toCaravan() })
                    game.visibleActionCards.cards.mapIndexed { index, card -> card.addSpice(action.parameter.payArray.getOrNull(index)) }

                    game.currentPlayer.caravan += action.parameter.card.takeAllSpice()
                    game.visibleActionCards.card(action.parameter.card).moveTo(game.currentPlayer.hand)

                    if (game.actionDeck.size > 0) {
                        game.actionDeck.random(this.replayable, 1, "NewActionCard") { it.toStateString() }.forEach { it.moveTo(game.visibleActionCards) }
                    }
                }
                choose {
                    optionsWithIds({
                        game.visibleActionCards.cards.filterIndexed { index, _ -> index <= game.currentPlayer.caravan.count }
                                .map { it.toStateString() to it }
                    }) { card ->
                        recursive(emptyList<SpiceRoadGameModel.Spice>()) {
                            until { chosen.size == game.visibleActionCards.card(card).index }
                            options({ (game.currentPlayer.caravan - chosen.toCaravan()).spice.filter { it.value > 0 }.keys }) {
                                recursion(it) { list, e -> list + e }
                            }
                            parameter { AcquireParameter(card, chosen) }
                        }
                    }
                }
            }
            action(discard) {
                forceWhen { game.currentPlayer.caravan.count > 10 }
                options { game.currentPlayer.caravan.spice.keys.filter { game.currentPlayer.caravan.has(it.toCaravan()) } }
                requires { game.currentPlayer.caravan.has(action.parameter.toCaravan()) }
                effect {
                    game.currentPlayer.caravan -= this.action.parameter.toCaravan()
                }
            }
            allActions.precondition { game.currentPlayer.index == playerIndex }
            allActions.after {
                val gameEnd = when (game.playerCount) {
                    1, 2, 3 -> game.players.any { player -> player.pointCards.size >= 6 }
                    else -> game.currentPlayer.pointCards.size >= 5
                }
                if (gameEnd && game.currentPlayerIndex == game.playerCount - 1) {
                   this.eliminations.eliminateBy(game.players.mapIndexed { index, player -> index to player }, compareBy({ it.points }, { +it.index }))
                }
            }
            allActions.after {
                if (game.players[playerIndex].caravan.count <= 10) {
                    game.currentPlayerIndex = game.currentPlayerIndex.next(game.playerCount)
                    if (game.currentPlayerIndex == 0) game.round++
                }
            }
        }
        val buyScorer = scorers.isAction(claim)
        scorers.ai("#AI_BuyFirst", buyScorer)
    }
}

class SpiceRoadGameModel(val playerCount: Int) {
    var round: Int = 1

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
    val goldCoins = (0 until playerCount * 2).map { Coin(3) }.toMutableList()
    val silverCoins = (0 until playerCount * 2).map { Coin(1) }.toMutableList()

    class ActionCard(val upgrade: Int?, val gain: Caravan?, val trade: Pair<Caravan, Caravan>?) {
        var spiceOnMe = Caravan()
        fun toStateString(): String = "$upgrade $gain ${trade?.first}->${trade?.second}"

        fun toViewable(): Map<String, Any?> = mapOf(
            "upgrade" to upgrade,
            "gain" to gain?.toViewable(),
            "trade" to if (trade == null) null else mapOf(
                "give" to trade.first.toViewable(),
                "get" to trade.second.toViewable()
            ),
            "id" to toStateString(),
            "bonusSpice" to if (spiceOnMe.count == 0) null else spiceOnMe.toViewable()
        )

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

        fun toViewable(): Map<String, Any> = mapOf("points" to points, "cost" to cost.toViewable(), "id" to toStateString())
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
        val pointCards = CardZone<PointCard>()
        val coins = mutableListOf<Coin>()
        val points: Int get() = pointCards.cards.sumBy { it.points } +
                coins.sumBy { it.points } +
                caravan.spice.filter { it.key > Spice.YELLOW }.values.sum()

        fun toViewable(): Map<String, Any?> {
            return mapOf(
                    "caravan" to caravan.toViewable(),
                    "discard" to discard.cards.map { it.toViewable() },
                    "hand" to hand.cards.map { it.toViewable() },
                    "points" to points,
                    "pointCards" to pointCards.size,
                    "index" to index
            )
        }
    }

    enum class Spice(val char: Char) {
        YELLOW('Y'), RED('R'), GREEN('G'), BROWN('B');

        operator fun plus(upgrade: Int): Spice {
            return values()[minOf(this.ordinal + upgrade, BROWN.ordinal)]
        }

        fun toCaravan(count: Int = 1): Caravan {
            return Caravan(mutableMapOf(this to count))
        }

        fun upgrade(steps: Int): Caravan = Spice.values()[this.ordinal + steps].toCaravan()
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

        fun remainingKeys() = spice.filter { it.value > 0 }.keys
        fun filter(predicate: (Map.Entry<Spice, Int>) -> Boolean): Caravan = Caravan(spice.filter(predicate).toMutableMap())
    }
}

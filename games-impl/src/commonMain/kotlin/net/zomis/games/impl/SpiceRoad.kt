package net.zomis.games.impl

import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
import net.zomis.games.components.resources.GameResource
import net.zomis.games.components.resources.ResourceMap
import net.zomis.games.dsl.*

object SpiceRoadDsl {
    data class PlayParameter(val card: SpiceRoadGameModel.ActionCard, val remove: ResourceMap, val add: ResourceMap)
    data class AcquireParameter(val card: SpiceRoadGameModel.ActionCard, val payArray: List<GameResource>) {
        fun payArrayAsResourceMap(): ResourceMap = ResourceMap.fromList(payArray)
    }

    val factory = GameCreator(SpiceRoadGameModel::class)
    val play = factory.action("play", PlayParameter::class).serializer {
        "Play Card " + it.card.toStateString() + " Remove " + it.remove.toStateString() + " Add " + it.add.toStateString()
    }
    val claim = factory.action("claim", SpiceRoadGameModel.PointCard::class).serializer { it.toStateString() }
    val rest = factory.action("rest", Unit::class)
    val acquire = factory.action("acquire", AcquireParameter::class).serializer {
        "Acquire Card " + it.card.toStateString() + " PayArray " + it.payArray.joinToString("") { x -> (x as SpiceRoadGameModel.Spice).char.toString() }
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
                data class UpgradeChanges(val upgradesRemaining: Int, val changes: ResourceMap = ResourceMap.empty()) {
                    fun removes(): ResourceMap = changes.filter { it.value < 0 }.map { it.resource to it.value * -1 }
                    fun adds(): ResourceMap = changes.filter { it.value > 0 }
                    fun upgradableSpices(startingResources: ResourceMap): Iterable<GameResource> {
                        return (startingResources - this.removes()).filter { it.value > 0 }.entries().map { it.resource }.toSet() - SpiceRoadGameModel.Spice.BROWN
                    }
                    fun upgradableTimes(spiceToUpgrade: GameResource): IntRange {
                        return  1..(minOf(SpiceRoadGameModel.Spice.BROWN.ordinal - (spiceToUpgrade as SpiceRoadGameModel.Spice).ordinal, this.upgradesRemaining))
                    }
                }

                choose {
                    optionsWithIds({ game.currentPlayer.hand.cards.map { it.toStateString() to it } }) { card ->
                        when {
                            card.gain != null -> parameter(PlayParameter(card, ResourceMap.empty(), card.gain))
                            card.upgrade != null -> {
                                val upgrades = card.upgrade
                                recursive(UpgradeChanges(upgrades)) {
                                    intermediateParameter { true }
                                    parameter { PlayParameter(card, chosen.removes(), chosen.adds()) }
                                    until { chosen.upgradesRemaining == 0 }
                                    options({ chosen.upgradableSpices(game.currentPlayer.caravan) }) { spiceToUpgrade ->
                                        options({ chosen.upgradableTimes(spiceToUpgrade) }) { times ->
                                            val caravan = spiceToUpgrade.toResourceMap(-1) + (spiceToUpgrade as SpiceRoadGameModel.Spice).upgrade(times)
                                            recursion(UpgradeChanges(times, caravan)) { previous, e ->
                                                UpgradeChanges(previous.upgradesRemaining - e.upgradesRemaining, previous.changes + e.changes)
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
                    game.currentPlayer.caravan.has(action.parameter.payArrayAsResourceMap())
                }
                effect {
                    game.currentPlayer.caravan -= action.parameter.payArrayAsResourceMap()
                    game.visibleActionCards.cards.mapIndexed { index, card -> card.addSpice(action.parameter.payArray.getOrNull(index)) }

                    game.currentPlayer.caravan += action.parameter.card.takeAllSpice()
                    game.visibleActionCards.card(action.parameter.card).moveTo(game.currentPlayer.hand)

                    if (game.actionDeck.size > 0) {
                        game.actionDeck.random(this.replayable, 1, "NewActionCard") { it.toStateString() }.forEach { it.moveTo(game.visibleActionCards) }
                    }
                }
                choose {
                    optionsWithIds({
                        game.visibleActionCards.cards.filterIndexed { index, _ -> index <= game.currentPlayer.caravan.count() }
                                .map { it.toStateString() to it }
                    }) { card ->
                        recursive(emptyList<GameResource>()) {
                            until { chosen.count() == game.visibleActionCards.card(card).index }
                            options({ (game.currentPlayer.caravan - ResourceMap.fromList(chosen)).entries().filter { it.value > 0 }.map { it.resource } }) {
                                recursion(it) { list, e -> list + e }
                            }
                            parameter { AcquireParameter(card, chosen) }
                        }
                    }
                }
            }
            action(discard) {
                forceWhen { game.currentPlayer.caravan.count() > 10 }
                options { game.currentPlayer.caravan.resources().filter { game.currentPlayer.caravan.has(it, 1) }.filterIsInstance<SpiceRoadGameModel.Spice>() }
                requires { game.currentPlayer.caravan.has(action.parameter.toResourceMap()) }
                effect {
                    game.currentPlayer.caravan -= this.action.parameter.toResourceMap()
                }
            }
            allActions.precondition { game.currentPlayer.index == playerIndex }
            allActions.after {
                val pointCardsRequired = when (game.playerCount) {
                    1, 2, 3 -> 6
                    else -> 5
                }
                val gameEnd = game.players.any { player -> player.pointCards.size >= pointCardsRequired }
                if (gameEnd && game.currentPlayerIndex == game.playerCount - 1) {
                   this.eliminations.eliminateBy(game.players.mapIndexed { index, player -> index to player }, compareBy({ it.points }, { +it.index }))
                }
            }
            allActions.after {
                if (game.players[playerIndex].caravan.count() <= 10) {
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
    val pointsDeck: CardZone<PointCard> = CardZone(pointsCards.split("\n").map { x -> x.split(",") }.map { (x, y) -> PointCard(x.toInt(), y.toCaravan()!!) }.toMutableList())
    val actionDeck: CardZone<ActionCard> = CardZone(actionCards.split("\n").map { x -> x.split(",") }
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

    class ActionCard(val upgrade: Int?, val gain: ResourceMap?, val trade: Pair<ResourceMap, ResourceMap>?) {
        var spiceOnMe = ResourceMap.empty().toMutableResourceMap()
        fun toStateString(): String = "$upgrade $gain ${trade?.first}->${trade?.second}"

        fun toViewable(): Map<String, Any?> = mapOf(
            "upgrade" to upgrade,
            "gain" to gain?.toView(),
            "trade" to if (trade == null) null else mapOf(
                "give" to trade.first.toView(),
                "get" to trade.second.toView()
            ),
            "id" to toStateString(),
            "bonusSpice" to if (spiceOnMe.isEmpty()) null else spiceOnMe.toView()
        )

        fun addSpice(spice: GameResource?) {
            if (spice != null) {
                spiceOnMe += spice.toResourceMap()
            }
        }

        fun takeAllSpice(): ResourceMap {
            val tmp = spiceOnMe.toMutableResourceMap()
            spiceOnMe = ResourceMap.empty().toMutableResourceMap()
            return tmp
        }
    }

    class PointCard(val points: Int, val cost: ResourceMap) {
        fun toStateString(): String = "$points $cost"

        fun toViewable(): Map<String, Any> = mapOf("points" to points, "cost" to cost.toView(), "id" to toStateString())
    }

    class Coin(val points: Int)

    class Player(val index: Int) {
        var caravan = when (index) {
            0 -> ResourceMap.of(Spice.YELLOW to 3)
            1, 2 -> ResourceMap.of(Spice.YELLOW to 4)
            3, 4 -> ResourceMap.of(Spice.YELLOW to 3, Spice.RED to 1)
            else -> throw Exception("You have created a game with too many players")
        }.toMutableResourceMap()
        val discard = CardZone<ActionCard>()
        val hand: CardZone<ActionCard> = CardZone(mutableListOf(
                ActionCard(2, null, null),
                ActionCard(null, Spice.YELLOW.toResourceMap(2), null))
        )
        val pointCards = CardZone<PointCard>()
        val coins = mutableListOf<Coin>()
        val points: Int get() = pointCards.cards.sumOf { it.points } +
                coins.sumOf { it.points } +
                caravan.entries().filter { it.resource as Spice > Spice.YELLOW }.sumOf { it.value }

        fun toViewable(): Map<String, Any?> {
            return mapOf(
                    "caravan" to caravan.toView(),
                    "discard" to discard.cards.map { it.toViewable() },
                    "hand" to hand.cards.map { it.toViewable() },
                    "points" to points,
                    "pointCards" to pointCards.size,
                    "index" to index
            )
        }
    }

    enum class Spice(val char: Char): GameResource {
        YELLOW('Y'), RED('R'), GREEN('G'), BROWN('B');

        fun upgrade(steps: Int): ResourceMap = Spice.values()[this.ordinal + steps].toResourceMap()
    }

    private fun String.toCaravan(): ResourceMap? {
        return if (this.isEmpty()) null else
            this.groupBy { it }.mapValues { it.value.size }.map { Spice.values().find { x -> it.key == x.char }!!
                .toResourceMap(it.value) }.fold(ResourceMap.empty(), ResourceMap::plus)
    }

}

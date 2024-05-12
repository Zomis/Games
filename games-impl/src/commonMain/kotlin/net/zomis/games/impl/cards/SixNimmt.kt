package net.zomis.games.impl.cards

import net.zomis.games.api.GamesApi
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.GameModifierScope
import kotlin.math.abs

object SixNimmt {
    data class NimmtCard(val value: Int, val bulls: Int): GameSerializable {
        override fun serialize(): Int = value
        fun toStateString(): String = value.toString()
    }

    fun bulls(value: Int): Int = when {
        value == 55 -> 7
        value in listOf(11, 22, 33, 44, 55, 66, 77, 88, 99) -> 5
        value % 10 == 0 -> 3
        value % 10 == 5 -> 2
        else -> 1
    }
    fun nimmtCards(): List<NimmtCard> = (1..104).map { NimmtCard(it, bulls(it)) }

    class Player(ctx: Context, val playerIndex: Int) : Entity(ctx) {
        val hand by cards(mutableListOf<NimmtCard>()).privateView(playerIndex) { it.cards }.publicView { it.cards.size }
        val points by cards(mutableListOf<NimmtCard>()).publicView { it.cards.sumOf(NimmtCard::bulls) }
        var playedCard: NimmtCard? by component { null }

        val choosePile = action<Model, Int>("pile", Int::class) {
            precondition { playerIndex == this@Player.playerIndex }
            requires { game.piles.size > action.parameter }
            options { game.piles.indices }
            perform {
                val pile = game.piles[action.parameter]
                pile.cards.moveAllTo(points)
                pile.cards.add(playedCard!!)
                playedCard = null
                game.autoResolve()
            }
        }
    }

    class Pile(ctx: Context) : Entity(ctx), GameSerializable {
        val cards by cards(mutableListOf<NimmtCard>())
        override fun serialize(): Any = highestCard
        override fun toString(): String = cards.map { it.toString() }.toString()
        val highestCard by dynamicValue { cards.cards.last().value }
        val points by dynamicValue { cards.cards.sumOf { it.bulls } }
        val count by dynamicValue { cards.size }
    }

    class Model(override val ctx: Context) : ContextHolder, Entity(ctx) {
        val players by playerComponent { Player(ctx, it) }

        val piles by listComponent(4) { Pile(ctx) }
        val deck by cards(nimmtCards().toMutableList()).publicView { it.size }.onSetup { deck ->
            playerIndices.forEach { playerIndex ->
                deck.random(replayable, 10, "player-$playerIndex", NimmtCard::toStateString).forEach {
                    it.moveTo(players[playerIndex].hand)
                }
            }
            piles.forEachIndexed { index, pile ->
                deck.random(replayable, 1, "pile-$index", NimmtCard::toStateString).forEach {
                    it.moveTo(pile.cards)
                }
            }
        }

        fun findPileFor(nimmtCard: NimmtCard): Pile? = piles.filter {
            it.highestCard < nimmtCard.value
        }.minByOrNull {
            abs(nimmtCard.value - it.highestCard)
        }

        fun autoResolve() {
            do {
                val resolvingPlayers = players.filter { it.playedCard != null }
                val nextPlayer = resolvingPlayers.minBy { it.playedCard!!.value }
                val playedCard = nextPlayer.playedCard!!
                val pile = findPileFor(playedCard)
                checkNotNull(pile) { piles to playedCard }
                if (pile.cards.size >= 5) {
                    pile.cards.moveAllTo(nextPlayer.points)
                }
                pile.cards.cards.add(playedCard)
                nextPlayer.playedCard = null
            } while (players.any { it.playedCard != null })
        }

        val rule: GameModifierScope<Model, Unit>.() -> Unit = {
            if (players.all { it.playedCard != null }) {
                // Check for lowest card
                val lowestCard = players.minBy { it.playedCard!!.value }
                val lowestPile = piles.minOf { it.cards.cards.minOf(NimmtCard::value) }
                if (lowestCard.playedCard!!.value < lowestPile) {
                    // If it's lower than everything else, take a pile
                    action(lowestCard.choosePile)
                } else {
                    // Otherwise, place it automatically where it belongs.
                    autoResolve()
                }
            } else {
                action(playCard)
            }
        }

        val playCard = actionSerializable<Model, NimmtCard>("play", NimmtCard::class) {
            precondition { players[playerIndex].playedCard == null }
            requires { players[playerIndex].hand.cards.contains(action.parameter) }
            options { players[playerIndex].hand.cards }
            perform {
                val player = players[playerIndex]
                player.hand.cards.remove(action.parameter)
                player.playedCard = action.parameter
            }
        }
    }

    val game = GamesApi.gameContext("SixNimmt", Model::class) {
        players(2..10)
        init { Model(ctx) }
        baseRule { it.rule }
    }

}
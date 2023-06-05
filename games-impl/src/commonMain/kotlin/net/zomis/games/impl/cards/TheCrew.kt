package net.zomis.games.impl.cards

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.cards.CardZoneI
import net.zomis.games.common.next
import net.zomis.games.components.SemiKnownCardZone
import net.zomis.games.dsl.Replayable

object TheCrew {

    val factory = GamesApi.gameCreator(Model::class)
    val play = factory.action("play", SuitAndValue::class)
    val communicate = factory.action("communicate", Communication::class)
    enum class CardSuit {
        Yellow,
        Pink,
        Green,
        Blue,
        Rocket,
        ;

        val maxValue get() = if (this == Rocket) 4 else 9
    }
    data class SuitAndValue(val suit: CardSuit, val value: Int) : Replayable {
        override fun toStateString(): String = "$suit-$value"
        fun isRocket(): Boolean = suit == CardSuit.Rocket
    }

    data class Trick(val startingPlayer: Int, val cards: MutableList<SuitAndValue>, val suit: CardSuit) {
        fun determineWinner(): Int {
            val highestRocket = cards.filter { it.isRocket() }.maxByOrNull { it.value }
            val highestCard = highestRocket ?: cards.filter { it.suit == suit }.maxBy { it.value }
            return (cards.indexOf(highestCard) + startingPlayer) % cards.size
        }
    }

    enum class CommunicationType {
        Lowest,
        Only,
        Highest,
    }
    data class Communication(val card: SuitAndValue, val communicationType: CommunicationType)
    class Player(val playerIndex: Int) {
        val hand: CardZone<SuitAndValue> = GamesApi.components.cardZone()
        val missions: CardZone<SuitAndValue> = GamesApi.components.cardZone()
        var communication: Communication? = null
    }
    class Model(players: Int) {
        val players = (0 until players).map { Player(it) }
        val discarded: CardZoneI<SuitAndValue> = GamesApi.components.cardZone()
        var currentPlayerIndex = 0
        val currentPlayer get() = players[currentPlayerIndex]
        var currentTrick: Trick? = null
        var recentTrick: Trick? = null
        var startingPlayer: Player = this.players[0]

        fun checkTrick(): Boolean {
            val trick = currentTrick ?: return true
            if (trick.cards.size != players.size) return true
            val winner = trick.determineWinner()

            val finishedMissions = players[winner].missions.cards.filter { it in trick.cards }
            finishedMissions.forEach { players[winner].missions.card(it).remove() }
            this.recentTrick = trick
            this.currentTrick = null
            this.startingPlayer = players[winner]
            this.currentPlayerIndex = winner

            for (card in trick.cards) {
                if (players.any { it.missions.cards.contains(card) }) return false
            }
            return true
        }
    }

    val viewModel = factory.viewModel(::ViewModel)
    data class ViewPlayer(
        val playerIndex: Int,
        val missions: List<SuitAndValue>,
        val communication: Communication?,
    ) {
        constructor(player: Player): this(player.playerIndex, player.missions.cards, player.communication)
    }
    data class ViewModel(
        val players: List<ViewPlayer>,
        val currentPlayer: Int,
        val currentTrick: Trick?,
        val recentTrick: Trick?,
        val yourHand: List<SuitAndValue>?,
        val viewer: Int,
    ) {
        fun possibleCommunication(card: SuitAndValue): CommunicationType? {
            if (yourHand == null) return null
            val cardsInSuit = yourHand.filter { it.suit == card.suit }
            return when {
                cardsInSuit.size == 1 -> CommunicationType.Only
                card.value == cardsInSuit.minOf { it.value } -> CommunicationType.Lowest
                card.value == cardsInSuit.maxOf { it.value } -> CommunicationType.Highest
                else -> null
            }
        }

        constructor(model: Model, viewer: Int) : this(
            players = model.players.map { ViewPlayer(it) },
            currentPlayer = model.currentPlayerIndex,
            currentTrick = model.currentTrick,
            recentTrick = model.recentTrick,
            yourHand = model.players.getOrNull(viewer)?.hand?.cards,
            viewer = viewer,
        )
    }

    val game = factory.game("TheCrew") {
        setup {
            // TODO: Add 2-player version (it's in the rules)
            players(3..5)
            init { Model(playerCount) }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                viewModel(viewModel)
            }
        }
        gameFlow {
            val allCards = CardSuit.values().flatMap { suit ->
                (1..suit.maxValue).map { SuitAndValue(suit, it) }
            }
            SemiKnownCardZone(allCards, SuitAndValue::toStateString)
                .shuffle()
                .deal(replayable, "cards", allCards.size, game.players.map { it.hand })
            game.startingPlayer = findCommander(game)

            val missionCards = SemiKnownCardZone(allCards.filter { !it.isRocket() }, SuitAndValue::toStateString)
            missionCards.shuffle()
            missionCards.top(replayable, "missions", 1).forEach {
                it.moveTo(game.startingPlayer.missions)
            }
            step("distribute missions") {
                // TODO: Distribute missions.
            }
            loop {
                step("play") {
                    if (game.currentTrick == null) {
                        game.currentPlayerIndex = game.startingPlayer.playerIndex
                        yieldAction(communicate) {
                            requires { action.parameter.card.suit != CardSuit.Rocket }
                            requires {
                                val communicatorHand = action.game.players[playerIndex].hand
                                val card = action.parameter.card
                                val handOfSuit = communicatorHand.cards.filter { it.suit == card.suit }
                                when (action.parameter.communicationType) {
                                    CommunicationType.Highest -> {
                                        handOfSuit.maxBy { it.value } == card
                                    }
                                    CommunicationType.Lowest -> {
                                        handOfSuit.minBy { it.value } == card
                                    }
                                    CommunicationType.Only -> {
                                        handOfSuit.singleOrNull() == card
                                    }
                                }
                            }
                            requires { game.players[playerIndex].communication == null }
                            perform {
                                game.players[playerIndex].communication = action.parameter
                            }
                        }
                    }
                    yieldAction(play) {
                        precondition { playerIndex == game.currentPlayerIndex }
                        options { game.currentPlayer.hand.cards }
                        requires { game.currentPlayer.hand.cards.contains(action.parameter) }
                        requires {
                            val trick = game.currentTrick ?: return@requires true
                            action.parameter.suit == trick.suit || game.players[playerIndex].hand.cards.none { it.suit == trick.suit }
                        }
                        perform {
                            game.players[playerIndex].hand.card(action.parameter).remove()
                            if (game.currentTrick == null) {
                                game.currentTrick = Trick(playerIndex, mutableListOf(), action.parameter.suit)
                            }
                            game.currentTrick!!.cards.add(action.parameter)
                            game.currentPlayerIndex = game.currentPlayerIndex.next(eliminations)
                            if (!game.checkTrick()) {
                                eliminations.eliminateRemaining(WinResult.LOSS)
                            }
                            if (game.players.none { it.missions.size > 0 }) eliminations.eliminateRemaining(WinResult.WIN)
                        }
                    }
                }
            }
        }
    }

    private fun findCommander(game: Model): Player = game.players.first { p ->
        p.hand.cards.any { it.suit == CardSuit.Rocket && it.value == CardSuit.Rocket.maxValue }
    }

}
package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
import net.zomis.games.common.toSingleList
import net.zomis.games.dsl.flow.GameFlowScope
import net.zomis.games.dsl.flow.GameFlowStep

object Red7 {

    enum class Color(val description: String) {
        RED("Highest card"),
        ORANGE("most of one number"),
        YELLOW("most of one color"),
        GREEN("most even cards"),
        BLUE("most different colors"),
        INDIGO("most cards in a row"),
        VIOLET("most cards below 4"),
        ;

        fun value(value: Int): Card7 = Card7(this, value)
    }
    class Player(val playerIndex: Int) {
        val hand = CardZone<Card7>()
        val palette = CardZone<Card7>()
        val scores = CardZone<Card7>()

        fun findBestHand(color: Color): Hand {
            return when (color) {
                Color.RED -> Hand(palette.cards.maxOrNull()!!.toSingleList())
                Color.ORANGE -> {
                    val grouping = palette.cards.groupingBy { it.value }
                    val highest = grouping.eachCount().maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })!!
                    Hand(palette.cards.filter { it.value == highest.key })
                }
                Color.YELLOW -> {
                    val grouping = palette.cards.groupingBy { it.color }
                    val highest = grouping.eachCount().maxWithOrNull(compareBy<Map.Entry<Color, Int>> { it.value }.thenBy { it.key })!!
                    Hand(palette.cards.filter { it.color == highest.key })
                }
                Color.GREEN -> Hand(palette.cards.filter { it.value % 2 == 0 })
                Color.BLUE -> Hand(palette.cards.groupBy { it.color }.mapValues { it.value.maxOrNull()!! }.map { it.value })
                Color.INDIGO -> {
                    // Sort cards by highest value, (only use highest color of each value?), keep track of highest consecutive found so far
                    val cards = palette.cards.sortedBy { -it.value }
                    var consecutive = mutableListOf<Card7>()
                    var maxConsecutive = consecutive

                    for (card in cards) {
                        if (consecutive.isEmpty() || card.value == consecutive.last().value - 1) {
                            consecutive.add(card)
                        } else {
                            if (maxConsecutive.size < consecutive.size) {
                                maxConsecutive = consecutive
                            }
                            consecutive = mutableListOf(card)
                        }
                    }
                    if (maxConsecutive.size < consecutive.size) {
                        maxConsecutive = consecutive
                    }

                    Hand(maxConsecutive)
                }
                Color.VIOLET -> Hand(palette.cards.filter { it.value < 4 })
            }
        }
    }
    class Model(playerCount: Int) {
        var currentPlayerIndex: Int = 0
        val currentPlayer get() = players[currentPlayerIndex]
        val players = (0 until playerCount).map { Player(it) }
        val canvas = CardZone<Card7>()
        val deck = CardZone<Card7>()
        val scoresToWin = listOf(40, 35, 30)[playerCount - 2]

        fun determineWinner(): Player = determineWinnerBy(canvas.cards.last().color).first()
        fun determineWinnerBy(color: Color): List<Player> = players.sortedByDescending { it.findBestHand(color) }
    }
    data class Hand(val cards: List<Card7>): Comparable<Hand> {
        override fun compareTo(other: Hand): Int {
            return compareBy<Hand> { it.cards.size }.thenBy { it.cards.maxOrNull() }.compare(this, other)
        }
    }
    data class Card7(val color: Color, val value: Int): Comparable<Card7> {
        fun toStateString(): String = "$color-$value"
        override fun compareTo(other: Card7): Int = compareBy<Card7> { it.value }
            .thenBy { -it.color.ordinal }
            .compare(this, other)
    }

    object Game {
        val factory = GamesApi.gameCreator(Model::class)
        val playPalette = factory.action("palette", Card7::class).serializer { it.toStateString() }
        val discardCanvas = factory.action("canvas", Card7::class).serializer { it.toStateString() }
        val pass = factory.singleAction("pass")

        val game = factory.game("Red7") {
            setup {
                players(2..4)
                onStart {
                    // Starting rule
                    game.canvas.cards.add(Card7(Color.RED, 0))

                    // Distribute cards to players
                    game.deck.cards.addAll(Color.values().flatMap { color -> (1..7).map { color.value(it) } })
                    val handCards = game.deck.random(replayable, 7 * game.players.size, "hands") { it.toStateString() }
                    game.deck.deal(handCards.map { it.card }.toList(), game.players.map { it.hand })

                    // Distribute palette cards to players
                    val paletteCards = game.deck.random(replayable, game.players.size, "palette") { it.toStateString() }
                    game.deck.deal(paletteCards.map { it.card }.toList(), game.players.map { it.palette })

                    // Set starting player
                    val winner = game.determineWinner()
                    game.currentPlayerIndex = (winner.playerIndex + 1) % game.players.size
                }
                init { Model(playerCount) }
            }
            gameFlow {
//                data class StepParam(val playerIndex: Int, val played: Boolean)
//                val play = step("step", StepParam::class) {
//                }
//                play.nextStep { play.withParameters(params.playerIndex + 1, false) }

                loop {
                    val step = playMode(this, game.currentPlayerIndex, false)
                    if (step.action?.actionType == playPalette.name) {
                        playMode(this, game.currentPlayerIndex, true)
                    }
                }
            }
            gameFlowRules {
//                rule(42, "Configurable rule") {
//                    config
//                }
                rules.players.lastPlayerStanding()
                beforeReturnRule("view") {
                    view("players") {
                        game.players.map { player ->
                            mapOf(
                                "palette" to player.palette.cards.map { it.toStateString() },
                                "hand" to player.hand.size,
                                "score" to player.scores.cards.sumBy { it.value },
                                "scoreCards" to player.scores.cards.map { it.toStateString() },
                                "currentPlayer" to (player.playerIndex == game.currentPlayerIndex)
                            )
                        }
                    }
                    view("colors") {
                        Color.values().associate { color ->
                            color to mapOf("description" to color.description, "winners" to game.determineWinnerBy(color).map { it.playerIndex })
                        }
                    }
                    view("canvas") { game.canvas.cards.map { it.toStateString() } }
                    view("rule") { game.canvas.cards.last().color }
                }
            }
            testCase(4) {
                val r7 = Color.RED.value(7)
                val r6 = Color.RED.value(6)
                val g7 = Color.GREEN.value(7)
                val g5 = Color.GREEN.value(5)
                expectTrue(r7 > g7)
                expectTrue(r7 > r6)
                expectTrue(r7 > g5)
                expectTrue(g7 > g5)

                val hand1 = createPlayer(Color.RED.value(7), Color.GREEN.value(5))
                expectEquals(Hand(listOf(Color.RED.value(7))), hand1.findBestHand(Color.RED))
                expectEquals(Hand(emptyList()), hand1.findBestHand(Color.GREEN))
                expectEquals(Hand(emptyList()), hand1.findBestHand(Color.VIOLET))

                val hand2 = createPlayer(Color.RED.value(6), Color.GREEN.value(5),
                    Color.ORANGE.value(4), Color.VIOLET.value(3), Color.INDIGO.value(2), Color.GREEN.value(1))
                expectEquals(6, hand2.findBestHand(Color.INDIGO).cards.size)
                expectEquals(5, hand2.findBestHand(Color.BLUE).cards.size)
                expectEquals(Hand(listOf(Color.GREEN.value(5), Color.GREEN.value(1))),
                    hand2.findBestHand(Color.YELLOW))
                expectEquals(Hand(listOf(Color.VIOLET.value(3), Color.INDIGO.value(2), Color.GREEN.value(1))),
                    hand2.findBestHand(Color.VIOLET))
                expectEquals(Hand(listOf(Color.RED.value(6), Color.ORANGE.value(4), Color.INDIGO.value(2))),
                    hand2.findBestHand(Color.GREEN))
            }
        }
        suspend fun playMode(scope: GameFlowScope<Model>, currentPlayer: Int, played: Boolean): GameFlowStep<Model> {
            return scope.step("step") {
                game.currentPlayerIndex = currentPlayer
                if (!played) {
                    yieldAction(playPalette) {
                        precondition { playerIndex == game.currentPlayerIndex }
                        options { game.currentPlayer.hand.cards }
                        perform {
                            game.currentPlayer.hand.card(action.parameter).moveTo(game.currentPlayer.palette)
                        }
                    }
                }
                yieldAction(discardCanvas) {
                    precondition { playerIndex == game.currentPlayerIndex }
                    options { game.currentPlayer.hand.cards }
                    perform {
                        game.currentPlayer.hand.card(action.parameter).moveTo(game.canvas)
                    }
                }
                yieldAction(pass) {
                    precondition { playerIndex == game.currentPlayerIndex }
                    perform {
                        val winner = game.determineWinner() == game.players[action.playerIndex]
                        if (!winner) {
                            eliminations.result(action.playerIndex, WinResult.LOSS)
                        }
                        game.currentPlayerIndex = game.currentPlayerIndex.next(game.players.size)
                    }
                }
            }
        }
    }

    private fun createPlayer(vararg cards: Card7): Player = Player(-1).also {
        it.palette.cards.addAll(cards)
    }

}
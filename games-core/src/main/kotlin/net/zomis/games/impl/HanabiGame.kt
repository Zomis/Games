package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.createActionType
import net.zomis.games.dsl.createGame
import kotlin.random.Random

// RAINBOW: Either used as a sixth color, or as a wildcard (where cards can be both blue and yellow for example)
// name the card you are playing, or get a fail token
// only one card of multicolor
// game ends when players are defeated or an indispensible card has been discarded
// game normally ends one full turn after the last card drawn.
// allow empty clues - true/false

enum class HanabiColor { YELLOW, WHITE, RED, BLUE, GREEN }
data class HanabiCard(val color: HanabiColor, val value: Int, var colorKnown: Boolean, var valueKnown: Boolean) {
    fun known(known: Boolean): Map<String, String> {
        return mapOf<String, String>()
            .let { if (known || colorKnown) it.plus("color" to color.name) else it }
            .let { if (known || valueKnown) it.plus("value" to value.toString()) else it }
    }

    fun reveal(clue: HanabiClue) {
        if (clue.value == this.value) this.valueKnown = true
        if (clue.color == this.color) this.colorKnown = true
    }

    fun toStateString(): String {
        return "$color-$value"
    }
}

data class HanabiPlayer(val cards: CardZone<HanabiCard> = CardZone())
data class HanabiConfig(
    val rainbowExtraColor: Boolean,
    val rainbowWildcard: Boolean,
    val rainbowOnlyOne: Boolean,
    val namePlayingCard: Boolean,
    val playUntilFullEnd: Boolean,
    val allowEmptyClues: Boolean
)
data class Hanabi(val config: HanabiConfig, val players: List<HanabiPlayer>, val board: List<CardZone<HanabiCard>>, var clueTokens: Int, var failTokens: Int) {
    var currentPlayer: Int = 0
    val current: HanabiPlayer get() = players[currentPlayer]
    val discard = CardZone(mutableListOf<HanabiCard>())
    val deck = CardZone(HanabiColor.values().flatMap { color ->
        (1..5).flatMap { value ->
            val count = when (value) {
                1 -> 3
                in 2..4 -> 2
                5 -> 1
                else -> throw IllegalArgumentException("Not an Hanabi value: $value")
            }
            (1..count).map { HanabiCard(color, value, colorKnown = false, valueKnown = false) }
        }
    }.shuffled(Random(42)).toMutableList())

    // Playing a five should give a clue token back
    fun reveal(clue: HanabiClue) {
        val player = players[clue.player]
        player.cards.forEach { it.reveal(clue) }
    }

    fun nextTurn() {
        this.currentPlayer = (this.currentPlayer + 1) % players.size
    }

    fun playAreaFor(card: HanabiCard): CardZone<HanabiCard>? {
        val potentialArea = board[card.color.ordinal]
        return potentialArea.takeIf { it.size + 1 == card.value }
    }

    fun boardComplete(): Boolean {
        return board.minus(board.last()).all { it.size == 5 }
    }

}

data class HanabiClue(val player: Int, val color: HanabiColor?, val value: Int?)

object HanabiGame {

    val giveClue = createActionType("GiveClue", HanabiClue::class)
    val discard = createActionType("Discard", Int::class)
    val play = createActionType("Play", Int::class)
    val game = createGame<Hanabi>("Hanabi") {
        setup(HanabiConfig::class) {
            defaultConfig {
                HanabiConfig(
                    rainbowExtraColor = false,
                    rainbowOnlyOne = false,
                    rainbowWildcard = false,
                    allowEmptyClues = true,
                    namePlayingCard = false,
                    playUntilFullEnd = false
                )
            }
            players(2..5)
            init {
                Hanabi(config, (0 until playerCount).map { HanabiPlayer() }, HanabiColor.values().map { CardZone<HanabiCard>() }, 8, 3)
            }
            onStart {
                // 5 cards for 2 or 3 players, otherwise 4 cards.
                val playerCount = it.players.size
                val cards = if (playerCount in 2..3) 5 else 4
//                val cardsToDeal = this.state("deal") { it.deck.cards.take(cards * playerCount) }
                it.deck.deal(cards * playerCount, it.players.map { p -> p.cards })
            }
        }
        logic {
            intAction(discard, { it.current.cards.indices }) {
                allowed { it.playerIndex == it.game.currentPlayer }
//                replayableEffect {
//                    val newCard = this.state("card") { it.game.deck.first() }
//
//                }
                effect {
                    // TODO: Some Replayable interface would be handy here, use state if it exists, save state otherwise
                    val newCard = it.game.current.cards[it.parameter].moveAndReplace(it.game.discard, it.game.deck)
                    this.state("card", newCard.toStateString())
                    it.game.nextTurn()
                }
//                replayEffect {
//                    val newCard = this.state("card") as String
//                    it.game.deck.find(newCard)
//                    val newCard = it.game.current.cards[it.parameter].moveAndReplace(it.game.discard, it.game.deck)
//                    this.state("card", newCard)
//                }
            }
            intAction(play, {it.current.cards.indices}) {
                allowed { it.playerIndex == it.game.currentPlayer }
                effect {
                    val card = it.game.current.cards[it.parameter]
                    val playArea = it.game.playAreaFor(card.card)
                    val newCard = card.moveAndReplace(playArea ?: it.game.discard, it.game.deck)
                    this.state("card", newCard.toStateString())
                    if (card.card.value == 5) {
                        it.game.clueTokens++
                    }
                    if (playArea == null) {
                        it.game.failTokens--
                    }
                    if (it.game.failTokens == 0) {
                        playerEliminations.eliminateRemaining(WinResult.LOSS)
                    }
                    if (it.game.boardComplete()) {
                        playerEliminations.eliminateRemaining(WinResult.WIN)
                    }
                    it.game.nextTurn()
                }
//                replayEffect {
//                    val newCard = this.state("card")
//                }
            }
            action(giveClue) {
                options {
                    optionFrom({ it.players.indices.toList().minus(it.currentPlayer) }) {player ->
                        val TEXT_COLOR = "color"
                        option(listOf(TEXT_COLOR, "value")) {clueMode ->
                            if (clueMode == TEXT_COLOR) {
                                option(HanabiColor.values().toList()) {color ->
                                    actionParameter(HanabiClue(player, color, null))
                                }
                            } else {
                                option(1..5) {value ->
                                    actionParameter(HanabiClue(player, null, value))
                                }
                            }
                        }
                    }
                }
                allowed { it.parameter.player != it.playerIndex && it.playerIndex == it.game.currentPlayer }
                effect {
                    it.game.reveal(it.parameter)
                    it.game.nextTurn()
                }
            }
        }
        view {
            currentPlayer { it.currentPlayer }
            value("others") {
                it.players.mapIndexed { index, player ->
                    val cards = player.cards.map { card ->
                        card.known(index != viewer)
                    }
                    if (index == viewer) return@mapIndexed null
                    mapOf("index" to index, "cards" to cards)
                }.filterNotNull()
            }
            value("hand") { it.players[this.viewer!!].cards.map { card -> card.known(false) } }
            value("discard") { it.discard.cards.map { card -> card.known(true) } }
            value("clues") { it.clueTokens }
            value("fails") { it.failTokens }
            value("board") { it.board.map { b -> b.cards.map { c -> c.known(true) } } }
        }
    }

}
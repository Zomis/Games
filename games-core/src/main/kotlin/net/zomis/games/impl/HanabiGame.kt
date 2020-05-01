package net.zomis.games.impl

import net.zomis.games.PlayerEliminations
import net.zomis.games.WinResult
import net.zomis.games.cards.Card
import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.ReplayableScope
import net.zomis.games.dsl.createActionType
import net.zomis.games.dsl.createGame
import kotlin.math.min

// RAINBOW: Either used as a sixth color, or as a wildcard (where cards can be both blue and yellow for example)
// name the card you are playing, or get a fail token
// only one card of multicolor
// game ends when players are defeated or an indispensible card has been discarded
// game normally ends one full turn after the last card drawn.
// allow empty clues - true/false

// 0-5 Fruktansvärt utbuad av publiken.
// 6-10 Mediokert, spridda applåder som bäst.
// 11-15 Anmärkningsvärt, men kommer inte bli ihågkommet så länge
// 16-20 Utmärkt, publiken är väldigt nöjd
// 21-24 Suveränt, kommer att komma ihåg väldigt länge
// 25 Legendariskt, alla är förstummade och hänförda

enum class HanabiColor { YELLOW, WHITE, RED, BLUE, GREEN }
data class HanabiCard(val color: HanabiColor, val value: Int, var colorKnown: Boolean, var valueKnown: Boolean) {
    fun known(known: Boolean): Map<String, Any> {
        return mapOf<String, Any>(
            "colorKnown" to colorKnown,
            "valueKnown" to valueKnown
        )
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
    val maxClueTokens: Int,
    val rainbowExtraColor: Boolean,
    val rainbowWildcard: Boolean,
    val rainbowOnlyOne: Boolean,
    val namePlayingCard: Boolean,
    val playUntilFullEnd: Boolean,
    val allowEmptyClues: Boolean
)
data class Hanabi(val config: HanabiConfig, val players: List<HanabiPlayer>, val board: List<CardZone<HanabiCard>>, var clueTokens: Int, var failTokens: Int) {
    var currentPlayer: Int = 0
    var turnsLeft = -1
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
    }.shuffled().toMutableList())

    // Playing a five should give a clue token back
    fun reveal(clue: HanabiClue) {
        val player = players[clue.player]
        player.cards.forEach { it.reveal(clue) }
    }

    fun nextTurn() {
        if (turnsLeft > 0) turnsLeft--
        this.currentPlayer = (this.currentPlayer + 1) % players.size
    }

    fun playAreaFor(card: HanabiCard): CardZone<HanabiCard>? {
        val potentialArea = board[card.color.ordinal]
        return potentialArea.takeIf { it.size + 1 == card.value }
    }

    fun boardComplete(): Boolean {
        return board.minus(board.last()).all { it.size == 5 }
    }

    fun increaseClueTokens() {
        this.clueTokens = min(this.clueTokens + 1, this.config.maxClueTokens)
    }

    fun emptyDeckCheck() {
        if (deck.size == 0 && turnsLeft < 0) {
            turnsLeft = this.players.size + 1
        }
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
                    maxClueTokens = 8,
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
                val cardPerPlayer = if (playerCount in 2..3) 5 else 4
                val cardStates = this.strings("cards") {
                    it.deck.top(cardPerPlayer * playerCount).map { c -> c.toStateString() }
                }
                val cards = it.deck.findStates(cardStates) { c -> c.toStateString() }
                it.deck.deal(cards, it.players.map { p -> p.cards })
            }
        }
        logic {
            intAction(discard, { it.current.cards.indices }) {
                allowed { it.playerIndex == it.game.currentPlayer && it.game.turnsLeft != 0 && it.game.clueTokens < it.game.config.maxClueTokens }
                effect {
                    moveCard(this.replayable(), it.game, it.game.current.cards[it.parameter], it.game.discard)
                    it.game.increaseClueTokens()
                    nextTurnEndCheck(it.game, playerEliminations)
                }
            }
            intAction(play, {it.current.cards.indices}) {
                allowed { it.playerIndex == it.game.currentPlayer && it.game.turnsLeft != 0 }
                effect {
                    val playCard = it.game.current.cards[it.parameter]
                    val playArea = it.game.playAreaFor(playCard.card)

                    moveCard(this.replayable(), it.game, playCard, playArea ?: it.game.discard)
                    if (playCard.card.value == 5 && playArea != null) {
                        it.game.increaseClueTokens()
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
                    nextTurnEndCheck(it.game, playerEliminations)
                }
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
                allowed {
                    it.parameter.player != it.playerIndex &&
                    it.playerIndex == it.game.currentPlayer &&
                    it.game.clueTokens > 0 && it.game.turnsLeft != 0
                }
                effect {
                    it.game.clueTokens--
                    it.game.reveal(it.parameter)
                    nextTurnEndCheck(it.game, playerEliminations)
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
            value("hand") {
                val cards = it.players[this.viewer ?: 0].cards.map { card -> card.known(false) }
                mapOf("index" to this.viewer, "cards" to cards)
            }
            value("discard") { it.discard.cards.map { card -> card.known(true) } }
            value("cardsLeft") { it.deck.size }
            value("clues") { it.clueTokens }
            value("fails") { it.failTokens }
            value("board") { it.board.map { b -> b.cards.map { c -> c.known(true) } } }
        }
    }

    private fun nextTurnEndCheck(game: Hanabi, playerEliminations: PlayerEliminations) {
        game.nextTurn()
        if (game.turnsLeft == 0) {
            playerEliminations.eliminateRemaining(WinResult.LOSS)
        }
    }

    private fun moveCard(replayable: ReplayableScope, game: Hanabi, card: Card<HanabiCard>, destination: CardZone<HanabiCard>) {
        if (game.deck.size > 0) {
            val zone = card.zone
            val cardState = replayable.string("card") { game.deck.cards[0].toStateString() }
            val nextCard = game.deck.findState(cardState) { c -> c.toStateString() }
            card.moveTo(destination)
            game.deck.card(nextCard).moveTo(zone)
            // OLD: card.moveAndReplace(destination, game.deck.card(nextCard))
            game.emptyDeckCheck()
        } else {
            card.moveTo(destination)
        }
    }

}
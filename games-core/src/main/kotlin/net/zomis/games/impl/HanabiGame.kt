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

enum class HanabiColor { YELLOW, WHITE, RED, BLUE, GREEN, RAINBOW }
class HanabiCard(val color: HanabiColor, val value: Int, var colorKnown: Boolean, var valueKnown: Boolean) {
    val possibleValues: MutableMap<Int, Boolean> = mutableMapOf()
    val possibleColors: MutableMap<HanabiColor, Boolean> = mutableMapOf()
    var id: Int = 0

    fun known(known: Boolean): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to id,
            "colorKnown" to colorKnown,
            "valueKnown" to valueKnown
        )
            .let { if (known || colorKnown) it.plus("color" to color.name) else it }
            .let { if (known || valueKnown) it.plus("value" to value.toString()) else it }
    }

    fun matches(clue: HanabiClue): Boolean = this.color == clue.color || this.value == clue.value

    fun reveal(clue: HanabiClue) {
        if (clue.color != null) {
            possibleColors[clue.color] = this.color == clue.color
        }
        if (clue.value != null) {
            possibleValues[clue.value] = this.value == clue.value
        }
        if (clue.value == this.value) this.valueKnown = true
        if (clue.color == this.color) this.colorKnown = true
    }

    fun toStateString(): String {
        return "$color-$value"
    }

    override fun toString(): String {
        return "$color($colorKnown) $value($valueKnown)"
    }
}

data class HanabiPlayer(val cards: CardZone<HanabiCard> = CardZone())
data class HanabiConfig(
        val viewAllowCardIsNot: Boolean, // TODO
        val viewAllowCardProbability: Boolean, // TODO
        val maxClueTokens: Int,
        val maxFailTokens: Int,
        val rainbowExtraColor: Boolean,
        val rainbowWildcard: Boolean, // TODO: This will screw up probabilities and knowledge and lots of stuff a lot
        val rainbowOnlyOne: Boolean,
        val namePlayingCard: Boolean, // TODO
        val playUntilFullEnd: Boolean,
        val allowEmptyClues: Boolean
) {
    private fun useRainbowColor() = rainbowExtraColor || rainbowWildcard
    private fun allowRainbowClue() = rainbowExtraColor && !rainbowWildcard

    fun colors() = HanabiColor.values().toList().minus(if (useRainbowColor()) listOf() else listOf(HanabiColor.RAINBOW))
    fun clueableColors() = HanabiColor.values().toList().minus(if (allowRainbowClue()) listOf() else listOf(HanabiColor.RAINBOW))

    fun countInDeck(color: HanabiColor, value: Int): Int {
        if (rainbowOnlyOne && color == HanabiColor.RAINBOW) return 1
        if (!useRainbowColor() && color == HanabiColor.RAINBOW) return 0
        return when (value) {
            1 -> 3
            in 2..4 -> 2
            5 -> 1
            else -> throw IllegalArgumentException("Not an Hanabi value: $value")
        }
    }

    fun values(): IntRange = 1..5

    fun createCards(): List<HanabiCard> {
        return HanabiColor.values().flatMap { color ->
            values().flatMap { value ->
                List(countInDeck(color, value)) { HanabiCard(color, value, colorKnown = false, valueKnown = false) }
            }
        }
    }

}
data class HanabiColorData(val color: HanabiColor, val board: CardZone<HanabiCard> = CardZone(mutableListOf()), val discard: CardZone<HanabiCard> = CardZone(mutableListOf())) {
    fun values(): List<Pair<HanabiColor, Int>> = (1..5).map { color to it }
}
data class Hanabi(val config: HanabiConfig, val players: List<HanabiPlayer>) {
    val colors: List<HanabiColorData> = config.colors().map { HanabiColorData(it) }
    var clueTokens: Int = config.maxClueTokens
    var failTokens: Int = 0
    var currentPlayer: Int = 0
    var turnsLeft = -1
    val current: HanabiPlayer get() = players[currentPlayer]

    val deck = CardZone(config.createCards().shuffled().toMutableList()).also { it.cards.forEachIndexed { index, hanabiCard -> hanabiCard.id = index } }

    fun reveal(clue: HanabiClue) {
        val player = players[clue.player]
        player.cards.forEach { it.reveal(clue) }
    }

    fun nextTurn() {
        if (turnsLeft > 0) turnsLeft--
        this.currentPlayer = (this.currentPlayer + 1) % players.size
    }

    fun colorData(card: HanabiCard): HanabiColorData = colors.find { it.color == card.color }!!
    fun playAreaFor(card: HanabiCard): CardZone<HanabiCard>? {
        return colorData(card).board.takeIf { it.size + 1 == card.value }
    }

    fun boardComplete(): Boolean {
        return this.colors.size == this.config.colors().size && this.colors.all { it.board.size == 5 }
    }

    fun increaseClueTokens() {
        this.clueTokens = min(this.clueTokens + 1, this.config.maxClueTokens)
    }

    fun emptyDeckCheck() {
        if (deck.size == 0 && turnsLeft < 0 && !config.playUntilFullEnd) {
            turnsLeft = this.players.size + 1
        }
    }

    fun isGameOver(): Boolean {
        return this.boardComplete() || this.turnsLeft == 0 || this.failTokens == this.config.maxFailTokens
    }

    fun allowClue(clue: HanabiClue): Boolean {
        return config.allowEmptyClues || players[clue.player].cards.cards.any { it.matches(clue) }
    }

    fun score(): Int = colors.sumBy { zone -> zone.board.size }
    fun possibleClues(currentPlayer: Int): List<HanabiClue> {
        return this.players.indices.toList().minus(currentPlayer).flatMap {cluePlayer ->
            colors.map { HanabiClue(cluePlayer, it.color, null) } +
                config.values().map { HanabiClue(cluePlayer, null, it) }
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
                    viewAllowCardIsNot = false,
                    viewAllowCardProbability = false,
                    maxClueTokens = 8,
                    maxFailTokens = 3,
                    rainbowExtraColor = false,
                    rainbowOnlyOne = false,
                    rainbowWildcard = false,
                    allowEmptyClues = false,
                    namePlayingCard = false,
                    playUntilFullEnd = false
                )
            }
            players(2..5)
            init {
                Hanabi(config, (0 until playerCount).map { HanabiPlayer() })
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
                    val card = it.game.current.cards[it.parameter]
                    moveCard(this.replayable(), it.game, card, it.game.colorData(card.card).discard)
                    it.game.increaseClueTokens()
                    nextTurnEndCheck(it.game, playerEliminations)
                }
            }
            intAction(play, {it.current.cards.indices}) {
                allowed { it.playerIndex == it.game.currentPlayer && it.game.turnsLeft != 0 }
                effect {
                    val playCard = it.game.current.cards[it.parameter]
                    val playArea = it.game.playAreaFor(playCard.card)

                    moveCard(this.replayable(), it.game, playCard, playArea ?: it.game.colorData(playCard.card).discard)
                    if (playCard.card.value == 5 && playArea != null) {
                        it.game.increaseClueTokens()
                    }
                    if (playArea == null) {
                        it.game.failTokens++
                    }
                    if (it.game.failTokens == it.game.config.maxFailTokens) {
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
                                optionFrom({ game -> game.config.clueableColors() }) {color ->
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
                        && it.game.allowClue(it.parameter)
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
                val viewPerspective = this.viewer ?: it.currentPlayer
                val cards = it.players[viewPerspective].cards.map {
                    card -> card.known(it.isGameOver())
                }
                mapOf("index" to viewPerspective, "cards" to cards)
            }
            value("colors") {
                it.colors.map {colorData ->
                    mapOf(
                        "color" to colorData.color.name.toLowerCase(),
                        "board" to colorData.board.map { card -> card.known(true) },
                        "discard" to colorData.discard.toList().map { card -> card.known(true) }
                    )
                }
            }
            value("cardsLeft") { it.deck.size }
            value("clues") { it.clueTokens }
            value("score") { it.score() }
            value("fails") { it.failTokens }
            value("maxFails") { it.config.maxFailTokens }
            if (game.config.viewAllowCardIsNot) {
                onRequest("canNotBe") {
                    game.players[viewer ?: game.currentPlayer].cards.cards.map {
                        mapOf("colors" to it.possibleColors, "values" to it.possibleValues)
                    }
                }
            }
            if (game.config.viewAllowCardProbability) {
                onRequest("probabilities") {
                    HanabiProbabilities.calculateProbabilities(game, viewer ?: game.currentPlayer)
                }
            }
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
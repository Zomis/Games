package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.Card
import net.zomis.games.cards.CardZone
import net.zomis.games.cards.random
import net.zomis.games.components.IdGenerator
import net.zomis.games.dsl.*
import net.zomis.games.metrics.MetricBuilder
import kotlin.math.min

// RAINBOW: Either used as a sixth color, or as a wildcard (where cards can be both blue and yellow for example)
// name the card you are playing, or get a fail token
// only one card of multicolor
// game ends when players are defeated or an indispensable card has been discarded
// game normally ends one full turn after the last card drawn.
// allow empty clues - true/false

enum class HanabiColor { YELLOW, WHITE, RED, BLUE, GREEN, RAINBOW }
class HanabiCard(val color: HanabiColor, val value: Int, var colorKnown: Boolean, var valueKnown: Boolean): Replayable {
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

    override fun toStateString(): String = "$color-$value"

    override fun toString(): String = "$color($colorKnown) $value($valueKnown)"
}

data class HanabiPlayer(val index: Int, val cards: CardZone<HanabiCard> = CardZone())
data class HanabiConfig(
        val viewAllowCardIsNot: Boolean, // TODO
        val viewAllowCardProbability: Boolean, // TODO
        val maxClueTokens: Int,
        val maxFailTokens: Int,
        val rainbowExtraColor: Boolean,
        val rainbowWildcard: Boolean, // TODO: This will screw up probabilities and knowledge and lots of stuff a lot
        val rainbowOnlyOne: Boolean,
        val namePlayingCard: Boolean,
        val playUntilFullEnd: Boolean,
        val allowEmptyClues: Boolean
) {
    private fun useRainbowColor() = rainbowExtraColor || rainbowWildcard
    private fun allowRainbowClue() = rainbowExtraColor && !rainbowWildcard

    fun colors() = HanabiColor.values().toList().minus(if (useRainbowColor()) emptySet() else setOf(HanabiColor.RAINBOW))
    fun clueableColors() = HanabiColor.values().toList().minus(if (allowRainbowClue()) setOf() else setOf(HanabiColor.RAINBOW))

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
    fun nextPlayable(): Int? = board.map { it.value }.maxOrNull().let { (it ?: 0) + 1 }.takeIf { it <= 5 }
}
data class Hanabi(val config: HanabiConfig, val players: List<HanabiPlayer>) {
    val idGenerator = IdGenerator()
    val colors: List<HanabiColorData> = config.colors().map { HanabiColorData(it) }
    var clueTokens: Int = config.maxClueTokens
    var failTokens: Int = 0
    var currentPlayer: Int = 0
    var turnsLeft = -1
    val current: HanabiPlayer get() = players[currentPlayer]
    var lastAffectedCards: Map<Int, String> = emptyMap()

    val deck = CardZone(config.createCards().toMutableList())

    fun affectedCards(clue: HanabiClue): List<HanabiCard> = players[clue.player].cards.cards.filter { it.matches(clue) }
    fun reveal(clue: HanabiClue): List<HanabiCard> = affectedCards(clue).onEach { it.reveal(clue) }

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

    fun score(): Int = colors.sumOf { zone -> zone.board.size }
    fun possibleClues(currentPlayer: Int): List<HanabiClue> {
        return this.players.indices.toList().minus(currentPlayer).flatMap {cluePlayer ->
            colors.map { HanabiClue(cluePlayer, it.color, null) } +
                config.values().map { HanabiClue(cluePlayer, null, it) }
        }
    }

    fun scoreDescription(): String = when (score()) {
        in 0..5 -> "Horrible. Booed by the crowd."
        in 6..10 -> "Mediocre, just a splattering of applause"
        in 11..15 -> "Honourable, but will not be remembered for very long"
        in 16..20 -> "Excellent, crowd pleasing"
        in 21..24 -> "Amazing, will be remembered for a very long time!"
        else -> "Legendary, everyone left speechless, stars in their eyes"
    }

}

data class HanabiClue(val player: Int, val color: HanabiColor?, val value: Int?) {
    fun text(): String = color?.name?.lowercase() ?: value!!.toString()
}

data class PlayNamedAction(val cardIndex: Int, val color: HanabiColor)

object HanabiGame {

    val factory = GamesApi.gameCreator(Hanabi::class)
    val giveClue = factory.action("GiveClue", HanabiClue::class)
    val discard = factory.action("Discard", Int::class)
    val play = factory.action("Play", Int::class)
    val playNamed = factory.action("PlayNamed", PlayNamedAction::class)
    val game = factory.game("Hanabi") {
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
                Hanabi(config, (0 until playerCount).map { HanabiPlayer(it) })
            }
        }
        actionRules {
            gameStart {
                // 5 cards for 2 or 3 players, otherwise 4 cards.
                val playerCount = game.players.size
                val cardPerPlayer = if (playerCount in 2..3) 5 else 4
                val cards = game.deck.random(replayable, cardPerPlayer * playerCount, "cards")
                    .map { it.card }.toList()
                game.deck.deal(cards, game.players.map { p -> p.cards }) { it.id = game.idGenerator.invoke(); it }
            }
            allActions.precondition { playerIndex == game.currentPlayer }
            allActions.precondition { game.turnsLeft != 0 }
            action(discard).options { game.current.cards.indices }
            action(discard).requires { game.clueTokens < game.config.maxClueTokens }
            action(discard).effect {
                val card = game.current.cards[action.parameter]
                moveCard(replayable, game, card, game.colorData(card.card).discard)
                log {
                    "$player discarded ${viewLink(card.card.toStateString(), "card", card.card.known(true))}"
                }
                game.lastAffectedCards = mapOf(card.card.id to "discard")
            }
            action(discard).effect { game.increaseClueTokens() }

            action(play).options { game.current.cards.indices }
            action(play).requires { !game.config.namePlayingCard }
            action(play).effect {
                val card = game.current.cards[action.parameter]
                val playArea = game.playAreaFor(card.card)
                playCardTo(card, playArea, game, this)
                if (playArea != null) {
                    log {
                        "$player played ${viewLink(card.card.toStateString(), "card", card.card.known(true))}"
                    }
                } else {
                    log {
                        "$player tried to play ${viewLink(card.card.toStateString(), "card", card.card.known(true))} but failed"
                    }
                }
                game.lastAffectedCards = mapOf(card.card.id to if (playArea != null) "play" else "fail")
            }

            action(playNamed).precondition { game.config.namePlayingCard }
            action(playNamed).effect {
                val playCard = game.current.cards[action.parameter.cardIndex]
                val playArea = game.playAreaFor(playCard.card).takeIf { playCard.card.color == action.parameter.color }
                playCardTo(playCard, playArea, game, this)
                log {
                    "$player played ${viewLink(playCard.card.toStateString(), "card", playCard.card.known(true))} as ${action.color}"
                }
                game.lastAffectedCards = mapOf(playCard.card.id to if (playArea != null) "play" else "fail")
            }
            action(playNamed).choose {
                options({ game.current.cards.indices.toList() }) {cardIndex ->
                    options({ game.colors.filter { c -> c.nextPlayable() != null }.map { c -> c.color } }) {color ->
                        parameter(PlayNamedAction(cardIndex, color))
                    }
                }
            }

            action(giveClue).requires { action.parameter.player != action.playerIndex }
            action(giveClue).requires { game.clueTokens > 0 }
            action(giveClue).requires { game.allowClue(action.parameter) }
            action(giveClue).effect { game.clueTokens-- }
            action(giveClue).effect {
                val cards = game.players[action.parameter.player].cards.cards
                    .filter { it.matches(action.parameter) }
                val actionPerformer = action.playerIndex
                logSecret(action.parameter.player) {
                    "${player(actionPerformer)} gave clue to ${player(action.player)}: ${cards.size}x ${action.text()}"
                }.publicLog {
                    "${player(actionPerformer)} gave clue to ${player(action.player)}: ${cards.size}x ${action.text()} - ${cards.filter { it.matches(action) }.joinToString(", ") {
                        viewLink(it.toStateString(), "card", it.known(true))
                    }}"
                }
                game.reveal(action.parameter)
                game.lastAffectedCards = cards.associate { it.id to "clue" }
            }
            action(giveClue).choose {
                options({ game.players.indices.toList().minus(game.currentPlayer) }) {player ->
                    val textColor = "color"
                    options({ listOf(textColor, "value") }) {clueMode ->
                        if (clueMode == textColor) {
                            options({ game.config.clueableColors() }) {color ->
                                parameter(HanabiClue(player, color, null))
                            }
                        } else {
                            options({ 1..5 }) {value ->
                                parameter(HanabiClue(player, null, value))
                            }
                        }
                    }
                }
            }
            allActions.after {
                game.nextTurn()
                if (game.turnsLeft == 0) {
                    eliminations.eliminateRemaining(WinResult.DRAW)
                }
            }

            view("currentPlayer") { game.currentPlayer }
            view("actions") {
                val chosen = actionsChosen().chosen()
                if (chosen?.actionType == giveClue.name) {
                    val chosenPlayer = chosen.chosen.first() as Int
                    val playerCards = game.players[chosenPlayer].cards
                    val modes = actionsChosen().nextSteps(String::class)
                    if (modes.isNotEmpty()) return@view mapOf("clueOptions" to modes.associateWith { emptyList<Int>() })
                    val clues =
                        actionsChosen().nextSteps(HanabiColor::class).map { HanabiClue(chosenPlayer, it, null) } +
                        actionsChosen().nextSteps(Int::class).map { HanabiClue(chosenPlayer, null, it) }
                    return@view mapOf(
                        "colors" to actionsChosen().nextSteps(HanabiColor::class).isNotEmpty(),
                        "clueOptions" to clues.associate { clue ->
                        (clue.color ?: clue.value) to playerCards.cards.filter { card -> card.matches(clue) }.map { it.id }
                    })
                }
                mapOf("clueOptions" to emptyList<Unit>())
            }
            view("lastAction") { game.lastAffectedCards }
            view("others") {
                game.players.map { player ->
                    val cards = player.cards.map { card ->
                        card.known(player.index != viewer)
                    }
                    if (player.index == viewer) return@map null
                    mapOf("index" to player.index, "cards" to cards)
                }.filterNotNull()
            }
            view("hand") {
                if (this.viewer != null) {
                    val viewPerspective = this.viewer ?: game.currentPlayer
                    val cards = game.players[viewPerspective].cards.map {
                        card -> card.known(game.isGameOver())
                    }
                    mapOf("index" to viewPerspective, "cards" to cards)
                } else mapOf()
            }
            view("colors") {
                game.colors.map {colorData ->
                    mapOf(
                        "color" to colorData.color.name.lowercase(),
                        "board" to colorData.board.map { card -> card.known(true) },
                        "discard" to colorData.discard.toList().map { card -> card.known(true) }
                    )
                }
            }
            view("cardsLeft") { game.deck.size }
            view("clues") { game.clueTokens }
            view("score") { game.score() }
            view("scoreDescription") { game.scoreDescription() }
            view("fails") { game.failTokens }
            view("maxFails") { game.config.maxFailTokens }
            /*
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
            */
        }
    }

    class Metrics(builder: MetricBuilder<Hanabi>) {
        val points = builder.endGameMetric {
            game.score()
        }
        val fails = builder.endGameMetric { game.failTokens }
        val cluesUsed = builder.actionMetric(giveClue) { game.affectedCards(action.parameter).size }
        val discarded = builder.actionMetric(discard) { 1 }
    }

    private fun playCardTo(playCard: Card<HanabiCard>, playArea: CardZone<HanabiCard>?,
        game: Hanabi, effectScope: ActionRuleScope<Hanabi, *>
    ) {
        moveCard(effectScope.replayable, game, playCard, playArea ?: game.colorData(playCard.card).discard)
        if (playCard.card.value == 5 && playArea != null) {
            game.increaseClueTokens()
        }
        if (playArea == null) {
            game.failTokens++
        }
        if (game.failTokens == game.config.maxFailTokens) {
            effectScope.eliminations.eliminateRemaining(WinResult.LOSS)
        }
        if (game.boardComplete()) {
            effectScope.eliminations.eliminateRemaining(WinResult.WIN)
        }
    }

    private fun moveCard(replayable: ReplayStateI, game: Hanabi, card: Card<HanabiCard>, destination: CardZone<HanabiCard>) {
        if (game.deck.size > 0) {
            val zone = card.zone
            val cardState = replayable.string("card") { game.deck.cards[0].toStateString() }
            val nextCard = game.deck.findState(cardState) { c -> c.toStateString() }
            card.mutateTo(destination) { it.id = game.idGenerator.invoke() }
            game.deck.card(nextCard).moveTo(zone)
            // OLD: card.moveAndReplace(destination, game.deck.card(nextCard))
            game.emptyDeckCheck()
        } else {
            card.moveTo(destination)
        }
    }

}
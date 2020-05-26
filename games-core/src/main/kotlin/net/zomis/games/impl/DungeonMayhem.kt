package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.cards.Card
import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.ActionSerialization
import net.zomis.games.dsl.ReplayableScope
import net.zomis.games.dsl.createActionType
import net.zomis.games.dsl.createGame
import net.zomis.games.dsl.sourcedest.next
import kotlin.math.min

data class DungeonMayhemConfig(
    val drawNewImmediately: Boolean = false,
    val pickPocketIgnoresCleverDisguise: Boolean = true
)
private infix fun String.card(symbol: DungeonMayhemSymbol): DungeonMayhemCard {
    return this card listOf(symbol)
}
private infix fun String.card(symbols: List<DungeonMayhemSymbol>): DungeonMayhemCard {
    return DungeonMayhemCard(this, symbols)
}
enum class DungeonMayhemSymbol {
    ATTACK, // target shields first, then players
    HEAL,
    PLAY_AGAIN,
    DRAW,
    SHIELD,

    // Specials - Yellow
    FIREBALL, // target shields first? auto-resolve?
    STEAL_SHIELD, // target shield if available
    SWAP_HITPOINTS, // target player

    // Specials - Red
    PICK_UP_CARD, // target discarded card
    DESTROY_ALL_SHIELDS,

    // Specials - Purple
    PROTECTION_ONE_TURN,
    DESTROY_SINGLE_SHIELD, // target shield if available
    STEAL_CARD, // target player

    // Specials - Blue
    HEAL_AND_ATTACK_FOR_EACH_OPPONENT,
    ALL_DISCARD_AND_DRAW,
    ;

    operator fun plus(other: DungeonMayhemSymbol): List<DungeonMayhemSymbol> = listOf(this, other)
    operator fun times(count: Int): List<DungeonMayhemSymbol> = (1..count).map { this }
    fun availableTargets(game: DungeonMayhem): List<DungeonMayhemTarget>? {
        return when (this) {
            ATTACK -> game.players.minus(game.currentPlayer).flatMap { player ->
                if (player.shields.size == 0) listOf(DungeonMayhemTarget(player.index, null, null))
                else player.shields.indices.map { DungeonMayhemTarget(player.index, it, null) }
            }
            DESTROY_SINGLE_SHIELD, STEAL_SHIELD -> game.players.flatMap { player -> player.shields.indices.map { player.index to it } }.map {
                DungeonMayhemTarget(it.first, it.second, null)
            }
            SWAP_HITPOINTS, STEAL_CARD -> game.players.minus(game.currentPlayer).map { DungeonMayhemTarget(it.index, null, null) }
            PICK_UP_CARD -> game.currentPlayer.discard.cards.mapIndexed { index, _ -> DungeonMayhemTarget(game.currentPlayerIndex, null, index) }
            else -> null
        }
    }

    fun autoResolve(count: Int, game: DungeonMayhem, playerIndex: Int, replayable: ReplayableScope): Boolean {
        val player = game.players[playerIndex]
        val targets = this.availableTargets(game)
        if (targets?.isEmpty() == true) return true
        when (this) {
            HEAL -> player.health = min(player.health + count, 10)
            DRAW -> player.drawCard(replayable, "drawEffect", count)
            FIREBALL -> repeat(count) { game.players.forEach { it.damage(3) } }
            DESTROY_ALL_SHIELDS -> game.players.forEach { it.shields.asSequence().forEach { c -> c.card.destroy(c) } }
//            PROTECTION_ONE_TURN ->
            HEAL_AND_ATTACK_FOR_EACH_OPPONENT -> {
                val opponents = game.players.minus(player)
                player.heal(opponents.size)
                opponents.forEach { it.damage(1) }
            }
            ALL_DISCARD_AND_DRAW -> repeat(count) { game.players.forEach { it.hand.moveAllTo(it.discard); it.drawCard(replayable, "allDraw", 3) } }
            SHIELD -> return true
            else -> return false
        }
        return true
    }

    fun resolve(game: DungeonMayhem, count: Int, target: DungeonMayhemTarget) {
        val player: DungeonMayhemPlayer = game.players[target.player]
        when (this) {
            ATTACK -> player.damage(count)
            DESTROY_SINGLE_SHIELD -> player.shields[target.shieldCard!!].let { it.card.destroy(it) }
            STEAL_SHIELD -> player.shields[target.shieldCard!!].moveTo(game.currentPlayer.shields)
            SWAP_HITPOINTS -> {
                val temp = game.currentPlayer.health
                game.currentPlayer.health = player.health
                player.health = temp
            }
//            STEAL_CARD ->
            PICK_UP_CARD -> player.discard[target.discardedCard!!].moveTo(player.hand)
            else -> throw IllegalStateException("No targets required for $this")
        }
    }
}
class DungeonMayhemCard(val name: String, val symbols: List<DungeonMayhemSymbol>)
class DungeonMayhemShield(val discard: CardZone<DungeonMayhemCard>, val card: DungeonMayhemCard, var health: Int) {
    fun destroy(card: Card<DungeonMayhemShield>) {
        discard.cards.add(card.remove().card)
    }
}

class DungeonMayhemPlayer(val index: Int) {
    fun drawCard(replayable: ReplayableScope, keyPrefix: String, count: Int) {
        val state = replayable.strings("$keyPrefix-$index") {
            val beforeReshuffle = min(deck.size, count)
            val fromDeck = deck.cards.shuffled().take(beforeReshuffle)
            val fromDiscard = discard.cards.shuffled().take(count - beforeReshuffle)
            (fromDeck + fromDiscard).map { it.name }
        }
        repeat(count) {iteration ->
            if (deck.size == 0) discard.moveAllTo(deck)
            deck.card(deck.cards.first { it.name == state[iteration] }).moveTo(hand)
        }
    }

    fun damage(amount: Int) {
        this.health = this.health - amount
    }
    fun heal(amount: Int) {
        this.health = min(this.health + amount, 10)
    }

    lateinit var color: String
    var health: Int = 10
    val deck = CardZone<DungeonMayhemCard>()
    val hand = CardZone<DungeonMayhemCard>()
    val shields = CardZone<DungeonMayhemShield>()
    val played = CardZone<DungeonMayhemCard>()
    val discard = CardZone<DungeonMayhemCard>()
}

private typealias s = DungeonMayhemSymbol
private operator fun Int.times(card: DungeonMayhemCard): List<DungeonMayhemCard> = (1..this).map { card }
object DungeonMayhemDecks {

    fun yellow() = "yellow" to listOf(
        2 * ("Fireball" card s.FIREBALL),
        2 * ("Evil Sneer" card s.HEAL + s.PLAY_AGAIN),
        3 * ("Speed of Thought" card s.PLAY_AGAIN * 2),
        2 * ("Charm" card s.STEAL_SHIELD),
        3 * ("Magic Missile" card s.ATTACK + s.PLAY_AGAIN),
        3 * ("Burning Hands" card s.ATTACK * 2),
        3 * ("Knowledge Is Power" card s.DRAW * 3),
        2 * ("Shield" card s.SHIELD + s.DRAW),
        1 * ("Mirror Image" card s.SHIELD * 3),
        1 * ("Stone Skin" card s.SHIELD * 2),
        4 * ("Lightning Bolt" card s.ATTACK * 3),
        2 * ("Vampiric Touch" card s.SWAP_HITPOINTS)
    ).flatten()

    fun red() = "red" to listOf(
        2 * ("For The Most Justice" card s.ATTACK * 3),
        2 * ("Divine Inspiration" card s.HEAL * 2 + s.PICK_UP_CARD),
        1 * ("Divine Smite" card s.ATTACK * 3 + s.HEAL),
        4 * ("For Even More Justice" card s.ATTACK * 2),
        2 * ("Spinning Parry" card s.SHIELD + s.DRAW),
        3 * ("Fighting Words" card s.ATTACK * 2 + s.HEAL),
        2 * ("High Carisma" card s.DRAW * 2),
        1 * ("Fluffy" card s.SHIELD * 2),
        1 * ("Cure Wounds" card s.DRAW * 2 + s.HEAL),
        2 * ("Finger-Wag of Judgement" card s.PLAY_AGAIN * 2),
        2 * ("Divine Shield" card s.SHIELD * 3),
        3 * ("For Justice" card s.ATTACK + s.PLAY_AGAIN),
        3 * ("Banishing Smite" card s.DESTROY_ALL_SHIELDS + s.PLAY_AGAIN)
    ).flatten()

    fun purple() = "purple" to listOf(
        5 * ("One Thrown Dagger" card s.ATTACK + s.PLAY_AGAIN),
        3 * ("All The Thrown Daggers" card s.ATTACK * 3),
        2 * ("Winged Serpent" card s.SHIELD + s.DRAW),
        1 * ("My Little Friend" card s.SHIELD * 3),
        4 * ("Two Thrown Daggers" card s.ATTACK * 2),
        2 * ("The Goon Squad" card s.SHIELD * 2),
        2 * ("Stolen Potion" card s.HEAL + s.PLAY_AGAIN),
        2 * ("Cunning Action" card s.PLAY_AGAIN * 2),
        2 * ("Clever Disguise" card s.PROTECTION_ONE_TURN),
        2 * ("Sneak Attack" card s.DESTROY_SINGLE_SHIELD + s.PLAY_AGAIN),
        2 * ("Pick Pocket" card s.STEAL_CARD),
        1 * ("Even More Daggers" card s.DRAW * 2 + s.HEAL)
    ).flatten()

    fun blue() = "blue" to listOf(
        5 * ("Big Axe Is The Best Axe" card s.ATTACK * 3),
        2 * ("Brutal Punch" card s.ATTACK * 2),
        1 * ("Riff" card s.SHIELD * 3),
        1 * ("Raff" card s.SHIELD * 3),
        1 * ("Snack Time" card s.DRAW * 2 + s.HEAL),
        2 * ("Flex!" card s.HEAL + s.DRAW),
        2 * ("Whirling Axes" card s.HEAL_AND_ATTACK_FOR_EACH_OPPONENT),
        2 * ("Head Butt" card s.ATTACK + s.PLAY_AGAIN),
        2 * ("Rage" card s.ATTACK * 4),
        2 * ("Open The Armory" card s.DRAW * 2),
        1 * ("Spiked Shield" card s.SHIELD * 2),
        1 * ("Bag of Rats" card s.SHIELD + s.DRAW),
        2 * ("Two Axes Are Better Than One" card s.PLAY_AGAIN * 2),
        2 * ("Mighty Toss" card s.DESTROY_SINGLE_SHIELD + s.DRAW),
        2 * ("Battle Roar" card s.ALL_DISCARD_AND_DRAW + s.PLAY_AGAIN)
    ).flatten()

}

class DungeonMayhem(playerCount: Int, val config: DungeonMayhemConfig) {
    val players = (0 until playerCount).map { DungeonMayhemPlayer(it) }
    var currentPlayerIndex: Int = 0
    val symbolsToResolve = mutableListOf<DungeonMayhemSymbol>()
    val currentPlayer: DungeonMayhemPlayer get() = players[currentPlayerIndex]
}
data class DungeonMayhemTarget(val player: Int, val shieldCard: Int?, val discardedCard: Int?)

object DungeonMayhemDsl {

    val play = createActionType("play", DungeonMayhemCard::class, ActionSerialization<DungeonMayhemCard, DungeonMayhem>({ it.name }, { key -> game.currentPlayer.hand.cards.first { it.name == key } }))
    val target = createActionType("target", DungeonMayhemTarget::class)
    val game = createGame<DungeonMayhem>("Dungeon Mayhem") {
        setup(DungeonMayhemConfig::class) {
            players(2..4)
            defaultConfig { DungeonMayhemConfig() }
            init { DungeonMayhem(playerCount, config) }
        }

        rules {
            allActions.requires { action.playerIndex == game.currentPlayerIndex }
            view("currentPlayer") { game.currentPlayerIndex }

            gameStart {
                // How to choose player decks? Before game as player options or first action in game?
                // just shuffle characters in the beginning (playing it like this for a while might make me more motivated for real solution later)
                val decks = listOf(DungeonMayhemDecks.blue(), DungeonMayhemDecks.purple(),
                        DungeonMayhemDecks.red(), DungeonMayhemDecks.yellow()).shuffled()
                val deckStrings = replayable.strings("characters") { decks.map { it.first } }

                game.players.forEachIndexed { index, player ->
                    player.color = deckStrings[index]
                    player.deck.cards.addAll(decks.first { it.first == deckStrings[index] }.second)
                    player.drawCard(replayable, "gameStart", 3)
                }
                game.players[0].drawCard(replayable, "turnStart", 1)
            }
            fun CardZone<DungeonMayhemCard>.view(): List<Map<String, Any>> {
                return this.cards.map {
                    mapOf("name" to it.name, "symbols" to it.symbols)
                }
            }
            fun CardZone<DungeonMayhemShield>.view(): List<Map<String, Any>> {
                return this.cards.map {
                    mapOf("name" to it.card.name, "health" to it.health)
                }
            }

            view("players") {
                game.players.map { mapOf(
                    "color" to it.color,
                    "health" to it.health,
                    "deck" to it.deck.size,
                    "hand" to if (viewer == it.index) it.hand.view() else it.hand.size,
                    "discard" to it.discard.view(),
                    "played" to it.played.view(),
                    "shields" to it.shields.view()
                )}
            }
            view("stack") { game.symbolsToResolve }

            action(play).options { game.currentPlayer.hand.cards }
            action(play).effect { game.symbolsToResolve.remove(DungeonMayhemSymbol.PLAY_AGAIN) }
            action(play).effect { game.symbolsToResolve.addAll(action.parameter.symbols) }
            action(play).effect {
                val shields = action.parameter.symbols.count { it == DungeonMayhemSymbol.SHIELD }
                if (shields > 0) {
                    game.currentPlayer.shields.cards.add(
                        DungeonMayhemShield(game.currentPlayer.discard, game.currentPlayer.hand.card(action.parameter).remove(), shields)
                    )
                } else game.currentPlayer.hand.card(action.parameter).moveTo(game.currentPlayer.played)
            }
            action(play).after {
                // Auto-clear effects that does not need targets
                val autoResolve = game.symbolsToResolve.filter { it.availableTargets(game) == null }.groupBy { it }
                autoResolve.forEach {
                    if (it.key.autoResolve(it.value.size, game, action.playerIndex, replayable)) {
                        game.symbolsToResolve.removeAll(it.value)
                    }
                }
            }
            action(target).options { game.symbolsToResolve.mapNotNull { it.availableTargets(game) }.firstOrNull() ?: emptyList() }
            action(target).forceUntil { game.symbolsToResolve.none { it.availableTargets(game) != null } }
            action(target).effect {
                val symbol = game.symbolsToResolve.first { it.availableTargets(game) != null }
                val count = game.symbolsToResolve.count { it == symbol }
                symbol.resolve(game, count, action.parameter)
            }
            action(target).after {
                val symbol = game.symbolsToResolve.first { it.availableTargets(game) != null }
                game.symbolsToResolve.removeAll { it == symbol }
            }

            allActions.after {
                val lost = game.players.filter { it.health <= 0 }
                    .filter { player -> eliminations.eliminations().none { it.playerIndex == player.index } }
                eliminations.eliminateMany(lost.map { it.index }, WinResult.LOSS)
            }
            allActions.after {
                val remaining = game.players.filter { it.health > 0 }
                if (remaining.size == 1) eliminations.eliminateRemaining(WinResult.WIN)
            }
            allActions.after {
                if (game.symbolsToResolve.isEmpty()) {
                    game.players.forEach { it.played.moveAllTo(it.discard) }
                    game.currentPlayerIndex = game.currentPlayerIndex.next(game.players.size)
                    game.currentPlayer.drawCard(replayable, "turnStart", 1)
                }
            }
        }
    }
}
/*
Problems:
- Attacking shields, should destroy or damage shield and then allow target next shield, etc.
  - set "attacked player" and only remove limited number of symbols
- Protection
  - trigger system? and trigger prevention?
- Tracing what's happening (effects and why actions are - not - allowed)
  - name each rule and log it?
- Steal card and play it -- auto resolve, and equip as shield
  - trigger system?
*/

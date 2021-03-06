package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.cards.Card
import net.zomis.games.cards.CardZone
import net.zomis.games.cards.random
import net.zomis.games.common.next
import net.zomis.games.dsl.*
import kotlin.math.min

data class DungeonMayhemConfig(
    val drawNewImmediately: Boolean = false,
    val fireBallPiercesShields: Boolean = false, // implementation change on 2020-06-08
    val pickPocketIgnoresCleverDisguise: Boolean = false
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
            ATTACK -> game.players.minus(game.currentPlayer).filter { it.index == game.attackedPlayer || game.attackedPlayer == null }.filter { it.health > 0 }.flatMap { player ->
                if (player.shields.size == 0) listOf(DungeonMayhemTarget(player.index, null, null))
                else player.shields.indices.map { DungeonMayhemTarget(player.index, it, null) }
            }
            DESTROY_SINGLE_SHIELD, STEAL_SHIELD -> game.players.filter { it.health > 0 }.flatMap { player -> player.shields.indices.map { player.index to it } }.map {
                DungeonMayhemTarget(it.first, it.second, null)
            }
            SWAP_HITPOINTS, STEAL_CARD -> game.players.minus(game.currentPlayer).filter { it.health > 0 }.map { DungeonMayhemTarget(it.index, null, null) }
            PICK_UP_CARD -> game.currentPlayer.discard.cards.mapIndexed { index, _ -> DungeonMayhemTarget(game.currentPlayerIndex, null, index) }
            else -> null
        }
    }

    fun autoResolve(count: Int, game: DungeonMayhem, trigger: DungeonMayhemPlayCard, replayable: ReplayableScope): Boolean {
        val player = game.players[trigger.player.index].takeUnless { it.protectedFrom(trigger) }
        val targets = this.availableTargets(game)
        if (targets?.isEmpty() == true) return true
        when (this) {
            HEAL -> if (player != null) player.health = min(player.health + count, 10)
            DRAW -> player?.drawCard(replayable, "drawEffect", count)
            FIREBALL -> repeat(count) {
                game.players.filter { !it.protectedFrom(trigger) }.forEach {
                    if (game.config.fireBallPiercesShields)
                        it.damage(3)
                    else it.damageShieldsOrPlayer(3)
                }
            }
            DESTROY_ALL_SHIELDS -> game.players.filter { !it.protectedFrom(trigger) }.forEach { it.shields.asSequence().forEach { c -> c.card.destroy(c) } }
            PROTECTION_ONE_TURN -> player?.protected = true
            HEAL_AND_ATTACK_FOR_EACH_OPPONENT -> {
                val opponents = game.players.minus(player).filterNotNull().filter { it.alive() }
                player?.heal(opponents.size)
                opponents.filter { !it.protectedFrom(trigger) }.forEach { it.damageShieldsOrPlayer(1) }
            }
            ALL_DISCARD_AND_DRAW -> repeat(count) {
                game.players.filter { !it.protectedFrom(trigger) }
                    .forEach { it.hand.moveAllTo(it.discard); it.drawCard(replayable, "allDraw", 3) }
            }
            SHIELD -> return true
            else -> return false
        }
        return true
    }

    fun resolve(game: DungeonMayhem, scope: GameRuleTriggerScope<DungeonMayhem, DungeonMayhemEffect>, playEffect: GameRuleTrigger<DungeonMayhem, DungeonMayhemPlayCard>) {
        val target = scope.trigger.target
        val player: DungeonMayhemPlayer = game.players[target.player]
        return when (this) {
            ATTACK -> {
                if (target.shieldCard != null) player.shields[target.shieldCard].card.health -= scope.trigger.count
                else player.damage(scope.trigger.count)
            }
            DESTROY_SINGLE_SHIELD -> player.shields[target.shieldCard!!].let { it.card.destroy(it) }
            STEAL_SHIELD -> player.shields[target.shieldCard!!].moveTo(game.currentPlayer.shields)
            SWAP_HITPOINTS -> {
                val temp = game.currentPlayer.health
                game.currentPlayer.health = player.health
                player.health = temp
            }
            STEAL_CARD -> playEffect(DungeonMayhemPlayCard(game.config, game.currentPlayer, player,
                player.deck.random(scope.replayable, 1, "topCard").first()
            )).let {  }
            PICK_UP_CARD -> player.discard[target.discardedCard!!].moveTo(player.hand)
            else -> throw IllegalStateException("No targets required for $this")
        }
    }
}
data class DungeonMayhemCard(val name: String, val symbols: List<DungeonMayhemSymbol>): Replayable {
    override fun toStateString(): String = name

    fun view() = mapOf("name" to name, "symbols" to symbols.map { it.name })
}
data class DungeonMayhemShield(val discard: CardZone<DungeonMayhemCard>, val card: DungeonMayhemCard, var health: Int) {
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

    fun damageShieldsOrPlayer(amount: Int) {
        var remaining = amount
        for (shield in this.shields.cards) {
            val shieldDamage = min(remaining, shield.health)
            shield.health -= shieldDamage
            remaining -= shieldDamage
        }
        this.damage(remaining)
    }

    fun alive(): Boolean = health > 0
    fun protectedFrom(trigger: DungeonMayhemPlayCard): Boolean {
        if (!this.protected) return false
        val comparePlayer = if (trigger.config.pickPocketIgnoresCleverDisguise) trigger.player
            else trigger.ownedByPlayer
        return this != comparePlayer
    }

    var protected: Boolean = false
    lateinit var character: DungeonMayhemCharacter
    var health: Int = 10
    val deck = CardZone<DungeonMayhemCard>()
    val hand = CardZone<DungeonMayhemCard>()
    val shields = CardZone<DungeonMayhemShield>()
    val played = CardZone<DungeonMayhemCard>()
    val discard = CardZone<DungeonMayhemCard>()
}

private typealias s = DungeonMayhemSymbol
private operator fun Int.times(card: DungeonMayhemCard): List<DungeonMayhemCard> = (1..this).map { card }
data class DungeonMayhemCharacter(val color: String, val name: String, val className: String)
object DungeonMayhemDecks {

    fun yellow() = DungeonMayhemCharacter("yellow", "Azzan the Mystic", "Wizard") to listOf(
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

    fun red() = DungeonMayhemCharacter("red", "Lia the Radiant", "Paladin") to listOf(
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

    fun purple() = DungeonMayhemCharacter("purple", "Oriax the Clever", "Rogue") to listOf(
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

    fun blue() = DungeonMayhemCharacter("blue", "Sutha the Skullcrusher", "Barbarian") to listOf(
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

data class DungeonMayhemResolveSymbol(val player: DungeonMayhemPlayer, val symbol: DungeonMayhemSymbol)
class DungeonMayhem(playerCount: Int, val config: DungeonMayhemConfig) {
    val players = (0 until playerCount).map { DungeonMayhemPlayer(it) }
    var currentPlayerIndex: Int = 0
    val symbolsToResolve = mutableListOf<DungeonMayhemResolveSymbol>()
    val currentPlayer: DungeonMayhemPlayer get() = players[currentPlayerIndex]
    var attackedPlayer: Int? = null
}
data class DungeonMayhemTarget(val player: Int, val shieldCard: Int?, val discardedCard: Int?)
data class DungeonMayhemPlayCard(
    val config: DungeonMayhemConfig,
    val player: DungeonMayhemPlayer,
    val ownedByPlayer: DungeonMayhemPlayer,
    val card: Card<DungeonMayhemCard>
)
data class DungeonMayhemEffect(
    val game: DungeonMayhem,
    val byPlayer: DungeonMayhemPlayer,
    val cardOwner: DungeonMayhemPlayer,
    val symbol: DungeonMayhemSymbol,
    val count: Int,
    val target: DungeonMayhemTarget
)

object DungeonMayhemDsl {

    val factory = GameCreator(DungeonMayhem::class)

    val play = factory.action("play", DungeonMayhemCard::class)
        .serialization({ it.name }, { key -> game.currentPlayer.hand.cards.first { it.name == key } })
    val target = factory.action("target", DungeonMayhemTarget::class)
    val game = factory.game("Dungeon Mayhem") {
        setup(DungeonMayhemConfig::class) {
            players(2..4)
            defaultConfig { DungeonMayhemConfig() }
            init { DungeonMayhem(playerCount, config) }
        }

        actionRules {
            allActions.precondition { playerIndex == game.currentPlayerIndex }
            view("currentPlayer") { game.currentPlayerIndex }
            val newTurnDrawCard = trigger(Unit::class).effect {
                game.currentPlayer.drawCard(replayable, "turnStart", 1)
                game.currentPlayer.protected = false
            }
            val playTrigger = trigger(DungeonMayhemPlayCard::class)
            val effectTrigger = trigger(DungeonMayhemEffect::class).effect {
                trigger.symbol.resolve(game, this, playTrigger)
            }
            effectTrigger.map {
                if (trigger.symbol == DungeonMayhemSymbol.ATTACK) {
                    val player = game.players[trigger.target.player]
                    val health = player.shields.cards.getOrNull(trigger.target.shieldCard ?: -1)?.health
                    if (health != null && health < trigger.count) trigger.copy(count = health) else trigger
                } else trigger
            }
            effectTrigger.ignoreEffectIf {
                if (!game.config.pickPocketIgnoresCleverDisguise || trigger.cardOwner.index != trigger.target.player) {
                    game.players[trigger.target.player].protected
                } else false
            }
            effectTrigger.after { (1..trigger.count).forEach { game.symbolsToResolve.remove(game.symbolsToResolve.first { it.symbol == trigger.symbol }) } }
            effectTrigger.after { if (game.symbolsToResolve.any { it.symbol == trigger.symbol }) game.attackedPlayer = trigger.target.player }

            gameStart {
                // How to choose player decks? Before game as player options or first action in game?
                // just shuffle characters in the beginning (playing it like this for a while might make me more motivated for real solution later)
                val decks = listOf(DungeonMayhemDecks.blue(), DungeonMayhemDecks.purple(),
                        DungeonMayhemDecks.red(), DungeonMayhemDecks.yellow()).shuffled()
                val deckStrings = replayable.strings("characters") { decks.map { it.first.color } }

                game.players.forEachIndexed { index, player ->
                    val playerSetup = decks.first { it.first.color == deckStrings[index] }
                    player.character = playerSetup.first
                    player.deck.cards.addAll(playerSetup.second)
                    player.drawCard(replayable, "gameStart", 3)
                }
                newTurnDrawCard(Unit)
            }
            fun CardZone<DungeonMayhemCard>.view(): List<Map<String, Any>> {
                return this.cards.map { card ->
                    mapOf("name" to card.name, "symbols" to card.symbols.map { it.name })
                }
            }
            fun CardZone<DungeonMayhemShield>.view(): List<Map<String, Any>> {
                return this.cards.map {
                    mapOf("name" to it.card.name, "health" to it.health)
                }
            }

            view("players") {
                game.players.map { mapOf(
                    "character" to it.character,
                    "health" to it.health,
                    "deck" to it.deck.size,
                    "hand" to if (viewer == it.index) it.hand.view() else it.hand.size,
                    "protected" to it.protected,
                    "discard" to it.discard.view(),
                    "played" to it.played.view(),
                    "shields" to it.shields.view()
                )}
            }
            view("stack") { game.symbolsToResolve.map { it.symbol } }

            action(play).options { game.currentPlayer.hand.cards }
            action(play).effect { game.symbolsToResolve.remove(game.symbolsToResolve.firstOrNull { it.symbol == DungeonMayhemSymbol.PLAY_AGAIN }) }
            action(play).effect {
                log { "$player plays ${viewLink(action.name, "card", action.view())}" }
                playTrigger(DungeonMayhemPlayCard(game.config, game.currentPlayer, game.currentPlayer,
                    game.currentPlayer.hand.card(action.parameter)))
            }
            playTrigger.effect { game.symbolsToResolve.addAll(trigger.card.card.symbols.map { DungeonMayhemResolveSymbol(trigger.ownedByPlayer, it) }) }
            playTrigger.effect {
                val shields = trigger.card.card.symbols.count { it == DungeonMayhemSymbol.SHIELD }
                if (shields > 0) {
                    trigger.player.shields.cards.add(
                        DungeonMayhemShield(trigger.ownedByPlayer.discard, trigger.card.remove(), shields)
                    )
                } else trigger.card.moveTo(trigger.ownedByPlayer.played)
            }
            playTrigger.after {
                // Auto-clear effects that does not need targets
                val autoResolve = game.symbolsToResolve.filter {symbol ->
                    symbol.symbol.availableTargets(game).let { it == null || it.isEmpty() }
                }.groupBy { it }
                autoResolve.forEach {
                    if (it.key.symbol.autoResolve(it.value.size, game, trigger, replayable)) {
                        game.symbolsToResolve.removeAll(it.value)
                    }
                }
            }
            action(target).options { game.symbolsToResolve.mapNotNull { it.symbol.availableTargets(game) }.firstOrNull() ?: emptyList() }
            action(target).forceWhen { game.symbolsToResolve.any { it.symbol.availableTargets(game) != null } }
            action(target).effect {
                val symbol = game.symbolsToResolve.first { it.symbol.availableTargets(game) != null }
                val count = game.symbolsToResolve.count { it == symbol }
                val playerTarget = game.players[action.parameter.player]
                log {
                    val target = when {
                        action.discardedCard != null -> playerTarget.discard[action.discardedCard!!].card.let { viewLink(it.name, "card", it.view()) }
                        action.shieldCard != null -> playerTarget.shields[action.shieldCard!!].card.card.let {
                            player(playerTarget.index) + " " + viewLink(it.name, "card", it.view())
                        }
                        else -> player(playerTarget.index)
                    }
                    "$player targets $target with ${count}x ${symbol.symbol.name}"
                }
                effectTrigger(DungeonMayhemEffect(game, playerTarget, symbol.player, symbol.symbol, count, action.parameter))
            }
            action(target).after { if (game.symbolsToResolve.none { it.symbol == DungeonMayhemSymbol.ATTACK }) game.attackedPlayer = null }

            allActions.after {
                if (game.currentPlayer.hand.size == 0) {
                    game.currentPlayer.drawCard(replayable, "empty-hand", 2)
                }
            }
            allActions.after {
                game.players.forEach { player ->
                    player.shields.cards.filter { it.health <= 0 }.asSequence().forEach { shield ->
                        player.shields.card(shield).let { it.card.destroy(it) }
                    }
                }
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
                    do {
                        game.currentPlayerIndex = game.currentPlayerIndex.next(game.players.size)
                    } while (eliminations.remainingPlayers().isNotEmpty() && !eliminations.remainingPlayers().contains(game.currentPlayerIndex))
                    newTurnDrawCard(Unit)
                }
            }
        }
    }
}

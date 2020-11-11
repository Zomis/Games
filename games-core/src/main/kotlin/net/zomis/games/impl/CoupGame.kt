package net.zomis.games.impl

import net.zomis.games.cards.CardZone
import net.zomis.games.common.GameEvents
import net.zomis.games.common.next
import net.zomis.games.dsl.GameEventsExecutor
import kotlin.math.ceil
import kotlin.math.max

enum class CoupCharacter {
    ASSASSIN,
    CONTESSA,
    DUKE,
    AMBASSADOR,
    CAPTAIN,
    ;
}
enum class CoupActionType(val needsTarget: Boolean, val claim: CoupCharacter?, val blockableBy: List<CoupCharacter> = emptyList()) {
    INCOME(false, null),
    FOREIGN_AID(false, null, listOf(CoupCharacter.DUKE)),
    COUP(true, null),
    TAX(false, CoupCharacter.DUKE),
    ASSASSINATE(true, CoupCharacter.ASSASSIN, listOf(CoupCharacter.CONTESSA)),
    EXCHANGE(false, CoupCharacter.AMBASSADOR),
    STEAL(true, CoupCharacter.CAPTAIN, listOf(CoupCharacter.CAPTAIN, CoupCharacter.AMBASSADOR)),
    ;
}
data class CoupPlayer(val playerIndex: Int) {
    fun isAlive(): Boolean = influence.size > 0

    var coins: Int = 0
    val influence = CardZone<CoupCharacter>()
    val previousInfluence = CardZone<CoupCharacter>()
}
data class CoupClaim(val player: CoupPlayer, val character: CoupCharacter, val awaitingPlayers: MutableList<Int>) {
    fun canReveal(): Boolean {
        return player.influence.cards.contains(character)
    }

    init { awaitingPlayers.remove(player.playerIndex) }

    fun accept(playerIndex: Int): Boolean {
        awaitingPlayers.remove(playerIndex)
        return awaitingPlayers.isEmpty()
    }

}

data class CoupConfig(val gainMoneyOnSuccessfulChallenge: Int)
data class CoupChallengeResolved(val challengedClaim: CoupChallengedClaim, val trueClaim: Boolean)
class Coup(val events: GameEventsExecutor, val config: CoupConfig, val playersCount: Int) {
    val players = (0 until playersCount).map { CoupPlayer(it) }
    private val cardsPerCharacter = max(3, ceil((3 + playersCount * 2) / 5.0).toInt())
    val deck: CardZone<CoupCharacter> = CoupCharacter.values().flatMap { character -> (1..cardsPerCharacter).map { character } }.let { CardZone(it.toMutableList()) }
    var currentPlayerIndex = 0
    val currentPlayer get() = players[currentPlayerIndex]

    val stack = GameStack()
    val challengeEvents = GameEvents<CoupChallengeResolved>(events)
    // A challenge can only be made once. If action is targeting someone, then only targeted player may counteract.

    fun nextPlayer() {
        do {
            currentPlayerIndex = currentPlayerIndex.next(playersCount)
        } while (currentPlayer.influence.size == 0)
    }
}
data class CoupAction(val player: CoupPlayer, val action: CoupActionType, val target: CoupPlayer? = null)

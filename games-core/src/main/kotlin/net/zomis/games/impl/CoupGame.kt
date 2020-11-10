package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
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
enum class CoupResponseType {
    APPROVE,
    COUNTERACT,
    CHALLENGE,
}
data class CoupPlayerResponse(val responseType: CoupResponseType, val characterClaim: CoupCharacter?)
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

class Coup(val playersCount: Int) {
    val players = (0 until playersCount).map { CoupPlayer(it) }
    private val cardsPerCharacter = max(3, ceil((3 + playersCount * 2) / 5.0).toInt())
    val deck: CardZone<CoupCharacter> = CoupCharacter.values().flatMap { character -> (1..cardsPerCharacter).map { character } }.let { CardZone(it.toMutableList()) }
    var currentPlayerIndex = 0
    val currentPlayer get() = players[currentPlayerIndex]

    val stack = GameStack()
    // A challenge can only be made once. If action is targeting someone, then only targeted player may counteract.

/*
    var currentAction: CoupAction? = null
    var currentCounterAction: Pair<CoupPlayer, CoupCharacter>? = null
    val currentClaim: CoupClaim? get() {
        return currentCounterAction?.let { CoupClaim(it.first, it.second, true) } ?:
        currentAction?.action?.claim?.let { CoupClaim(currentPlayer, it, false) }
    }
    var playerToLoseInfluence: CoupPlayer? = null
    var playerLookingAtDeck: CoupPlayer? = null

    val claimIsChallenged: Boolean get() {
        val claim = currentClaim ?: return false
        return if (claim.counterAction) {
            players.any { it.counterActionAccepted == false }
        } else {
            players.any { it.actionResponse?.responseType == CoupResponseType.CHALLENGE }
        }
    }
*/

    fun nextPlayer() {
        do {
            currentPlayerIndex = currentPlayerIndex.next(playersCount)
        } while (currentPlayer.influence.size == 0)
    }
}
data class CoupAction(val player: CoupPlayer, val action: CoupActionType, val target: CoupPlayer? = null)

object CoupGame {
    val factory = GamesApi.gameCreator(Coup::class)
    val perform = factory.action("perform", CoupAction::class).serializer { it.action.name + "-" + it.target?.playerIndex }
    val counter = factory.action("counteract", CoupCharacter::class).serializer { it.name }
    val approve = factory.action("approve", Unit::class)
    val reveal = factory.action("reveal", Unit::class)
    val challenge = factory.action("challenge", Unit::class)
    val ambassadorPutBack = factory.action("putBack", CoupCharacter::class).serializer { it.name }
    val loseInfluence = factory.action("lose", CoupCharacter::class).serializer { it.name }

    val game = factory.game("Coup") {
        setup {
            players(2..6)
            init {
                Coup(playerCount)
            }
        }
        actionRules {
            gameStart {
                game.players.forEach { player ->
                    val influence = game.deck.random(replayable, 2, "start-" + player.playerIndex) { it.name }
                    influence.forEach { it.moveTo(player.influence) }
                }
            }
            action(perform) {
                precondition { game.currentPlayerIndex == playerIndex }
                precondition { game.stack.isEmpty() }
                effect {
                    game.stack.add(action.parameter)
//                    game.stack.add(CoupChallengeOrCounteract(game, action.parameter, null))
                }
            }
            action(approve) {
//                precondition { game.stack.peek() is CoupChallengeOrCounteract }
//                effect {
//                    val current = game.stack.peek() as CoupChallengeOrCounteract
//                    current.playerResponse(playerIndex, CoupResponseType.APPROVE)
//                }
            }
            action(challenge) {
                // Allow both challenges and counteractions at the same time.
                // If there is a challenge, perform it directly.
                // If there is a counteraction, still allow challenges until all players have answered
//                precondition { game.stack.peek() is CoupChallengeOrCounteract awaitingResponseFrom(playerIndex) }
//                effect {
//                    game.playerResponse(playerIndex, CoupResponseType.CHALLENGE)
//                }
            }
            action(counter) {
//                precondition { game.awaitingResponseFrom(playerIndex) }
//                precondition { game.currentAction != null }
//                precondition { game.currentCounterAction == null }
//                precondition { game.currentAction?.target == null || game.currentAction?.target?.playerIndex == playerIndex }
//                precondition { game.currentAction?.action?.blockableBy?.isNotEmpty() == true }
//                options { game.currentAction!!.action.blockableBy.asIterable() }
//                effect {
//                    game.playerResponse(playerIndex, CoupResponseType.COUNTERACT, action.parameter)
//                }
            }
            action(ambassadorPutBack) {
//                forceWhen { game.playerLookingAtDeck == game.players[playerIndex] }
                options { game.currentPlayer.influence.cards }
                effect {
                    game.currentPlayer.influence.card(action.parameter).moveTo(game.deck)
                    logSecret(playerIndex) { "$player put back ${action.name}" }

                    if (game.currentPlayer.influence.size <= 2 - game.currentPlayer.previousInfluence.size) {
//                        game.playerLookingAtDeck = null
                        game.nextPlayer()
                    }
                }
            }
            action(reveal) {
//                precondition { game.currentClaim?.player?.playerIndex == playerIndex }
//                precondition { game.currentClaim?.canReveal() ?: false }
//                precondition { game.claimIsChallenged }
                effect {
                    log { "$player revealed the influence" }
                    // Perform or deny current action
                }
            }
            action(loseInfluence) {
//                precondition { game.claimIsChallenged }
//                precondition { game.currentClaim?.player?.playerIndex == playerIndex }
//                options { game.currentClaim!!.player.influence.cards }
                effect {
//                    game.currentClaim!!.player.influence.card(action.parameter).moveTo(game.playerToLoseInfluence!!.previousInfluence)
                }
            }
            allActions.after {
                // Eliminate players without influence
                eliminations.remainingPlayers().map { game.players[it] }.filter { it.influence.size == 0 }.forEach {
                    eliminations.result(it.playerIndex, WinResult.LOSS)
                }
                if (eliminations.remainingPlayers().size == 1) {
                    eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
            view("players") {
                game.players.map {
                    mapOf(
                        "coins" to it.coins,
                        "influence" to it.influence
                    )
                }
            }
//            view("fds") { game. }
            view("deck") { game.deck.size }
        }
        testCase(players = 3) {
            state("start-0", listOf("CONTESSA", "ASSASSIN"))

            // Challenge and player did not reveal
//            action(0, perform, CoupAction(CoupActionType.TAX))
            action(2, approve, Unit)
            action(1, challenge, Unit)

            action(0, loseInfluence, CoupCharacter.CONTESSA)
            expectEquals(listOf(CoupCharacter.ASSASSIN), game.players[0].influence.cards)
            expectEquals(1, game.currentPlayerIndex)
        }
        testCase(players = 3) {
            state("start-0", listOf("CONTESSA", "DUKE"))

            // Challenge and player reveals
//            action(0, perform, CoupAction(CoupActionType.TAX))
            action(2, challenge, Unit)
            action(1, challenge, Unit)

            action(0, reveal, Unit)
            expectEquals(2, game.players[2].influence.size)
            expectEquals(1, game.players[1].influence.size)
        }
        testCase(players = 3) {
            state("start-0", listOf("CONTESSA", "DUKE"))

            // Challenge and player did not reveal
//            action(0, perform, CoupAction(CoupActionType.TAX))
            action(2, approve, Unit)
            action(1, challenge, Unit)

            action(0, loseInfluence, CoupCharacter.CONTESSA)
            expectEquals(listOf(CoupCharacter.ASSASSIN), game.players[0].influence.cards)
            expectEquals(1, game.currentPlayerIndex)
        }
        testCase(players = 3) {
            // Simple setup, approved action, nothing strange here
//            action(0, perform, CoupAction(CoupActionType.TAX))
            action(1, approve, Unit)
            action(2, approve, Unit)

            expectEquals(1, game.currentPlayerIndex)
            expectEquals(5, game.players[0].coins)
        }
        testCase(players = 3) {
            // Counteraction accepted
//            action(0, perform, CoupAction(CoupActionType.STEAL, game.players[1]))
            action(1, counter, CoupCharacter.AMBASSADOR)
            action(2, approve, Unit)

            action(0, approve, Unit)
            action(2, approve, Unit)

            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)
        }
        testCase(players = 3) {
            // Counteraction challenged and counteraction successful (player who countered revealed blocking character)
//            action(0, perform, CoupAction(CoupActionType.STEAL, game.players[1]))
            action(1, counter, CoupCharacter.AMBASSADOR)
            action(2, approve, Unit)

            action(0, approve, Unit)
            action(2, challenge, Unit)

            action(1, reveal, Unit)

            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)

        }
        testCase(players = 3) {
            // Counteraction challenged and counteraction failed (player who countered did not reveal blocking character)
            state("start-1", listOf("CONTESSA", "ASSASSIN"))

//            action(0, perform, CoupAction(CoupActionType.STEAL, game.players[1]))
            action(1, counter, CoupCharacter.AMBASSADOR)
            action(2, approve, Unit)

            action(0, approve, Unit)
            action(2, challenge, Unit)

            action(1, loseInfluence, CoupCharacter.CONTESSA)

            expectEquals(1, game.currentPlayerIndex)
            expectEquals(4, game.players[0].coins)
            expectEquals(0, game.players[1].coins)
        }
        testCase(players = 3) {
            // Both challenge and counteract. Challenge fails, counteraction success
            state("start-0", listOf("CAPTAIN", "ASSASSIN"))

//            action(0, perform, CoupAction(CoupActionType.STEAL, game.players[1]))
            action(1, counter, CoupCharacter.AMBASSADOR)
            action(2, challenge, Unit)

            branches {
                branch("counter first") {
                    action(1, counter, CoupCharacter.AMBASSADOR)
                    action(2, challenge, Unit)
                }
                branch("challenge first") {
                    action(2, challenge, Unit)
                    action(1, counter, CoupCharacter.AMBASSADOR)
                }
            }
            action(0, reveal, Unit)

            action(0, approve, Unit)
            action(2, approve, Unit)

            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)
            expectEquals(2, game.players[1].coins)
        }
    }

}

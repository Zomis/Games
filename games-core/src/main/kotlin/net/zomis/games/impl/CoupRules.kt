package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.common.next
import kotlin.math.min

class GameStack {
    val stack = mutableListOf<Any>()

    fun add(item: Any) {
        stack.add(item)
    }
    fun asList(): List<Any> = stack.toList()
    fun peek(): Any? = stack.lastOrNull()
    fun isEmpty(): Boolean = stack.isEmpty()
    fun pop(): Any = stack.removeAt(stack.lastIndex)
    fun clear() = stack.clear()

    // Stack in model, use rules as normal (either action-based or rule-based)
    // Choice, Triggers, Effects... player decisions (changable until cleared?)
}

data class CoupChallengedClaim(val claim: CoupClaim, val challengedBy: Int): CoupLoseInfluence {
    override val player: CoupPlayer
        get() = claim.player
}
data class CoupCounteract(val claim: CoupClaim)
data class CoupLoseInfluenceTask(override val player: CoupPlayer): CoupLoseInfluence
interface CoupLoseInfluence {
    val player: CoupPlayer
}
data class CoupAwaitCountering(val target: CoupPlayer?, val blockableBy: List<CoupCharacter>, val possibleCounters: MutableList<Int>)
data class CoupPlayerExchangeCards(val player: CoupPlayer, val startSize: Int)

object CoupRuleBased {

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
        view {
            value("players") { game ->
                game.players.map {player ->
                    mapOf(
                        "influence" to player.influence.cards.map { it.name },
                        "coins" to player.coins,
                        "previousInfluence" to player.previousInfluence.cards.map { it.name }
                    )
                }
            }
            value("currentPlayer") { it.currentPlayerIndex }
            value("deck") { it.deck.size }
            value("stack") {game ->
                game.stack.asList().map { it.toString() }
            }
        }
        actionRules {
            allActions.precondition {
                game.players[playerIndex].influence.size > 0
            }
        }
        gameRules {
            // TODO: rules.players.lastPlayerStanding()
            rule("last player standing") {
                appliesWhen { eliminations.remainingPlayers().size == 1 }
                effect { eliminations.eliminateRemaining(WinResult.WIN) }
            }
            // TODO: rules.players.losers { game.players.filter { it.influence.size == 0 }.map { it.playerIndex } }
            rule("eliminate players") {
                applyForEach {
                    game.players.filter { it.influence.size == 0 && eliminations.remainingPlayers().contains(it.playerIndex) }
                }.effect {
                    eliminations.result(it.playerIndex, WinResult.LOSS)
                }
            }
            rule("skip eliminated players") {
                appliesWhen { !eliminations.remainingPlayers().contains(game.currentPlayerIndex) }
                effect { game.currentPlayerIndex = game.currentPlayerIndex.next(eliminations.playerCount) }
            }
            rule("await countering: no more counters") {
                appliesWhen {
                    val stackTop = game.stack.peek()
                    stackTop is CoupAwaitCountering && stackTop.possibleCounters.isEmpty()
                }
                effect { game.stack.pop() }
            }
            rule("setup") {
                gameSetup {
                    game.players.forEach { player ->
                        val influence = game.deck.random(replayable, 2, "start-" + player.playerIndex) { it.name }
                        influence.forEach { it.moveTo(player.influence) }
                    }
                }
                gameSetup {
                    game.players.forEach { it.coins = 2 }
                }
            }
            rule("lose influence") {
                appliesWhen { game.stack.peek() is CoupLoseInfluence || game.stack.peek() is CoupChallengedClaim }
                action(loseInfluence) {
                    precondition { playerIndex == (game.stack.peek() as CoupLoseInfluence).player.playerIndex }
                    requires {
                        val task = game.stack.peek()
                        if (task is CoupChallengedClaim) task.claim.character != action.parameter else true
                    }
                    options { (game.stack.peek() as CoupLoseInfluence).player.influence.cards.toSet() }
                    perform {
                        val player = game.players[action.playerIndex]
                        player.influence.card(action.parameter).moveTo(player.previousInfluence)

                        val topTask = game.stack.pop() as CoupLoseInfluence
                        val actionTask = game.stack.asList().filterIsInstance<CoupAction>().first()
                        if (topTask.player == actionTask.player) {
                            // Everything either challenged or countered
                            if (actionTask.action == CoupActionType.ASSASSINATE) {
                                actionTask.player.coins += 3
                            }
                            game.currentPlayerIndex = game.currentPlayerIndex.next(eliminations.playerCount)
                            game.stack.clear()
                        }
                        if (game.stack.peek() is CoupCounteract) {
                            game.stack.pop()
                            return@perform
                        }
                    }
                }
            }
            rule("reveal card") {
                action(reveal) {
                    precondition { game.stack.peek() is CoupChallengedClaim }
                    precondition { playerIndex == (game.stack.peek() as CoupChallengedClaim).player.playerIndex }
                    precondition {
                        val challengedClaim = game.stack.peek() as CoupChallengedClaim
                        challengedClaim.claim.player.influence.cards.contains(challengedClaim.claim.character)
                    }
                    perform {
                        // Put back card, draw a new one
                        val challengedClaim = game.stack.pop() as CoupChallengedClaim
                        challengedClaim.claim.player.influence.card(challengedClaim.claim.character).moveTo(game.deck)
                        game.deck.random(replayable, 1, "replacement") { it.name }.forEach {
                            it.moveTo(challengedClaim.claim.player.influence)
                        }

                        game.stack.add(CoupLoseInfluenceTask(game.players[challengedClaim.challengedBy]))
                    }
                }
            }
            rule("exchange cards") {
                appliesWhen { game.stack.peek() is CoupPlayerExchangeCards }
                action(ambassadorPutBack) {
                    precondition { playerIndex == (game.stack.peek() as CoupPlayerExchangeCards).player.playerIndex }
                    options { game.players[playerIndex].influence.cards.toSet() }
                    perform {
                        val exchangeTask = game.stack.peek() as CoupPlayerExchangeCards
                        game.players[playerIndex].influence.card(action.parameter).moveTo(game.deck)
                        if (game.players[playerIndex].influence.size == exchangeTask.startSize) {
                            game.stack.pop()
                            game.currentPlayerIndex = game.currentPlayerIndex.next(eliminations.playerCount)
                        }
                    }
                }
            }
            rule("choose actions") {
                // stack is: CoupAction(type, target, claim?)
                // after consensus possible stack: CoupAction(ASSASSINATE...), CHALLENGE
                // challenge marked as success/failure
                // possible stack: CoupAction(ASSASSINATE...), CHALLENGE, COUNTERACT

                appliesWhen { game.stack.isEmpty() }
                action(perform) {
                    precondition { game.currentPlayerIndex == playerIndex }
                    choose {
                        optionsWithIds({ CoupActionType.values().asIterable().map { it.name to it } }) {type ->
                            val player = context.game.players[context.playerIndex]
                            if (type.needsTarget) {
                                optionsWithIds({ game.players.minus(player).filter { it.isAlive() }
                                        .map { it.playerIndex.toString() to it } }) {target ->
                                    parameter(CoupAction(player, type, target))
                                }
                            } else {
                                parameter(CoupAction(player, type, null))
                            }
                        }
                    }
                }
            }
            rule("coup action") {
                action(perform) {
                    appliesForActions { action.parameter.action == CoupActionType.COUP }
                    rule("requires money") {
                        requires { action.parameter.player.coins >= 7 }
                        perform { action.parameter.player.coins -= 7 }
                    }
                }
                rule("must perform coup") {
                    appliesWhen { game.currentPlayer.coins >= 10 }
                    action(perform) {
                        requires { action.parameter.action == CoupActionType.COUP }
                    }
                }
            }
            rule("assassinate") {
                action(perform) {
                    appliesForActions { action.parameter.action == CoupActionType.ASSASSINATE }
                    rule("requires money") {
                        requires { action.parameter.player.coins >= 3 }
                        perform { action.parameter.player.coins -= 3 }
                    }
                }
            }
            rule("perform action") {
                action(perform) {
                    precondition { game.stack.isEmpty() }
                    precondition { game.currentPlayerIndex == playerIndex }
                    perform {
                        game.stack.add(action.parameter)
                        if (action.parameter.action.blockableBy.isNotEmpty()) {
                            val possibleCounters = eliminations.remainingPlayers().toMutableList()
                            possibleCounters.remove(action.playerIndex)
                            if (action.parameter.target != null) {
                                possibleCounters.retainAll(listOf(action.parameter.target!!.playerIndex))
                            }
                            game.stack.add(CoupAwaitCountering(action.parameter.target, action.parameter.action.blockableBy, possibleCounters))
                        }
                        if (action.parameter.action.claim != null) {
                            game.stack.add(CoupClaim(action.parameter.player, action.parameter.action.claim!!,
                                eliminations.remainingPlayers().toMutableList()
                            ))
                        }
                    }
                }
            }
            rule("approve needs one applicable rule active") {
                action(approve) {
                    precondition {
                        val task = game.stack.peek()
                        task is CoupAwaitCountering || task is CoupClaim
                    }
                }
            }
            rule("counteract possibility") {
                action(approve) {
                    precondition {
                        if (game.stack.peek() !is CoupAwaitCountering) return@precondition true
                        val task = game.stack.peek() as CoupAwaitCountering
                        task.possibleCounters.contains(playerIndex)
                    }
                    perform {
                        if (game.stack.peek() !is CoupAwaitCountering) return@perform
                        val task = game.stack.peek() as CoupAwaitCountering
                        task.possibleCounters.remove(action.playerIndex)
                        if (task.possibleCounters.isEmpty()) {
                            game.stack.pop()
                        }
                    }
                }
                action(counter) {
                    precondition {
                        if (game.stack.peek() !is CoupAwaitCountering) return@precondition false
                        val task = game.stack.peek() as CoupAwaitCountering
                        task.possibleCounters.contains(playerIndex)
                    }
                    options {
                        val task = game.stack.peek() as CoupAwaitCountering
                        task.blockableBy
                    }
                    perform {
                        val task = game.stack.peek() as CoupAwaitCountering
                        task.possibleCounters.remove(action.playerIndex)

                        val claim = CoupClaim(game.players[action.playerIndex], action.parameter, eliminations.remainingPlayers().toMutableList())
                        game.stack.add(CoupCounteract(claim))
                        game.stack.add(claim)
                    }
                }
            }
            rule("resolve action") {
                appliesWhen { game.stack.peek() is CoupAction }
                effect {
                    val action = game.stack.pop() as CoupAction
                    when (action.action) {
                        CoupActionType.INCOME -> action.player.coins += 1
                        CoupActionType.FOREIGN_AID -> action.player.coins += 2
                        CoupActionType.TAX -> action.player.coins += 3
                        CoupActionType.COUP -> game.stack.add(CoupLoseInfluenceTask(action.target!!))
                        CoupActionType.ASSASSINATE -> game.stack.add(CoupLoseInfluenceTask(action.target!!))
                        CoupActionType.STEAL -> {
                            val stolen = min(2, action.target!!.coins)
                            action.target.coins -= stolen
                            action.player.coins += stolen
                        }
                        CoupActionType.EXCHANGE -> {
                            game.stack.add(CoupPlayerExchangeCards(action.player, action.player.influence.size))
                            game.deck.random(replayable, 2, "cards") { it.name }.forEach {
                                it.moveTo(action.player.influence)
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported Action type: ${action.action}")
                    }
                    if (action.action != CoupActionType.EXCHANGE) {
                        game.currentPlayerIndex = game.currentPlayerIndex.next(game.playersCount)
                    }
                }
            }
            rule("respond to claims") {
                // TODO: Some kind of concensus approach. Allow people to change their minds? "Lock-in"?
                // Preferably auto-accept somehow
                // TODO: actions.consensus... { until, effect, exludePlayers... }
                action(approve) {
                    precondition {
                        game.stack.peek() !is CoupClaim ||
                            playerIndex != (game.stack.peek() as CoupClaim).player.playerIndex
                    }
                    perform {
                        if (game.stack.peek() !is CoupClaim) return@perform
                        if (game.stack.peek().let { it as CoupClaim }.accept(action.playerIndex)) {
                            game.stack.pop()
                        }
                        if (game.stack.peek() is CoupCounteract) {
                            // Counteract approved, clear everything and go to next player
                            game.stack.clear()
                            game.currentPlayerIndex = game.currentPlayerIndex.next(eliminations.playerCount)

                        }
                    }
                }
                action(challenge) {
                    precondition { game.stack.peek() is CoupClaim }
                    precondition { playerIndex != (game.stack.peek() as CoupClaim).player.playerIndex }
                    perform {
                        val claim = game.stack.pop() as CoupClaim
                        game.stack.add(CoupChallengedClaim(claim, playerIndex))
                    }
                }
            }
            // challenging returns coins, counteraction does not (Assassination)
        }
        testCase(players = 3) {
            state("start-0", listOf("CONTESSA", "ASSASSIN"))
            action(0, perform, CoupAction(game.players[0], CoupActionType.STEAL, game.players[1]))
            action(1, challenge, Unit)
            action(0, loseInfluence, CoupCharacter.CONTESSA)
            expectTrue(game.stack.isEmpty())
            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)
            expectEquals(listOf(CoupCharacter.ASSASSIN), game.players[0].influence.cards)
        }
        testCase(players = 3) {
            state("start-0", listOf("CONTESSA", "ASSASSIN"))
            state("start-1", listOf("CONTESSA", "ASSASSIN"))
            state("start-2", listOf("DUKE", "DUKE"))
            actionNotAllowed(1, counter, CoupCharacter.DUKE) // Not time to counter yet
            action(0, perform, CoupAction(game.players[0], CoupActionType.FOREIGN_AID, null))
            action(1, counter, CoupCharacter.DUKE)
            action(0, challenge, Unit)
            action(1, loseInfluence, CoupCharacter.CONTESSA)

            actionNotAllowed(1, counter, CoupCharacter.DUKE) // Player has already attempted this
            action(2, counter, CoupCharacter.DUKE)
            action(0, challenge, Unit)
            state("replacement", listOf("CAPTAIN"))
            action(2, reveal, Unit)
            action(0, loseInfluence, CoupCharacter.ASSASSIN) // This results in action successfully countered

            expectTrue(game.stack.isEmpty())
            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)
            expectEquals(2, game.players[1].coins)
            expectEquals(2, game.players[2].coins)
            expectEquals(listOf(CoupCharacter.CONTESSA), game.players[0].influence.cards)
            expectEquals(listOf(CoupCharacter.ASSASSIN), game.players[1].influence.cards)
            expectEquals(listOf(CoupCharacter.DUKE, CoupCharacter.CAPTAIN), game.players[2].influence.cards)
        }
        testCase(players = 3) {
            // Both challenge and counteract. Challenge fails, counteraction success
            state("start-0", listOf("CAPTAIN", "ASSASSIN"))
            state("start-1", listOf("AMBASSADOR", "DUKE"))
            state("start-2", listOf("CONTESSA", "CONTESSA"))
            expectTrue(game.stack.isEmpty())

            actionNotAllowed(0, approve, Unit)
            actionNotAllowed(1, challenge, Unit)
            expectNoActions(1)
            expectNoActions(2)
            action(0, perform, CoupAction(game.players[0], CoupActionType.STEAL, game.players[1]))
            expectTrue(game.stack.peek() is CoupClaim)
            action(1, approve, Unit)
            action(2, challenge, Unit)

            expectTrue(game.stack.peek() is CoupChallengedClaim)
            state("replacement", listOf("CONTESSA"))
            action(0, reveal, Unit)
            expectEquals(2, game.players[0].influence.cards.size)
            expectTrue(game.players[0].influence.cards.contains(CoupCharacter.CONTESSA))
            expectTrue(game.players[0].influence.cards.contains(CoupCharacter.ASSASSIN))

            expectTrue(game.stack.peek() is CoupLoseInfluenceTask)
            action(2, loseInfluence, CoupCharacter.CONTESSA)

            expectTrue(game.stack.peek() is CoupAwaitCountering)
            action(1, counter, CoupCharacter.AMBASSADOR)

            expectTrue(game.stack.peek() is CoupClaim)
            action(0, approve, Unit)
            action(2, approve, Unit)

            expectTrue(game.stack.isEmpty())
            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)
            expectEquals(2, game.players[1].coins)
            expectEquals(2, game.players[2].coins)
        }
    }

}
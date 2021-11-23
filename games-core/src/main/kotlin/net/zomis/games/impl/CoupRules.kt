package net.zomis.games.impl

import net.zomis.games.api.GamesApi
import net.zomis.games.common.next
import net.zomis.games.dsl.flow.GameFlowRule
import net.zomis.games.dsl.flow.GameFlowScope
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
        setup(CoupConfig::class) {
            defaultConfig { CoupConfig(0) }
            players(2..6)
            init {
                Coup(events, config, playerCount)
            }
            onStart {
                game.players.forEach { player ->
                    val influence = game.deck.random(replayable, 2, "start-" + player.playerIndex) { it.name }
                    influence.forEach { it.moveTo(player.influence) }
                }
                game.players.forEach { it.coins = 2 }
            }
        }
        fun GameFlowRule<Coup>.coupViews() {
            view("currentPlayer") { game.currentPlayerIndex }
            view("deck") { game.deck.size }
            view("players") {
                game.players.map { player ->
                    if (player.playerIndex != this.viewer) {
                        return@map mapOf(
                            "alive" to player.isAlive(),
                            "actionable" to actionsChosen().nextSteps(CoupPlayer::class).any { it.playerIndex == player.playerIndex },
                            "influenceCount" to player.influence.size,
                            "coins" to player.coins,
                            "previousInfluence" to player.previousInfluence.cards.map { it.name }
                        )
                    }
                    mapOf(
                        "alive" to player.isAlive(),
                        "influence" to player.influence.cards.map { it.name },
                        "coins" to player.coins,
                        "previousInfluence" to player.previousInfluence.cards.map { it.name }
                    )
                }
            }
            view("stack") {
                game.stack.asList().map { task ->
                    // This is shown in an ActionLog-like format
                    fun playerPart(player: CoupPlayer?): Map<String, Any>? {
                        return if (player != null) mapOf("type" to "player", "playerIndex" to player.playerIndex) else null
                    }

                    fun textPart(text: String): Map<String, Any> = mapOf("type" to "text", "text" to text)
                    fun characterPart(charactrer: CoupCharacter): Map<String, Any> = mapOf("type" to "text", "text" to charactrer.name)

                    val parts = when (task) {
                        is CoupLoseInfluenceTask -> listOf(
                            playerPart(task.player),
                            textPart("has to lose influence")
                        )
                        is CoupAction -> listOf(
                            playerPart(task.player),
                            textPart("wants to " + task.action.name),
                            playerPart(task.target)
                        ).filterNotNull()
                        is CoupAwaitCountering -> listOf(
                            textPart("can be countered")
                        )
                        is CoupChallengedClaim -> listOf(
                            playerPart(game.players[task.challengedBy]),
                            textPart("challenged claim"),
                            characterPart(task.claim.character),
                            textPart("by"),
                            playerPart(task.claim.player)
                        )
                        is CoupClaim -> listOf(
                            playerPart(task.player),
                            textPart("claims"),
                            characterPart(task.character)
                        )
                        is CoupCounteract -> listOf(
                            playerPart(task.claim.player),
                            textPart("wants to counteract")
                        )
                        is CoupPlayerExchangeCards -> listOf(
                            playerPart(task.player),
                            textPart("exchanges cards with the deck")
                        )
                        else -> throw UnsupportedOperationException("Task not yet supported in view: $task")
                    }
                    mapOf("parts" to parts)
                }
            }
            view("actions") {
                if (viewer == null) return@view emptyMap<String, Any>()
                CoupActionType.values().associate { action: CoupActionType ->
                    action.name to mapOf(
                        "name" to action.name,
                        "description" to action.description,
                        "claim" to action.claim,
                        "blockable" to action.blockableBy,
                        "allowed" to action(perform).choose(action).anyAvailable()
                    )
                }
            }
            view("buttons") {
                if (viewer == null) return@view emptyMap<String, Any>()
                mapOf(
                    "approve" to action(approve).anyAvailable(),
                    "counter" to action(counter).options(),
                    "challenge" to action(challenge).anyAvailable(),
                    // CoupCharacters:
                    "reveal" to action(reveal).anyAvailable(),
                    "ambassadorPutBack" to action(ambassadorPutBack).options().map { it.name },
                    "loseInfluence" to action(loseInfluence).options().map { it.name }
                )
            }
        }
        gameFlow {
            loop {
                step("game") {
                    // TODO: Rewrite Coup to gameFlow
                    println("Test")
                }
            }
        }
        gameFlowRules {
            beforeReturnRule("views") {
                this.coupViews()
            }
            rules.players.lastPlayerStanding()
            rules.players.losing { game.players[it].influence.size == 0 }
            rules.action(perform).filtered { action.parameter.action == CoupActionType.COUP }
                .cost({7}) { game.players[playerIndex]::coins }
            rules.action(perform).filtered { action.parameter.action == CoupActionType.ASSASSINATE }
                .cost({3}) { game.players[playerIndex]::coins }
            rule("cancel lose influence") {
                appliesWhen {
                    val peek = game.stack.peek()
                    if (peek !is CoupLoseInfluence) return@appliesWhen false
                    val task = peek as CoupLoseInfluence
                    task.player.influence.size == 0
                }
                effect {
                    game.stack.pop()
                }
            }
            rules.players.skipEliminated { game::currentPlayerIndex }
            rule("await countering: no more counters") {
                appliesWhen {
                    val stackTop = game.stack.peek()
                    stackTop is CoupAwaitCountering && stackTop.possibleCounters.isEmpty()
                }
                effect { game.stack.pop() }
            }
            rule("Get 1 coin (from the challenged player?) when winning a challenge") {
                appliesWhen { true } // game.config.gainMoneyOnSuccessfulChallenge > 0 }
                onEvent { game.challengeEvents }.perform {
                    println("XXXXXXXXXX Challenge Resolved ${this.event}")
                    if (!event.trueClaim) {
                        game.players[event.challengedClaim.challengedBy].coins += game.config.gainMoneyOnSuccessfulChallenge
                        if (game.config.gainMoneyOnSuccessfulChallenge > 0) {
                            // TODO: Add action log "$player got $x coins for a successful challenge
                        }
                    }
                }
            }
            afterActionRule("lose influence") {
                appliesWhen { game.stack.peek() is CoupLoseInfluence }
                action(loseInfluence) {
                    precondition {
                        if (game.stack.peek() !is CoupLoseInfluence) return@precondition false
                        playerIndex == (game.stack.peek() as CoupLoseInfluence).player.playerIndex
                    }
                    requires {
                        val task = game.stack.peek()
                        if (task is CoupChallengedClaim) task.claim.character != action.parameter else true
                    }
                    options { (game.stack.peek() as CoupLoseInfluence).player.influence.cards.toSet() }
                    perform {
                        log { "$player lost $action" }
                        val player = game.players[action.playerIndex]

                        val topTask = game.stack.peek() as CoupLoseInfluence
                        val actionTask = game.stack.asList().filterIsInstance<CoupAction>().firstOrNull()
                        if (topTask is CoupChallengedClaim) {
                            // This will fire all rules
                            println("FIRING COUP CHALLENGE RESOLVED with stack ${game.stack.asList()}")
                            game.challengeEvents.fire(CoupChallengeResolved(topTask, false))
                            println("FIRED COUP CHALLENGE RESOLVED with stack ${game.stack.asList()}")
                        }
                        game.stack.pop() // Pop the LoseInfluenceTask

                        player.influence.card(action.parameter).moveTo(player.previousInfluence)

                        if (topTask.player == actionTask?.player) {
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
            afterActionRule("reveal card") {
                action(reveal) {
                    precondition { game.stack.peek() is CoupChallengedClaim }
                    precondition { playerIndex == (game.stack.peek() as CoupChallengedClaim).player.playerIndex }
                    precondition {
                        val challengedClaim = game.stack.peek() as CoupChallengedClaim
                        challengedClaim.claim.canReveal()
                    }
                    perform {
                        // Put back card, draw a new one
                        val challengedClaim = game.stack.pop() as CoupChallengedClaim
                        game.challengeEvents.fire(CoupChallengeResolved(challengedClaim, true))

                        log { "$player reveals ${challengedClaim.claim.character} and draws a new card" }
                        challengedClaim.claim.player.influence.card(challengedClaim.claim.character).moveTo(game.deck)
                        game.deck.random(replayable, 1, "replacement") { it.name }.forEach {
                            it.moveTo(challengedClaim.claim.player.influence)
                        }

                        game.stack.add(CoupLoseInfluenceTask(game.players[challengedClaim.challengedBy]))
                    }
                }
            }
            afterActionRule("exchange cards") {
                appliesWhen { game.stack.peek() is CoupPlayerExchangeCards }
                action(ambassadorPutBack) {
                    precondition { playerIndex == (game.stack.peek() as CoupPlayerExchangeCards).player.playerIndex }
                    options { game.players[playerIndex].influence.cards.toSet() }
                    perform {
                        val exchangeTask = game.stack.peek() as CoupPlayerExchangeCards
                        game.players[playerIndex].influence.card(action.parameter).moveTo(game.deck)
                        logSecret(playerIndex) { "$player puts back $action" }.publicLog { "$player puts back a card" }
                        if (game.players[playerIndex].influence.size == exchangeTask.startSize) {
                            game.stack.pop()
                            game.currentPlayerIndex = game.currentPlayerIndex.next(eliminations.playerCount)
                        }
                    }
                }
            }
            afterActionRule("coup action") {
                // actions: List options -> Requires -> Perform
                // Options: There should only be one total of options and choose.
                // Options: Loop through all rules, check for action statement, 1) check preconditions 2) check options/choose
                // Options - Choose: Resolve and return while checking
                // View - Available Options: view("thing") { mapOf(...) + actionable(player.hand.card(it)) } <-- object equals check
                // Actions - Requires: Loop through all rules, check for action statement, check apply-for-action / requires
                // Actions - Perform: Loop through all rules, check for action statement, check apply-for-action / run perform
                rule("must perform coup") {
                    appliesWhen { game.currentPlayer.coins >= 10 }
                    action(perform) {
                        requires { action.parameter.action == CoupActionType.COUP }
                    }
                }
            }
            afterActionRule("perform action") {
                action(perform) {
                    precondition { game.stack.isEmpty() }
                    precondition { game.currentPlayerIndex == playerIndex }
                    perform {
                        if (action.parameter.target != null) {
                            log { "$player wants to perform ${action.action} on ${player(action.target!!.playerIndex)}" }
                        } else {
                            log { "$player wants to perform ${action.action}" }
                        }
                        game.stack.add(action.parameter)
                        if (action.parameter.action.blockableBy.isNotEmpty()) {
                            val possibleCounters = eliminations.remainingPlayers().toMutableList()
                            possibleCounters.remove(action.playerIndex)
                            if (action.parameter.target != null) {
                                possibleCounters.retainAll(listOf(action.parameter.target!!.playerIndex))
                            }
                            game.stack.add(CoupAwaitCountering(action.parameter.target, action.parameter.action.blockableBy, possibleCounters))
                            log { "Action can be blocked by ${action.action.blockableBy.joinToString(" / ")}" }
                        }
                        if (action.parameter.action.claim != null) {
                            game.stack.add(CoupClaim(action.parameter.player, action.parameter.action.claim!!,
                                eliminations.remainingPlayers().toMutableList()
                            ))
                            log { "$player claims ${action.action.claim}" }
                        }
                    }
                }
            }
            rule("eliminated players can't counteract") {
                appliesWhen {
                    if (game.stack.peek() !is CoupAwaitCountering) return@appliesWhen false
                    val task = game.stack.peek() as CoupAwaitCountering
                    task.possibleCounters.map { game.players[it] }.all { !it.isAlive() }
                }
                effect {
                    game.stack.pop()
                }
            }
            afterActionRule("approve needs one applicable rule active") {
                action(approve) {
                    precondition {
                        val task = game.stack.peek()
                        task is CoupAwaitCountering || task is CoupClaim
                    }
                }
            }
            afterActionRule("counteract possibility") {
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
                        log { "$player wants to counter by claiming $action" }
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
                    }
                    if (action.action != CoupActionType.EXCHANGE) {
                        game.currentPlayerIndex = game.currentPlayerIndex.next(game.playersCount)
                    }
                    // TODO: Allow logs and log that action is performed
                }
            }
            afterActionRule("choose actions") {
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
            afterActionRule("respond to claims") {
                // TODO: Some kind of concensus approach. Allow people to change their minds? "Lock-in"?
                // Preferably auto-accept somehow
                // TODO: actions.consensus... { until, effect, exludePlayers... }
                action(approve) {
                    precondition {
                        game.stack.peek() !is CoupClaim ||
                            (game.stack.peek() as CoupClaim).awaitingPlayers.contains(playerIndex)
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
                    precondition { (game.stack.peek() as CoupClaim).awaitingPlayers.contains(playerIndex) }
                    perform {
                        val claim = game.stack.pop() as CoupClaim
                        game.stack.add(CoupChallengedClaim(claim, playerIndex))
                        log { "$player challenges the claim ${claim.character} by ${player(claim.player.playerIndex)}" }
                    }
                }
            }
            // challenging returns coins, counteraction does not (Assassination)
        }
        testCase(players = 3) {
            // Simple take income action
            initialize()
            expectEquals(2, game.players[0].coins)
            action(0, perform, CoupAction(game.players[0], CoupActionType.INCOME))
            expectEquals(3, game.players[0].coins)
            expectEquals(1, game.currentPlayerIndex)
            expectTrue(game.stack.isEmpty())
        }
        testCase(players = 3) {
            // Take income, then next player uses assassin
            initialize()
            game.players[1].coins = 3
            expectEquals(2, game.players[0].coins)
            action(0, perform, CoupAction(game.players[0], CoupActionType.INCOME))
            expectEquals(3, game.players[0].coins)
            expectEquals(3, game.players[1].coins)

            expectEquals(1, game.currentPlayerIndex)
            expectTrue(game.stack.isEmpty())
            action(1, perform, CoupAction(game.players[1], CoupActionType.ASSASSINATE, game.players[0]))
            expectEquals(0, game.players[1].coins)
        }
        testCase(players = 6) {
            // Eliminate multiple players, make sure that currentPlayer skips multiple turns
            initialize()
            for (i in 2..5) {
                game.players[i].influence.moveAllTo(game.players[i].previousInfluence)
            }
            action(0, perform, CoupAction(game.players[0], CoupActionType.INCOME))
            action(1, perform, CoupAction(game.players[1], CoupActionType.INCOME))
            expectEquals(0, game.currentPlayerIndex)
            expectTrue(game.stack.isEmpty())
        }
        testCase(players = 3) {
            // Assassinate, successful block with contessa
            state("start-0", listOf("CONTESSA", "ASSASSIN"))
            state("start-1", listOf("CONTESSA", "ASSASSIN"))
            initialize()
            game.players[0].coins = 3
            action(0, perform, CoupAction(game.players[0], CoupActionType.ASSASSINATE, game.players[1]))
            action(1, approve, Unit)
            action(2, approve, Unit)
            action(1, counter, CoupCharacter.CONTESSA)
            action(0, approve, Unit)
            action(2, approve, Unit)
            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[1].influence.size)
            expectEquals(0, game.players[0].coins)
            expectTrue(game.stack.isEmpty())
        }
        testCase(players = 3) {
            // Assassinate, failed challenge, no counteract attempt
            state("start-0", listOf("CONTESSA", "ASSASSIN"))
            state("start-1", listOf("CONTESSA", "ASSASSIN"))
            initialize()
            game.players[0].coins = 3
            action(0, perform, CoupAction(game.players[0], CoupActionType.ASSASSINATE, game.players[1]))
            action(1, challenge, Unit)
            action(0, reveal, Unit)
            action(1, loseInfluence, CoupCharacter.ASSASSIN)
            action(1, approve, Unit)
            expectNoActions(2)
            action(1, loseInfluence, CoupCharacter.CONTESSA)
            expectEquals(2, game.currentPlayerIndex)
            expectEquals(0, game.players[1].influence.size)
            expectEquals(0, game.players[0].coins)
            expectTrue(game.stack.isEmpty())
        }
        testCase(players = 3) {
            // Assassinate, failed block with contessa
            state("start-0", listOf("CONTESSA", "ASSASSIN"))
            state("start-1", listOf("DUKE", "AMBASSADOR"))
            initialize()
            game.players[0].coins = 3
            action(0, perform, CoupAction(game.players[0], CoupActionType.ASSASSINATE, game.players[1]))
            action(1, approve, Unit)
            action(2, approve, Unit)
            action(1, counter, CoupCharacter.CONTESSA)
            action(0, challenge, Unit)
            action(1, loseInfluence, CoupCharacter.AMBASSADOR)
            action(1, loseInfluence, CoupCharacter.DUKE)
            expectEquals(2, game.currentPlayerIndex)
            expectEquals(0, game.players[1].influence.size)
            expectEquals(0, game.players[0].coins)
            expectTrue(game.stack.isEmpty())
        }
        testCase(players = 3) {
            // Assassinate, successful challenge
            state("start-0", listOf("CONTESSA", "DUKE"))
            initialize()
            game.players[0].coins = 3
            action(0, perform, CoupAction(game.players[0], CoupActionType.ASSASSINATE, game.players[1]))
            action(1, challenge, Unit)
            action(0, loseInfluence, CoupCharacter.CONTESSA)
            expectEquals(1, game.currentPlayerIndex)
            expectEquals(1, game.players[0].influence.size)
            expectEquals(2, game.players[1].influence.size)
            expectEquals(3, game.players[0].coins)
            expectTrue(game.stack.isEmpty())
        }
        testCase(players = 3) {
            config(CoupConfig(gainMoneyOnSuccessfulChallenge = 1))
            state("start-0", listOf("CONTESSA", "ASSASSIN"))
            initialize()
            action(0, perform, CoupAction(game.players[0], CoupActionType.TAX))
            action(1, challenge, Unit)
            action(0, loseInfluence, CoupCharacter.CONTESSA)

            expectTrue(game.stack.isEmpty())
            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)
            expectEquals(3, game.players[1].coins)
            expectEquals(listOf(CoupCharacter.ASSASSIN), game.players[0].influence.cards)
        }
        testCase(players = 3) {
            state("start-0", listOf("CONTESSA", "ASSASSIN"))
            initialize()
            action(0, perform, CoupAction(game.players[0], CoupActionType.STEAL, game.players[1]))
            action(1, challenge, Unit)
            action(0, loseInfluence, CoupCharacter.CONTESSA)
            expectTrue(game.stack.isEmpty())
            expectEquals(1, game.currentPlayerIndex)
            expectEquals(2, game.players[0].coins)
            expectEquals(listOf(CoupCharacter.ASSASSIN), game.players[0].influence.cards)
        }
        testCase(players = 3) {
            state("start-0", listOf("CAPTAIN", "ASSASSIN"))
            state("start-1", listOf("CONTESSA", "ASSASSIN"))
            state("start-2", listOf("DUKE", "DUKE"))
            initialize()
            game.players[2].influence.cards.removeAt(0)
            expectEquals(2, game.players[0].coins)
            action(0, perform, CoupAction(game.players[0], CoupActionType.STEAL, game.players[2]))
            // Steal from a player having 1 influence left, that player then challenges action, loses influence and can't choose to counteract or approve
            action(2, challenge, Unit)
            state("replacement", listOf("AMBASSADOR"))
            action(0, reveal, Unit)
            action(2, loseInfluence, CoupCharacter.DUKE)
            expectTrue(game.players[2].influence.size == 0)
            check(game.stack.isEmpty()) { game.stack.asList().toString() }
            expectTrue(game.stack.isEmpty())
            expectEquals(4, game.players[0].coins)
            expectEquals(1, game.currentPlayerIndex)
        }
        testCase(players = 6) {
            initialize()
            game.players[2].influence.cards.removeAt(0)
            game.players[1].influence.cards.clear()
            game.players[0].coins = 200
            action(0, perform, CoupAction(game.players[0], CoupActionType.COUP, game.players[2]))
            action(2, loseInfluence, game.players[2].influence.cards.first())
            expectTrue(game.stack.isEmpty())
            expectEquals(3, game.currentPlayerIndex)
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
            initialize()
            expectTrue(game.stack.isEmpty())

            actionNotAllowed(0, approve, Unit)
            actionNotAllowed(1, challenge, Unit)
            expectNoActions(1)
            expectNoActions(2)
            action(0, perform, CoupAction(game.players[0], CoupActionType.STEAL, game.players[1]))
            expectTrue(game.stack.peek() is CoupClaim)
            action(1, approve, Unit)
            actionNotAllowed(1, approve, Unit)
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

    private suspend fun chooseAction(flow: GameFlowScope<Coup>, player: CoupPlayer) {
        flow.step("") {
            this.yieldAction(perform) {

            }
        }
        TODO("Not yet implemented")
    }

}

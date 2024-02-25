package net.zomis.games.impl.cards

import net.zomis.bestOf
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.cards.CardZoneI
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.next
import net.zomis.games.common.shifted
import net.zomis.games.common.toSingleList
import net.zomis.games.components.SemiKnownCardZone
import net.zomis.games.components.resources.GameResource
import net.zomis.games.components.resources.ResourceMap
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.Viewable
import net.zomis.games.dsl.flow.GameFlowStepScope
import net.zomis.games.dsl.flow.SmartActionDsl
import net.zomis.games.impl.GameStack
import net.zomis.games.rules.NoState
import net.zomis.games.rules.Rule
import net.zomis.games.rules.RuleSpec
import kotlin.math.ceil

object Grizzled {

    class StartMission
    data class PlayCard(val playerIndex: Int, val fromZone: CardZoneI<GrizzledCard>, val card: GrizzledCard, val trapsEnabled: Boolean)
    data class Support(val supportedPlayers: MutableMap<Player, Player>)
    class Supported(val restoreCharm: Boolean, val removeHardKnocks: List<GrizzledCard>) {
        fun toStateString() = "$restoreCharm/${removeHardKnocks.map { it.toStateString() }}"
    }
    class MoraleDrop(var count: Int)
    class ChangeLeader(var nextPlayer: Int)

    data class WithdrawAction(val support: SupportTile?) {
        fun toStateString() = support?.name ?: "null"
    }
    val factory = GamesApi.gameCreator(Model::class)
    val trapsConfig = factory.config("traps") { true }
    val playAction = factory.action("play", GrizzledCard::class).serializer { it.toStateString() }
    val withdraw = factory.action("withdraw", WithdrawAction::class).serializer { it.toStateString() }
    val giveSpeech = factory.action("speech", Threat::class)
    val discard = factory.action("discard", GrizzledCard::class).serializer { it.toStateString() }
    val useLuckyCharm = factory.action("charm", GrizzledCard::class).serializer { it.toStateString() }
    val useSupport = factory.action("useSupport", Supported::class).serializer { it.toStateString() }
    val chooseCardCount = factory.action("cards", Int::class)
    val merryChristmasAction = factory.action("christmas", GrizzledCard::class).serializer { it.toStateString() }
    val absentMindedAction = factory.action("absent-minded", SupportTile::class)

    enum class SupportTile(val offset: Int) {
        DoubleLeft(-2), Left(-1), Right(1), DoubleRight(2);

        companion object {
            private const val SINGLE_SUPPORT_TILES = 6
            private const val DOUBLE_SUPPORT_TILES = 2
            val onlySingles = (1..SINGLE_SUPPORT_TILES).flatMap { listOf(Left, Right) }
            val allTiles = onlySingles + (1..DOUBLE_SUPPORT_TILES).flatMap { listOf(DoubleLeft, DoubleRight) }
        }
    }

    val HardKnock = GameResource.withName("HardKnock")
    object HardKnocks {
        val hardKnock = HardKnock.toResourceMap(1)

        // A hard-knock card needs: Effect on played. Resource map. Event-listener. Action manipulation. Extra state.

        // Resource manipulation
        val phobias = Threat.values().filter { it.item }.map { GrizzledCard(hardKnock + it, "Phobia ${it.name}", it.name) }
        val traumas = Threat.values().filter { it.weather }.map { GrizzledCard(hardKnock + it, "Trauma ${it.name}", it.name) }
        val frenzied: GrizzledCard = GrizzledCard(hardKnock, "Frenzied", "At the beginning of the mission, you must draw 2 extra cards (from trial to hand)") {
            on(StartMission::class).perform {
                game.trials.top(replayable, "frenzied",2).forEach { it.moveTo(ruleHolder.hand) }
            }
        }
        val wounded = GrizzledCard(hardKnock * 2, "Wounded", "Counts as 2 hard knocks")
        val demoralized = GrizzledCard(hardKnock, "Demoralized", "When the morale drops, flip one extra card (minimum 4)") {
            on(MoraleDrop::class).mutate { event.count++ }
        }

        // Rule modifications
        val panicked = GrizzledCard(hardKnock, "Panicked", "During withdrawal, draw your support tile randomly") {
            // TODO: Instead remove the support tile choice from withdraw action, or force player to choose support tile == null
            action(withdraw) {
                perform {
                    val player = game.players[action.playerIndex]
                    val supportPlayed = action.parameter.support
                    if (supportPlayed != null) {
                        // TODO: Is this done before or after the regular action things? Hopefully after.
                        player.supportTiles.cards.add(supportPlayed) // TODO: Don't do this.
                    }
                    if (player.supportTiles.isEmpty()) return@perform

                    val randomChoice = player.supportTiles.random(replayable, 1, "panicked", SupportTile::toString).first().card
                    player.placedSupportTile = randomChoice
                }
            }
        }
        val selfish = GrizzledCard(hardKnock, "Selfish", "Your support tile is always redirected to yourself") {
            val owner = this.ruleHolder
            on(Support::class).mutate {
                event.supportedPlayers[owner] = owner
            }
        }
        val mute = GrizzledCard(hardKnock, "Mute", "You can no longer speak or communicate with other players in any way. You may not use a speech") {
            val owner = this.ruleHolder
            action(giveSpeech) {
                precondition { playerIndex != owner.playerIndex }
            }
        }
        val fragile = GrizzledCard(hardKnock, "Fragile", "Other players cannot withdraw as long as they have any cards in hand") {
            action(withdraw) {
                precondition { playerIndex == ruleHolder.playerIndex || game.players[playerIndex].hand.isEmpty() }
            }
        }
        val prideful = GrizzledCard(hardKnock, "Prideful", "You may withdraw only if your hand is empty, or if you are the last one still in the mission") {
            val owner = ruleHolder
            action(withdraw) {
                precondition { owner.playerIndex != playerIndex || owner.hand.isEmpty() || game.players.count { it.inMission } == 1 }
            }
        }
        val merryChristmas = GrizzledCard(ResourceMap.empty(), "Merry Christmas", "Discard a hard knock card from yourself or another player") {
            val owner = this.ruleHolder
            stateCheckBeforeAction {
                // This card goes directly to the discard pile
                owner.hardKnocks.card(owner.hardKnocks.cards.first { it.hardKnockEffects == ruleSpec }).moveTo(game.discarded)
            }
            game.stack.add(StackItem.MerryChristmas(ruleHolder.playerIndex) {
                action(merryChristmasAction) {
                    precondition { playerIndex == owner.playerIndex }
                    choose {
                        optionsWithIds({ game.players.flatMap { it.hardKnocks.cards }.map { it.id.toString() to it } }) {
                            parameter(it)
                        }
                    }
                    perform { game.stack.pop() }
                    perform {
                        val zone = game.players.map { it.hardKnocks }.single { it.cards.contains(action.parameter) }
                        zone.card(action.parameter).moveTo(game.discarded)
                    }
                }
            })
        }
        val hardheaded = GrizzledCard(hardKnock, "Hardheaded", "You cannot withdraw as long as you have 2 or more cards in hand") {
            action(withdraw) {
                precondition { ruleHolder.playerIndex != playerIndex || ruleHolder.hand.size < 2 }
            }
        }
        val absentMinded = GrizzledCard(hardKnock, "Absent-minded", "Before withdrawing, choose and remove one of your support tiles from the game") {
            var discardedTile: Boolean by this.state2<Boolean> { false }

            action(withdraw) {
                precondition { playerIndex != ruleHolder.playerIndex || discardedTile }
                perform {
                    discardedTile = false
                }
            }
            action(absentMindedAction) {
                precondition { playerIndex == game.currentPlayerIndex && game.currentPlayer.inMission }
                precondition { playerIndex == ruleHolder.playerIndex && !discardedTile }
                options { ruleHolder.supportTiles.cards }
                perform {
                    discardedTile = true
                }
                perform {
                    ruleHolder.supportTiles.cards.remove(action.parameter)
                }
            }
        }
        val tyrannical = GrizzledCard(hardKnock, "Tyrannical", "Take the mission leader role and keep it, preventing the distribution of speeches") {
            onActivate {
                game.missionLeaderIndex = ruleHolder.playerIndex
            }
            on(ChangeLeader::class).mutate { event.nextPlayer = ruleHolder.playerIndex }
        }
        val clumsy = GrizzledCard(hardKnock, "Clumsy", "After your withdrawal, you must draw a trial card and play it") {
            action(withdraw) {
                perform {
                    if (action.playerIndex != ruleHolder.playerIndex) return@perform
                    if (game.trials.isEmpty()) return@perform
                    val card = game.trials.top(replayable, "clumsy", 1).first()
                    game.playCardEvent.invoke(PlayCard(playerIndex, game.trials, card.card, trapsEnabled = false))
                }
            }
        }
        val fearful = GrizzledCard(hardKnock, "Fearful", "On your turn, you must withdraw if 2 identical threats are active") {
            activeWhile {
                game.activeThreats.any { it.value >= 2 }
            }
            allActionsPrecondition {
                if (playerIndex != ruleHolder.playerIndex) return@allActionsPrecondition true
                actionType == withdraw.name
            }
        }
        val potentiallyConflictingRules = listOf(
            setOf(fearful, prideful),
            setOf(fearful, fragile),
            setOf(fearful, hardheaded),
        )

        val allCards = phobias + traumas + listOf(
            frenzied, wounded, demoralized, panicked,
            selfish, mute, fragile, prideful, merryChristmas, hardheaded,
            absentMinded, tyrannical, clumsy, fearful
        )
    }
    val Trap = GameResource.withName("Trap")
    val threatCards = listOf(
        Threat.Shell + Threat.Night,
        Threat.Shell + Threat.Rain + Trap,
        Threat.Whistle + Threat.Rain,
        Threat.Shell + Threat.Snow,
        Threat.Mask + Threat.Rain,
        Threat.Whistle + Threat.Rain + Trap,
        Threat.Whistle + Threat.Shell,
        Threat.Rain + Threat.Night,
        Threat.Whistle + Threat.Snow + Trap,
        Threat.Mask + Threat.Snow + Trap,
        Threat.Mask + Threat.Night,
        Threat.Mask + Threat.Whistle + Threat.Shell,
        Threat.Whistle + Threat.Snow,
        Threat.Snow + Threat.Rain + Threat.Night,
        Threat.Whistle + Threat.Night,
        Threat.Mask + Threat.Night  + Trap,
        Threat.Snow + Threat.Rain + Threat.Night + Threat.Whistle + Threat.Mask + Threat.Shell,
        Threat.Mask + Threat.Shell,
        Threat.Mask + Threat.Rain,
        Threat.Shell + Threat.Rain,
        Threat.Night + Threat.Snow,
        Threat.Shell + Threat.Night + Trap,
        Threat.Whistle + Threat.Rain,
        Threat.Mask + Threat.Night,
        Threat.Shell + Threat.Night,
        Threat.Whistle + Threat.Snow,
        Threat.Shell + Threat.Night,
        Threat.Mask + Threat.Snow,
        Threat.Shell + Threat.Snow + Trap,
        Threat.Mask + Threat.Rain,
        Threat.Shell + Threat.Snow,
        Threat.Mask + Threat.Rain + Trap,
        Threat.Mask + Threat.Whistle,
        Threat.Mask + Threat.Snow,
        Threat.Whistle + Threat.Night + Trap,
        Threat.Whistle + Threat.Snow,
        Threat.Whistle + Threat.Night,
        Threat.Shell + Threat.Rain,
        Threat.Snow + Threat.Rain,
    ).map(GrizzledCard::fromThreats)
    class GrizzledCard(
        val threats: ResourceMap,
        val name: String?,
        val description: String?,
        var id: Int = 0,
        val hardKnockEffects: RuleSpec<Model, Player>? = null
    ): GameSerializable, Viewable {
        override fun serialize(): Any = toStateString()

        fun toStateString(): String = name ?: threats.toStateString()
        override fun toString(): String = "$id:" + toStateString()

        override fun toView(viewer: PlayerIndex): Any = mapOf(
            "id" to id,
            "threats" to threats.toView(),
            "name" to name,
            "description" to (description ?: threats.toStateString())
        )

        companion object {
            fun fromThreats(threats: ResourceMap): GrizzledCard = GrizzledCard(threats = threats, name = null, description = null)
            fun fromThreat(threat: Threat): GrizzledCard = fromThreats(threat.toResourceMap())
        }

        fun isHardKnock(): Boolean = threats.has(HardKnock, 1) || threats.isEmpty()
    }
    class Player(ctx: Context, val playerIndex: Int): Entity(ctx) {
        var withdrawn by component { false }
        val inMission: Boolean get() = !withdrawn
        var character by component<Character?> { null }
        var charmAvailable by component { true }
        val hardKnocks by cards<GrizzledCard>().publicView { z -> z.cards.map { it.toView(playerIndex) } }
        val hand by cards<GrizzledCard>().privateView(playerIndex) { z -> z.cards.map { it.toView(playerIndex) } }.publicView { it.size }
        val supportTiles by cards<SupportTile>().privateView(playerIndex) { it.cards }.publicView { it.size }
        var placedSupportTile by component<SupportTile?> { null }.privateView(playerIndex) { it ?: "" }
        var speechesAvailable by component { 0 }
        val idGenerator = sharedIdGenerator()

        fun enterMission() {
            this.withdrawn = false
            this.placedSupportTile = null
        }
        val useSpeech: SmartActionDsl<Model, Threat> = {
            choice("speech", optional = false) { Threat.values().toList() }
            standard.apply {
                precondition { playerIndex == this@Player.playerIndex }
                precondition {
                    speechesAvailable > 0
                }
                perform {
                    speechesAvailable--
                }
                perform {
                    val discardableThreat = action.parameter
                    val speechGiver = action.playerIndex
                    val discardablePlayers = game.players
                        .filter { it.playerIndex != speechGiver }
                        .filter { it.inMission }
                        .filter { player -> player.hand.cards.any { it.threats.has(discardableThreat, 1) } }
                        .toMutableList()
                    if (discardablePlayers.isNotEmpty()) {
                        meta.injectStep("discard from speech $discardableThreat", discardStep(discardableThreat, discardablePlayers))
                    }
                }
            }
        }
        val useLuckyCharmDsl: SmartActionDsl<Model, GrizzledCard> = {
            choiceKeys("card", optional = false) {
                game.playedCards.cards.filter { it.threats.has(game.players[playerIndex].character!!.luckyCharm, 1) }
                    .map { it.id.toString() to it }
            }
            standard.apply {
                // TODO: Boolean cost (implement both that it requires true and sets false, and inverted)
                precondition { playerIndex == this@Player.playerIndex }
                precondition { charmAvailable }
                perform { charmAvailable = false }

                requires { action.parameter.threats.has(character!!.luckyCharm, 1) }
                perform { game.playedCards.card(action.parameter).moveTo(game.discarded) }
            }
        }
        val withdrawDsl: SmartActionDsl<Model, WithdrawAction> = {
            choice("tile", optional = false) {
                val supportTokens = this@Player.supportTiles.cards
                if (supportTokens.isEmpty()) listOf(WithdrawAction(null)) else supportTokens.map {
                    WithdrawAction(it)
                }
            }
            standard.apply {
                precondition { playerIndex == this@Player.playerIndex }
                perform { withdrawn = true }
                requires {
                    if (action.parameter.support == null) supportTiles.cards.isEmpty()
                    else supportTiles.cards.contains(action.parameter.support)
                }
                perform { placedSupportTile = action.parameter.support }
            }
        }
        val playCard: SmartActionDsl<Model, GrizzledCard> = {
            choiceKeys("card", optional = false) { hand.cards.map { it.id.toString() to it } }
            standard.apply {
                precondition { playerIndex == this@Player.playerIndex }
                perform {
                    game.playCardEvent.invoke(PlayCard(playerIndex, hand, action.parameter, trapsEnabled = true))
                }
                perform {
                    log { "$player plays ${action.toStateString()}" }
                }
            }
        }
        val chooseStartCards: SmartActionDsl<Model, Int> = {
            choice("number of cards", false) { 1..ceil(game.trials.size.toDouble() / game.players.size).toInt() }
            standard.apply {
                precondition { game.missionLeader.playerIndex == playerIndex }
                requires { action.parameter >= 1 }
                requires { action.parameter >= 3 || game.round > 1 }
                perform {
                    val count = action.parameter * game.players.size
                    game.trials.deal(
                        replayable, "trialCards", count.coerceAtMost(game.trials.size),
                        game.players.shifted(game.missionLeaderIndex).map { it.hand }
                    ) { it.id = idGenerator(); it } // val idGenerator by idGenerator() -- use a global id counter
                }
            }
        }
    }
    enum class Threat(val weather: Boolean): GameResource {
        Rain(true),
        Night(true),
        Snow(true),
        Shell(false),
        Whistle(false),
        Mask(false),
        ;
        val item get() = !weather
    }
    data class Character(val name: String, val luckyCharm: Threat)
    val characters = listOf(
        Character("Charles Saulière", Threat.Rain),
        Character("Félix Moreau", Threat.Night),
        Character("Gustave Bideau", Threat.Snow),
        Character("Lazare Bonticeli", Threat.Whistle),
        Character("Gaston Fayard", Threat.Shell),
        Character("Anselme Perrin", Threat.Mask),
    )
    sealed interface StackItem {
        val ruleSpec: RuleSpec<Model, StackItem>
        class MerryChristmas(val giftingPlayerIndex: Int, override val ruleSpec: RuleSpec<Model, StackItem>) : StackItem
    }
    class Model(val startingTrials: Int, override val ctx: Context): Entity(ctx), ContextHolder {
        val stack = GameStack<StackItem>()
        val startMissionEvent = event<StartMission>()
        val resolveSupportEvent = event<Support>()
        val moraleDropEvent = event<MoraleDrop>()
        val changeLeaderEvent = event<ChangeLeader>()
        val playCardEvent = event<PlayCard>()

        val baseRule by rule<Model, Unit>(Unit) {
            generateId.invoke(this)
            playCard.invoke(this)

            val activeRules = mutableListOf<Rule<Model, Player>>()
            for (player in players.shifted(currentPlayerIndex + 1).filter { it.inMission }) {
                for (card in player.hardKnocks.cards) {
                    activeRules.add(subRule(rule = card.hardKnockEffects ?: {}, owner = player, stateOwner = NoState))
                }
            }
            resolveConflicts(game, activeRules.filter { it.isActive() })

            when (val peek = stack.peek()) {
                null -> {}
                is StackItem.MerryChristmas -> subRule(peek.ruleSpec, peek, NoState)
            }

            onNoActions { stack.popOrNull() }
        }

        val discarded: CardZone<GrizzledCard> by cards()
        val activeThreats: ResourceMap by dynamicValue {
            val cards = players.filter { it.inMission }.flatMap { it.hardKnocks.cards } + playedCards.cards
            cards.fold(ResourceMap.empty()) { acc, next -> acc + next.threats }
                .filter { it.resource != HardKnock && it.resource != Trap }
        }.publicView { it.toView() }

        var speeches by component { 0 }.setup {
            when (playerCount) {
                5 -> 3
                4 -> 4
                2, 3 -> 5
                else -> throw IllegalArgumentException("No support for $playerCount players")
            }
        }
        val playedCards by cards<GrizzledCard>().publicView { z -> z.cards.map { it.toView(null) } }
        var round by component { 1 }
        val morale by component { CardZone<GrizzledCard>() }.onSetup {
            it.cards.addAll(threatCards + HardKnocks.allCards)
        }.publicView { it.size }
        val actions by viewOnly<Model> {
            val cards = actions().nextSteps(GrizzledCard::class).associate { it.id to true }
            mapOf(
                "actions" to listOf(giveSpeech, useLuckyCharm, withdraw, playAction, chooseCardCount, merryChristmasAction, discard, useSupport, absentMindedAction).associate {
                    it.name to actionRaw(it).nextStepsAll()
                },
                "cards" to cards,
                "supportTile" to actions().nextSteps(SupportTile::class).map { it.name }
            )
        }
        val trials: SemiKnownCardZone<GrizzledCard> by component { SemiKnownCardZone(emptyList(), GrizzledCard::toStateString) }
            .onSetup {trialZone ->
                morale.random(replayable, startingTrials, "trials", GrizzledCard::toStateString)
                    .forEach { it.moveTo(trialZone) }
            }
            .publicView { it.size }
        val players by playerComponent { Player(ctx, it) }.onSetup { players ->
            // Setup characters
            val characters = replayable.randomFromList("characters", characters, playerCount) { it.name }
            players.forEach { it.character = characters[it.playerIndex] }

            // Setup support tiles
            val supportTiles = when (playerCount) {
                2 -> TODO("PlayerCount 2 not supported yet")
                3 -> SupportTile.onlySingles
                4, 5 -> SupportTile.allTiles
                else -> throw IllegalArgumentException("Unsupported playerCount: $playerCount")
            }.let { CardZone(it.toMutableList()) }
            players.forEach {
                supportTiles.card(SupportTile.Left).moveTo(it.supportTiles)
                supportTiles.card(SupportTile.Right).moveTo(it.supportTiles)
            }
            val extra = supportTiles.random(replayable, playerCount, "extra-support-tiles") { it.name }.toList()
            supportTiles.deal(extra.map { it.card }, players.map { it.supportTiles })
        }
        var missionLeaderIndex: Int by component { 0 }
        val missionLeader get() = players[missionLeaderIndex]
        var currentPlayerIndex: Int by component { missionLeaderIndex }
        val currentPlayer get() = players[currentPlayerIndex]

        fun missionFailed(): Boolean = activeThreats.entries().any { it.value >= 3 }
    }

    private fun resolveConflicts(game: Model, activeRules: List<Rule<Model, Player>>) {
        for (conflict in HardKnocks.potentiallyConflictingRules) {
            if (!conflict.all { card -> activeRules.any { it.ruleSpec == card.hardKnockEffects } }) continue

            val conflictingSpecs = conflict.map { it.hardKnockEffects }

            val players = conflict.map { card -> game.players.single { player -> player.hardKnocks.cards.contains(card) } }.distinct()
            if (!players.contains(game.currentPlayer)) {
                continue
            } else if (players.size == 1) {
                // On the same player: Oldest cards win.
                val cardToDisable = game.currentPlayer.hardKnocks.cards.last { it.hardKnockEffects in conflictingSpecs }
                activeRules.single { it.ruleSpec == cardToDisable.hardKnockEffects }.disable()
            } else {
                // On different players: On player currently playing wins.
                val cardToKeep = game.currentPlayer.hardKnocks.cards.single { it.hardKnockEffects in conflictingSpecs }
                val cardToDisable = conflict.minus(cardToKeep).single()
                activeRules.single { it.ruleSpec == cardToDisable.hardKnockEffects }.disable()
            }
        }
    }

    private val generateId: RuleSpec<Model, Unit> = {
        on(PlayCard::class).perform {
            val player = game.players[event.playerIndex]
            if (event.card.id == 0) event.card.id = player.idGenerator.invoke()
        }
    }
    private val playCard: RuleSpec<Model, Unit> = {
        on(PlayCard::class).perform {
            val player = game.players[event.playerIndex]
            if (event.card.isHardKnock()) {
                event.fromZone.card(event.card).moveTo(player.hardKnocks)
            } else {
                event.fromZone.card(event.card).moveTo(game.playedCards)
                if (config(trapsConfig) && event.trapsEnabled && event.card.threats.has(Trap, 1) && game.trials.isNotEmpty()) {
                    game.playCardEvent.invoke(
                        PlayCard(event.playerIndex, game.trials, game.trials.top(replayable, "trap", 1).first().card, trapsEnabled = false)
                    )
                }
            }
        }
    }

    val game = GamesApi.gameContext("Grizzled", Model::class) {
        addConfig(trapsConfig)
        val startingTrialsConfig = this.config("startingTrials") { 25 }
        players(3..5)
        init { Model(config(startingTrialsConfig), ctx) }
        baseRule(Model::baseRule)
        gameFlow {
            loop {
                step("setup mission") {
                    game.currentPlayerIndex = game.missionLeaderIndex
                    game.players.forEach { it.enterMission() }
                    actionHandler(chooseCardCount, game.missionLeader.chooseStartCards)
                }
                step("start") {
                    game.startMissionEvent.invoke(StartMission())
                }
                check(game.players.sumOf { it.supportTiles.size } <= game.players.size * 3) {
                    "Too many support tiles in the game!"
                }

                while (game.players.any { it.inMission }) {
                    val action = step("play") {
                        actionHandler(playAction, game.currentPlayer.playCard)
                        actionHandler(giveSpeech, game.currentPlayer.useSpeech)
                        actionHandler(useLuckyCharm, game.currentPlayer.useLuckyCharmDsl)
                        actionHandler(withdraw, game.currentPlayer.withdrawDsl)
                    }
                    when (action.action?.actionType) {
                        playAction.name, giveSpeech.name, useLuckyCharm.name, withdraw.name -> {
                            val nextPlayer = game.currentPlayerIndex.next(game.players.size) { game.players[it].inMission } ?: break
                            game.currentPlayerIndex = nextPlayer
                        }
                    }
                    if (game.missionFailed()) break
                }
                if (game.missionFailed()) {
                    game.playedCards.moveAllTo(game.trials)
                    game.trials.shuffle()
                    step("Resolve support for failed mission", supportDsl(successfulMission = false))
                } else {
                    game.playedCards.moveAllTo(game.discarded)
                    step("Resolve support for successful mission", supportDsl(successfulMission = true))
                }

                // TODO: Implement win-condition as a rule instead?
                val totalCardsInHands = game.players.sumOf { it.hand.size }
                if (totalCardsInHands == 0 && game.trials.isEmpty()) {
                    eliminations.eliminateRemaining(WinResult.WIN)
                    return@loop
                }

                MoraleDrop(totalCardsInHands.coerceAtLeast(3)).also { drop ->
                    game.moraleDropEvent.invoke(drop)
                    val actualDrop = drop.count.coerceAtMost(game.morale.size)
                    game.morale.random(replayable, actualDrop, "morale-drop", GrizzledCard::toStateString).forEach {
                        it.moveTo(game.trials)
                    }
                }

                // TODO: Implement loss-condition as a rule instead?
                if (game.morale.isEmpty()) {
                    eliminations.eliminateRemaining(WinResult.LOSS)
                    return@loop
                }

                val oldMissionLeader = game.missionLeaderIndex
                ChangeLeader(game.missionLeaderIndex.next(game.players.size)).also {
                    game.changeLeaderEvent.invoke(it)
                    game.missionLeaderIndex = it.nextPlayer
                }

                if (game.missionLeaderIndex != oldMissionLeader && game.speeches > 0) {
                    game.speeches--
                    game.players[oldMissionLeader].speechesAvailable++
                }

                game.round++
            }
        }
        testCase(players = 3, name = "Conflict resolution") {
            state("trials", listOf(
                "Snow/1,Whistle/1", "Snow/1,Trap/1,Whistle/1", "Phobia Whistle", "Mask/1,Shell/1", "Night/1,Whistle/1", "Trauma Night", "Demoralized", "Panicked", "Rain/1,Whistle/1", "Selfish", "Fragile", "Shell/1,Whistle/1", "Rain/1,Shell/1", "Mask/1,Snow/1,Trap/1", "Rain/1,Whistle/1", "Mask/1,Shell/1,Whistle/1", "Trauma Snow", "Absent-minded", "Night/1,Shell/1,Trap/1", "Mute", "Night/1,Shell/1", "Prideful", "Rain/1,Shell/1,Trap/1", "Mask/1,Rain/1", "Fearful"
            ))
            initialize()
            action(0, chooseCardCount, 9)
            action(0, playAction, HardKnocks.fearful)
            action(1, playAction, HardKnocks.absentMinded)
            action(2, playAction, HardKnocks.fragile)
            actionNotAllowed(0, withdraw, WithdrawAction(game.currentPlayer.supportTiles.cards.random()))

            action(0, playAction, HardKnocks.prideful)
            expectEquals(2, game.players[0].hardKnocks.cards.size)
            action(1, playAction, game.currentPlayer.hand.cards.first { it.name == "Trauma Night" })
            action(2, playAction, game.currentPlayer.hand.cards.first { it.threats == Threat.Night + Threat.Whistle })

            action(0, withdraw, WithdrawAction(game.currentPlayer.supportTiles.cards.first()))
        }
        testCase(players = 3, name = "Merry Christmas") {
            initialize()
            action(0, chooseCardCount, 3)
            game.players.forEach { it.hand.cards.clear() }
            game.players[0].hand.cards.add(HardKnocks.merryChristmas)
            game.players[1].hardKnocks.cards.add(HardKnocks.fragile)
            action(0, playAction, game.currentPlayer.hand.cards.single())
            println("After Christmas it's ${game.currentPlayerIndex} turn")
            action(0, merryChristmasAction, game.players[1].hardKnocks.cards.single())
            expectEquals(0, game.players[0].hand.size)
            expectEquals(0, game.players[1].hardKnocks.size)
            expectEquals(1, game.currentPlayerIndex)
        }
        testCase(players = 4, name = "Cards for first round") {
            initialize()
            actionNotAllowed(0, chooseCardCount, 0)
            actionNotAllowed(0, chooseCardCount, 1)
            actionNotAllowed(0, chooseCardCount, 2)
            action(0, chooseCardCount, 3)
            expectEquals(3, game.players[0].hand.size)
            expectEquals(3, game.players[1].hand.size)
            expectEquals(3, game.players[2].hand.size)
            expectEquals(3, game.players[3].hand.size)
        }
        testCase(5, name = "Speech, Speech, Speech!") {
            initialize()
            action(0, chooseCardCount, 3)
            game.players.forEach { it.hand.cards.clear() }
            game.players[0].speechesAvailable = 1
            game.players[0].hand.cards.add(GrizzledCard.fromThreat(Threat.Snow))
            game.players[1].hand.cards.add(GrizzledCard.fromThreats(ResourceMap.from(Threat.values())))
            game.players[2].hand.cards.add(HardKnocks.traumas.single { it.threats.has(Threat.Snow, 1) })
            game.players[3].hand.cards.add(GrizzledCard.fromThreats(Threat.values().filter(Threat::item).fold(ResourceMap.empty()) { a, b -> a + b }))
            game.players[4].hand.cards.add(GrizzledCard.fromThreats(Threat.Snow + Threat.Whistle + Trap))
            game.players[4].hand.cards.add(GrizzledCard.fromThreats(Threat.Snow + Threat.Shell))
            game.players[1].apply {
                placedSupportTile = supportTiles.card(supportTiles.cards.random()).remove()
                withdrawn = true
            }
            action(0, giveSpeech, Threat.Snow)

            // Must discard card from your own hand
            val randomDiscarder = game.players.indices.random()
            actionNotAllowed(randomDiscarder, discard, game.players.minus(game.players[randomDiscarder]).random().hand.cards.random())

            game.players.indices.shuffled().forEach {
                when (it) {
                    0 -> actionNotAllowed(it, discard, game.players[it].hand.cards.single()) // Player giving speech
                    1 -> actionNotAllowed(it, discard, game.players[it].hand.cards.single()) // Already withdrawn
                    2 -> action(it, discard, game.players[it].hand.cards.single())
                    3 -> actionNotAllowed(it, discard, game.players[it].hand.cards.random()) // No matching cards
                    4 -> {
                        // Can only discard one card
                        action(it, discard, game.players[it].hand.cards.random())
                        actionNotAllowed(it, discard, game.players[it].hand.cards.single())
                    }
                    else -> throw UnsupportedOperationException()
                }
            }
        }
        val withdrawScorer = scorers.isAction(withdraw)
        val failScorer = scorers.actionConditional(playAction) {
            (this.action.parameter.threats + model.activeThreats).filter { it.value >= 3 }.any()
        }
        val useSupportRemoveHardKnocks = scorers.action(useSupport) { action.parameter.removeHardKnocks.size.toDouble() }
        val supportTroubledPlayer = scorers.action(withdraw) {
            val player = model.calculateSupportedPlayer(playerIndex, action.parameter.support ?: return@action 0.0)
            val charmRequiredScore = if (player.charmAvailable) 0.0 else 0.5
            player.hardKnocks.size.toDouble() + charmRequiredScore
        }
        scorers.ai("#AI_Chicken", withdrawScorer)
        scorers.ai("#AI_Simple", failScorer.weight(-10), useSupportRemoveHardKnocks, supportTroubledPlayer)
    }

    private fun Model.calculateSupportedPlayer(myIndex: Int, supportTile: SupportTile): Player {
        val supportedPlayerIndex = (myIndex + supportTile.offset + this.players.size) % this.players.size
        return this.players[supportedPlayerIndex]
    }

    private fun supportDsl(
        successfulMission: Boolean
    ): suspend GameFlowStepScope<Model>.() -> Unit {
        return {
            val supportedPlayers = game.players.filter { it.placedSupportTile != null }.associateWith {
                val placedSupport = it.placedSupportTile ?: throw IllegalStateException("Player ${it.playerIndex} ($it) has not placed a SupportTile")
                game.calculateSupportedPlayer(it. playerIndex, placedSupport)
            }.toMutableMap()
            this.game.resolveSupportEvent.invoke(Support(supportedPlayers))

            supportedPlayers.forEach {
                log { "${player(it.key.playerIndex)} gives support to ${player(it.value.playerIndex)}" }
            }

            val playersGivenSupport = supportedPlayers.entries.groupingBy { it.value }.eachCount()
            val mostSupported = playersGivenSupport.entries.bestOf { it.value.toDouble() }
            playersGivenSupport.forEach {
                log { "${player(it.key.playerIndex)} was supported by ${it.value} players" }
            }
            println("supportDsl step2 lambda $successfulMission")
            supportedPlayers.filter { it.key.placedSupportTile != null }.forEach {
//                it.value.supportTiles.cards.add(it.key.placedSupportTile!!)
                it.key.placedSupportTile = null
            }

            if (mostSupported.size == 1) {
                val limit = if (successfulMission) 2 else 1
                val supportedPlayer = mostSupported.single().key
                println("Supported player $supportedPlayer with limit $limit (successful? $successfulMission)")
                yieldAction(useSupport) {
                    precondition { playerIndex == supportedPlayer.playerIndex }
                    requires { action.parameter.removeHardKnocks.size <= limit }
                    requires { supportedPlayer.hardKnocks.cards.containsAll(action.parameter.removeHardKnocks) }
                    choose {
                        recursive(Supported(false, emptyList())) {
                            until { chosen.restoreCharm || chosen.removeHardKnocks.size == limit }
                            intermediateParameter { true }
                            parameter { chosen }
                            if (!chosen.restoreCharm) {
                                optionsWithIds({
                                    supportedPlayer.hardKnocks.cards.minus(chosen.removeHardKnocks.toSet())
                                        .plus(GrizzledCard(ResourceMap.empty(), null, null, id = -1)).map { it.id.toString() to it }
                                }) { chosenNext ->
                                    recursion(chosenNext) { acc, next ->
                                        val hardKnock = next.takeIf { it.id != -1 }?.toSingleList() ?: emptyList()
                                        Supported(acc.restoreCharm || next.threats.isEmpty(), acc.removeHardKnocks + hardKnock)
                                    }
                                }
                            }
                        }
                    }
                    perform {
                        if (action.parameter.removeHardKnocks.isEmpty()) {
                            supportedPlayer.charmAvailable = true
                        } else {
                            action.parameter.removeHardKnocks.asSequence().map { supportedPlayer.hardKnocks.card(it) }.forEach { it.moveTo(game.discarded) }
                        }
                    }
                }
            } else {
                log { "Support was tied. No player gets support." }
            }
        }
    }

    private fun ActionRuleScope<Model, Threat>.discardStep(
        discardableThreat: Threat,
        discardablePlayers: List<Player>
    ): suspend GameFlowStepScope<Model>.() -> Unit {
        return {
            yieldAction(discard) {
                precondition { discardablePlayers.any { it.playerIndex == playerIndex } }
                choose {
                    optionsWithIds({ game.players[playerIndex].hand.cards.map { it.id.toString() to it } }) {
                        parameter(it)
                    }
                }
                requires { action.parameter.threats.has(discardableThreat, 1) }
                requires { game.players[playerIndex].hand.cards.contains(action.parameter) }
                perform { game.players[playerIndex].hand.card(action.parameter).moveTo(game.discarded) }
                perform {
                    val remainingPlayers = discardablePlayers.minus(game.players[playerIndex])
                    if (remainingPlayers.isNotEmpty()) {
                        meta.injectStep("discardStep $remainingPlayers", this@discardStep.discardStep(discardableThreat, remainingPlayers))
                    }
                }
            }
        }
    }

}


package net.zomis.games.impl.cards

import net.zomis.bestOf
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.next
import net.zomis.games.common.shifted
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
import net.zomis.games.dsl.flow.GameModifierScope
import net.zomis.games.dsl.flow.SmartActionDsl
import kotlin.math.ceil

object Grizzled {

    class StartMission
    class Support(val supportedPlayers: MutableMap<Player, Player>)
    class Supported(val removeHardKnocks: List<GrizzledCard>) {
        fun toStateString() = "${removeHardKnocks.map { it.toStateString() }}"
    }
    class MoraleDrop(var count: Int)
    class ChangeLeader(var nextPlayer: Int)

    data class WithdrawAction(val support: SupportTile?) {
        fun toStateString() = support?.name ?: "null"
    }
    val factory = GamesApi.gameCreator(Model::class)
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
            private const val singles = 6
            private const val doubles = 2
            val onlySingles = (1..singles).flatMap { listOf(Left, Right) }
            val allTiles = onlySingles + (1..doubles).flatMap { listOf(DoubleLeft, DoubleRight) }
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
                game.trials.top(replayable, "frenzied",2).forEach { it.moveTo(ruleHolder.owner.hand) }
            }
        }
        val wounded = GrizzledCard(hardKnock * 2, "Wounded", "Counts as 2 hard knocks")
        val demoralized = GrizzledCard(hardKnock, "Demoralized", "When the morale drops, flip one extra card (minimum 4)") {
            on(MoraleDrop::class).mutate { event.count++ }
        }

        // Rule modifications
        val panicked = GrizzledCard(hardKnock, "Panicked", "During withdrawal, draw your support tile randomly") {
            // TODO: This can instead be done during Support event.
            action(withdraw) {
                perform {
                    val player = game.players[action.playerIndex]
                    val supportPlayed = action.parameter.support
                    if (supportPlayed != null) {
                        // TODO: Is this done before or after the regular action things? Hopefully after.
                        player.supportTiles.cards.add(supportPlayed)
                    }
                    if (player.supportTiles.isEmpty()) return@perform

                    val randomChoice = player.supportTiles.random(replayable, 1, "panicked", SupportTile::toString).first().card
                    player.placedSupportTile = randomChoice
                }
            }
        }
        val selfish = GrizzledCard(hardKnock, "Selfish", "Your support tile is always redirected to yourself") {
            val owner = this.ruleHolder.owner
            on(Support::class).mutate {
                event.supportedPlayers[owner] = owner
            }
        }
        val mute = GrizzledCard(hardKnock, "Mute", "You can no longer speak or communicate with other players in any way. You may not use a speech") {
            val owner = this.ruleHolder.owner
            action(giveSpeech) {
                precondition { playerIndex != owner.playerIndex }
            }
        }
        val fragile = GrizzledCard(hardKnock, "Fragile", "Other players cannot withdraw as long as they have any cards in hand") {
            action(withdraw) {
                precondition { playerIndex == ruleHolder.owner.playerIndex || game.players[playerIndex].hand.isEmpty() }
            }
        }
        val prideful = GrizzledCard(hardKnock, "Prideful", "You may withdraw only if your hand is empty, or if you are the last one still in the mission") {
            val owner = ruleHolder.owner
            action(withdraw) {
                precondition { owner.playerIndex != playerIndex || owner.hand.isEmpty() || game.players.count { it.inMission } == 1 }
            }
        }
        val merryChristmas = GrizzledCard(ResourceMap.empty(), "Merry Christmas", "Discard a hard knock card from yourself or another player") {
            val owner = this.ruleHolder.owner
            onActivate {
                meta.injectStep("Discard a hard knock card from yourself or another player") {
                    action(merryChristmasAction) {
                        precondition { playerIndex == owner.playerIndex }
                        options { game.players.flatMap { it.hardKnocks.cards } }
                        perform {
                            val zone = game.players.map { it.hardKnocks }.first { it.cards.contains(action.parameter) }
                            zone.card(action.parameter).moveTo(game.discarded)
                        }
                    }
                }
            }
        }
        val hardheaded = GrizzledCard(hardKnock, "Hardheaded", "You cannot withdraw as long as you have 2 or more cards in hand") {
            action(withdraw) {
                precondition { ruleHolder.owner.playerIndex != playerIndex || ruleHolder.owner.hand.size < 2 }
            }
        }
        val absentMinded = GrizzledCard(hardKnock, "Absent-minded", "Before withdrawing, choose and remove one of your support tiles from the game") {
            var discardedTile: Boolean by this.state { false }

            action(withdraw) {
                precondition { playerIndex != ruleHolder.owner.playerIndex || discardedTile }
                perform {
                    discardedTile = false
                }
            }
            action(absentMindedAction) {
                precondition { playerIndex == ruleHolder.owner.playerIndex && !discardedTile }
                options { ruleHolder.owner.supportTiles.cards }
                perform {
                    discardedTile = true
                }
                perform {
                    ruleHolder.owner.supportTiles.cards.remove(action.parameter)
                }
            }
        }
        val tyrannical = GrizzledCard(hardKnock, "Tyrannical", "Take the mission leader role and keep it, preventing the distribution of speeches") {
            onActivate {
                game.missionLeaderIndex = ruleHolder.owner.playerIndex
            }
            on(ChangeLeader::class).mutate { event.nextPlayer = ruleHolder.owner.playerIndex }
        }
        val clumsy = GrizzledCard(hardKnock, "Clumsy", "After your withdrawal, you must draw a trial card and play it") {
            action(withdraw) {
                perform {
                    if (action.playerIndex != ruleHolder.owner.playerIndex) return@perform
                    val card = game.trials.top(replayable, "clumsy", 1).first().card
                    this.meta.forcePerformAction(playAction, playerIndex, card) {}
                }
            }
        }
        val fearful = GrizzledCard(hardKnock, "Fearful", "On your turn, you must withdraw if 2 identical threats are active") {
            action(withdraw) {
                TODO("forceUntil { game.activeThreats.any { it != 2 } }")
            }
        }

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
    ).map { GrizzledCard(threats = it, name = null, description = null) }
    data class PlayedGrizzledCard(val owner: Player, val card: GrizzledCard)
    class GrizzledCard(
        val threats: ResourceMap,
        val name: String?,
        val description: String?,
        val hardKnockEffects: (GameModifierScope<Model, PlayedGrizzledCard>.() -> Unit)? = null
    ): GameSerializable, Viewable {
        override fun serialize(): Any = toStateString()

        fun toStateString(): String = name ?: threats.toStateString()
        override fun toString(): String = super.toString() + ":" + toStateString()

        override fun toView(viewer: PlayerIndex): Any? = mapOf(
            "threats" to threats.toView(),
            "name" to name,
            "description" to (description ?: threats.toStateString())
        )

        fun isHardKnock(): Boolean = threats.has(HardKnock, 1)
    }
    class Player(ctx: Context, val playerIndex: Int): Entity(ctx) {
        var withdrawn by component { false }
        val inMission: Boolean get() = !withdrawn
        var character by component<Character?> { null }
        var charmAvailable by component { true }
        val hardKnocks by cards<GrizzledCard>()
        val hand by cards<GrizzledCard>().privateView(playerIndex) { it.cards }.publicView { it.size }
        val supportTiles by cards<SupportTile>().privateView(playerIndex) { it.cards }.publicView { it.size }
        var placedSupportTile by component<SupportTile?> { null }
        var speechesAvailable by component { 0 }

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
                        .filter { player -> player.hand.cards.any { it.threats.has(discardableThreat, 1) } }
                        .toMutableList()
                    if (discardablePlayers.isNotEmpty()) {
                        meta.injectStep("discard from speech $discardableThreat", discardStep(discardableThreat, discardablePlayers))
                    }
                }
            }
        }
        val useLuckyCharm: SmartActionDsl<Model, GrizzledCard> = {
            choice("card", optional = false) {
                game.playedCards.cards.filter { it.threats.has(game.players[playerIndex].character!!.luckyCharm, 1) }
            }
            standard.apply {
                // TODO: Boolean cost (both requires true -> set false, and inverted)
                precondition { playerIndex == this@Player.playerIndex }
                precondition { charmAvailable }
                perform { charmAvailable = false }

                requires { action.parameter.threats.has(character!!.luckyCharm, 1) }
                perform { game.playedCards.card(action.parameter).moveTo(game.discarded) }
            }
        }
        val withdraw: SmartActionDsl<Model, WithdrawAction> = {
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
            choice("card", optional = false) { hand.cards }
            standard.apply {
                precondition { playerIndex == this@Player.playerIndex }
                perform {
                    if (action.parameter.isHardKnock()) {
                        hand.card(action.parameter).moveTo(hardKnocks)
                        val effect = action.parameter.hardKnockEffects
                        if (effect != null) {
                            meta.addRule(PlayedGrizzledCard(this@Player, action.parameter), effect)
                        }
                    } else {
                        hand.card(action.parameter).moveTo(game.playedCards)
                    }
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
                    )
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
    class Model(override val ctx: Context): Entity(ctx), ContextHolder {
        val startMissionEvent = event<StartMission>()
        val resolveSupportEvent = event<Support>()
        val moraleDropEvent = event<MoraleDrop>()
        val changeLeaderEvent = event<ChangeLeader>()

        val discarded: CardZone<GrizzledCard> by cards()
        val activeThreats: ResourceMap by dynamicValue {
            val cards = players.filter { it.inMission }.flatMap { it.hardKnocks.cards } + playedCards.cards
            cards.fold(ResourceMap.empty()) { acc, next -> acc + next.threats }
                .filter { it.resource != HardKnock && it.resource != Trap }
        }

        var speeches by component { 0 }.setup {
            when (playerCount) {
                5 -> 3
                4 -> 4
                2, 3 -> 5
                else -> throw IllegalArgumentException("No support for $playerCount players")
            }
        }
        val playedCards by cards<GrizzledCard>()
        var round by component { 1 }
        val morale by component { CardZone<GrizzledCard>() }.onSetup {
            it.cards.addAll(threatCards + HardKnocks.allCards)
        }.publicView { it.size }
        val trials: SemiKnownCardZone<GrizzledCard> by component { SemiKnownCardZone(emptyList(), GrizzledCard::toStateString) }
            .onSetup {trialZone ->
                morale.random(replayable, 25, "trials", GrizzledCard::toStateString)
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

    val game = GamesApi.gameContext("Grizzled", Model::class) {
        players(2..5)
        init { Model(ctx) }
        gameFlow {
            loop {
                step("setup mission") {
                    actionHandler(chooseCardCount, game.missionLeader.chooseStartCards)
                }
                step("start") {
                    game.currentPlayerIndex = game.missionLeaderIndex
                    game.startMissionEvent.invoke(StartMission())
                }
                check(game.players.sumOf { it.supportTiles.size } <= game.players.size * 3) {
                    "Too many support tiles in the game!"
                }

                while (game.players.any { it.inMission }) {
                    step("play") {
                        actionHandler(playAction, game.currentPlayer.playCard)
                        actionHandler(giveSpeech, game.currentPlayer.useSpeech)
                        actionHandler(useLuckyCharm, game.currentPlayer.useLuckyCharm)
                        actionHandler(withdraw, game.currentPlayer.withdraw)
                    }
                    val nextPlayer = game.currentPlayerIndex.next(game.players.size) { game.players[it].inMission } ?: break
                    game.currentPlayerIndex = nextPlayer
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

                if (game.missionLeaderIndex != oldMissionLeader) {
                    game.speeches--
                    game.players[oldMissionLeader].speechesAvailable++
                }

                game.round++
                game.players.forEach { it.enterMission() }
            }
        }
    }

    private fun supportDsl(
        successfulMission: Boolean
    ): suspend GameFlowStepScope<Model>.() -> Unit {
        println("supportDsl step $successfulMission")
        return {
            println("supportDsl step lambda $successfulMission")
            val supportedPlayers = game.players.associateWith {
                val supportOffset = it.placedSupportTile?.offset ?: 0
                val supportedPlayerIndex = (it.playerIndex + supportOffset + game.players.size) % game.players.size
                game.players[supportedPlayerIndex]
            }.toMutableMap()
            this.game.resolveSupportEvent.invoke(Support(supportedPlayers))

            supportedPlayers.forEach {
                log { "${player(it.key.playerIndex)} gives support to ${player(it.value.playerIndex)}" }
            }

            val playersGivenSupport = supportedPlayers.entries.groupingBy { it.value }.eachCount()
            val mostSupported = playersGivenSupport.entries.bestOf { it.value.toDouble() }
            mostSupported.forEach {
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
                        recursive(emptyList<GrizzledCard>()) {
                            until { chosen.size == limit }
                            intermediateParameter { true }
                            parameter { Supported(chosen) }
                            optionsWithIds({ supportedPlayer.hardKnocks.cards.minus(chosen).map { it.toStateString() to it } }) {
                                recursion(listOf(it)) { acc, next -> acc + next }
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
                options { game.players[playerIndex].hand.cards }
                requires { action.parameter.threats.has(discardableThreat, 1) }
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


package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.PlayerIndex
import net.zomis.games.components.resources.ResourceChange
import net.zomis.games.context.ActionTypeDefinition
import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.Viewable
import net.zomis.games.dsl.events.EventPriority
import net.zomis.games.dsl.flow.ActionDefinition
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.rules.RuleSpec

object ArtifactActions {
    data class OwnedArtifact(val owner: AlchemistsDelegationGame.Model.Player, val artifact: Artifact)
    class Artifact(
        val name: String,
        val description: String,
        val level: Int,
        val cost: Int,
        val victoryPoints: Int?,
        val immediateEffect: ActionRuleScope<AlchemistsDelegationGame.Model, Artifact>.() -> Unit = {},
        rule: RuleSpec<AlchemistsDelegationGame.Model, OwnedArtifact> = {}
    ): GameSerializable, Viewable {
        val rule: RuleSpec<AlchemistsDelegationGame.Model, OwnedArtifact> = {
            name = this@Artifact.name
            rule.invoke(this)
        }

        override fun toString(): String = name
        override fun serialize(): String = name
        override fun toView(viewer: PlayerIndex) = mapOf("name" to name, "description" to description, "level" to level, "cost" to cost, "victoryPoints" to victoryPoints)
    }

    // An artifact needs: An on-play effect, an effect while in place, a resource map (victory points - may be dynamic, gold). Manipulate actions. Event listeners. Extra state.
    val altarOfGold = Artifact("Altar of Gold", "Immediate effect: Pay 1 to 8 gold pieces. Gain that many points of reputation.", 3, 1, 0, immediateEffect = {
        game.stack.add(AltarOfGold("altarOfGold", playerIndex))
    })
    class AltarOfGold(name: String, private val playerIndex: Int) : AlchemistsDelegationGame.StackItem {
        private val actionType = GamesApi.gameCreator(AlchemistsDelegationGame.Model::class).action(name, Int::class)
        override val ruleSpec: RuleSpec<AlchemistsDelegationGame.Model, Unit> = {
            action(actionType) {
                precondition { playerIndex == this@AltarOfGold.playerIndex }
                requires { game.players[playerIndex].gold >= action.parameter }
                options { 0..game.players[playerIndex].gold }
                perform {
                    val owner = game.players[this@AltarOfGold.playerIndex]
                    owner.gold -= action.parameter
                    owner.reputation += action.parameter
                    log { "$player pays $action gold to get $action reputation" }
                }
            }
        }
    }

    class ScoreArtifactsEvent(val player: AlchemistsDelegationGame.Model.Player) {
        private val artifactsScored: MutableMap<Artifact, Int> = mutableMapOf()
        fun scoreArtifact(ownedArtifact: OwnedArtifact, method: (OwnedArtifact) -> Int) {
            artifactsScored[ownedArtifact.artifact] = method.invoke(ownedArtifact)
        }
    }
    val amuletOfRhetoric = Artifact("Amulet of Rhetoric", "Immediate effect: Gain 5 points of reputation.", 2, 4, 0, immediateEffect = {
        game.players[playerIndex].reputation += 5
    })
    val bootsOfSpeedAction = GamesApi.gameCreator(AlchemistsDelegationGame.Model::class).action("bootsOfSpeed", Boolean::class)
    val bootsOfSpeed = Artifact("Boots of Speed", "On an action space where you have at least one cube, you can perform that action again after everyone is done. Limit once per round. Can't be used to Sell Potions.",
            1, 4, 2) {
        var usedBootsOfSpeed by state2 { false }
        on(game.newRound).perform {
            usedBootsOfSpeed = false
        }
        on(game.spaceDone).perform {
            if (usedBootsOfSpeed) return@perform
            game.stack.add(BootsOfSpeed(ruleHolder.owner.playerIndex, event) { usedBootsOfSpeed = true })
        }
    }
    class BootsOfSpeedUsage(private val playerIndex: Int, position: AlchemistsDelegationGame.HasAction) : AlchemistsDelegationGame.StackItem {
        override val ruleSpec: RuleSpec<AlchemistsDelegationGame.Model, Unit> = {}
    }
    class BootsOfSpeed(private val playerIndex: Int, event: AlchemistsDelegationGame.HasAction, private val onPerform: () -> Unit) : AlchemistsDelegationGame.StackItem {
        override val ruleSpec: RuleSpec<AlchemistsDelegationGame.Model, Unit> = {
            action(bootsOfSpeedAction) {
                precondition { playerIndex == this@BootsOfSpeed.playerIndex }
                options { listOf(false, true) }
                perform {
                    if (!action.parameter) return@perform
                    onPerform.invoke()
                    game.stack.add(BootsOfSpeedUsage(playerIndex, event))
                    log { "$player uses boots of speed at ${event.actionSpace.name}" }
                }
            }
        }
    }

    val bronzeCup = Artifact("Bronze Cup", "This artifact has no special effect, but will earn you victory points.", 3, 4, 4)
    val crystalCabinet = Artifact("Crystal Cabinet", "When scoring artifacts, this is worth 2 points for each artifact you own, including this one.", 3, 5, null) {
        on(ScoreArtifactsEvent::class).perform {
            event.scoreArtifact(ruleHolder) { it.owner.artifacts.size * 2 }
        }
    }
    val discountCard = Artifact("Discount Card", "Your next artifact costs 2 gold less. After that, artifacts cost you 1 gold less.", 1, 3, 1) {
        var usedDiscountCard by state2 { false }

        stateCheckBeforeAction {
            if (game.currentActionSpace != game.buyArtifact) return@stateCheckBeforeAction
            if (game.currentActionSpace?.actionSpace?.nextPlayerIndex() != ruleHolder.owner.playerIndex) return@stateCheckBeforeAction
            val discount = if (usedDiscountCard) 1 else 2
            // Note that this action handler needs to be added for each time the BuyArtifactAction is added. No more, no less.
            meta.addActionHandler(buyArtifactAction) {
                change {
                    val handler = handlers.filterIsInstance<BuyArtifactAction>().firstOrNull() ?: throw IllegalStateException("Handler not found ${game.currentActionSpace}")
                    handler.cost.modify {
                        it - discount
                    }
                    handler.costPerform.modify {
                        it - discount
                    }
                }
                standard.perform {
                    usedDiscountCard = true
                }
            }
        }
    }
    val featherInCap =
        Artifact("Feather in Cap", "During the exhibition: Set aside ingredients from potions you demonstrate successfully. When scoring artifacts, this cap is worth 1 point for each type of ingredient set aside.", 3, 3, null) {
            TODO()
        }
    val hypnoticAmulet =
        Artifact("Hypnotic Amulet", "Immediate effect: Draw 4 favor cards.", 2, 3, 1, immediateEffect = {
            game.favors.deck.random(meta.replayable, 4, "hypnoticAmulet") { it.name }
                .forEach { game.favors.giveFavor(game, it, game.players[playerIndex]) }
        })
    val magicMirror =
        Artifact("Magic Mirror", "When scoring artifacts, this is worth 1 victory point for every 5 reputation points you had at the end of the final round.", 3, 4, null) {
            on(ScoreArtifactsEvent::class).perform {
                event.scoreArtifact(ruleHolder) { it.owner.reputation / 5 }
            }
        }
    val magicMortar = Artifact("Magic Mortar", "When you mix a potion, discard only one of the ingredients. A colleague chooses it randomly.", 1, 3, 1) {
        // TODO: Could be rule modification?
    }

    val periscopeAction = GamesApi.gameCreator(AlchemistsDelegationGame.Model::class).action("periscope", Boolean::class)
    val periscope: Artifact = Artifact("Periscope", "Immediately after a colleague sells or tests a potion, you may look at one of the ingredients. Choose It randomly. Limit once per round.", 1, 3, 1) {
        var usedPeriscope by state2 { false }
        on(game.newRound).perform {
            usedPeriscope = false
        }
        on(game.playerMixPotion).perform {
            if (usedPeriscope) return@perform
            if (ruleHolder.owner.playerIndex == event.playerIndex) return@perform
            game.stack.add(PeriscopeTrigger(event, event.playerIndex) { usedPeriscope = true })
        }
    }
    class PeriscopeTrigger(private val event: PotionActions.IngredientsMix, private val owningPlayer: Int, private val usedPeriscope: () -> Unit) : AlchemistsDelegationGame.StackItem {
        override val ruleSpec: RuleSpec<AlchemistsDelegationGame.Model, Unit> = {
            action(periscopeAction) {
                precondition { playerIndex == owningPlayer }
                options { listOf(false, true) }
                perform {
                    game.stack.pop()
                    if (!action.parameter) return@perform
                    usedPeriscope.invoke()
                    val ingredient =
                        replayable.randomFromList("periscope", event.ingredients.toList(), 1) { it.serialize() }
                            .single()
                    val potion = game.alchemySolution.mixPotion(event.ingredients)
                    logSecret(playerIndex) { "${player(event.playerIndex)} used $ingredient to mix ${potion.textRepresentation}" }
                        .publicLog { "$player uses periscope to see one ingredient used to mix ${potion.textRepresentation}" }
                }
            }
        }
    }


    val printingPress =
        Artifact("Printing Press", "You do not pay 1 gold to the bank when you publish or endorse a theory.", 1, 4, 2) {
            stateCheckBeforeAction {
                if (game.nextActionPlacer() != ruleHolder.owner.playerIndex) return@stateCheckBeforeAction
                TODO("change cost of theory")
            }
        }
    val robeOfRespect =
        Artifact("Robe of Respect", "Whenever you gain reputation points, gain 1 more. This does not apply in the final round.", 1, 4, 0) {
            on(ResourceChange::class, EventPriority.EARLIER).mutate {
                if (this.meta.game.round == 6) return@mutate
                if (
                    event.resourceMap == ruleHolder.owner.resources &&
                    event.diff > 0 &&
                    event.resource == AlchemistsDelegationGame.Resources.Reputation
                ) event.newValue++
            }
        }
    val sealOfAuthority =
        Artifact("Seal of Authority", "When you publish or endorse a theory, gain 2 additional points of reputation.", 2, 4, 0) {
            // TODO: Refactor and place logic here instead. Use an event and modify it.
        }
    val silverChalice =
        Artifact("Silver Chalice", "This artifact has no special effect, but will earn you victory points", 2, 4, 6)
    val freeTest = GamesApi.gameCreator(AlchemistsDelegationGame.Model::class).action("freeTest", PotionActions.IngredientsMix::class).serializer { it.serialize() }
    val thinkingCap =
        Artifact("Thinking Cap", "Immediate effect: Test up to two separate pairs of ingredients in you hand. Do not discard them.", 2, 4, 1, immediateEffect = {
            game.stack.add(ThinkingCap(playerIndex))
            game.stack.add(ThinkingCap(playerIndex))
        })
    class ThinkingCap(private val owner: Int) : AlchemistsDelegationGame.StackItem {
        override val ruleSpec: RuleSpec<AlchemistsDelegationGame.Model, Unit> = {
            action(freeTest) {
                precondition { playerIndex == owner }
                choose {
                    PotionActions.chooseIngredients(this)
                }
                perform {
                    val result = game.alchemySolution.mixPotion(action.parameter.ingredients)
                    logSecret(playerIndex) { "$player mixed ingredients ${action.ingredients.toList()} and got result $result" }
                        .publicLog { "$player tested a pair of ingredients and got $result" }
                }
            }
        }

    }
    val wisdomIdol =
        Artifact("Wisdom Idol", "At the end of the game, Wisdom Idol is worth 1 point for each seal you have on a correct theory.", 3, 4, null) {
            on(ScoreArtifactsEvent::class).perform {
                TODO(ruleHolder.artifact.name)
            }
        }
    val witchTrunk =
        Artifact("Witch's Trunk", "Immediate effect: Draw 7 ingredients. | You no longer draw ingredients when choosing play order.", 2, 3, 2, immediateEffect = {
            val destination = game.players[playerIndex].ingredients
            game.ingredients.deck.random(meta.replayable, 7, "ingredients") { it.toString() }.forEach {
                it.moveTo(destination)
            }
        })
    val artifacts: List<Artifact> = listOf(
        altarOfGold,
        amuletOfRhetoric,
        bootsOfSpeed,
        bronzeCup,
        discountCard,
        crystalCabinet,
        featherInCap,
        hypnoticAmulet,
        magicMirror,
        magicMortar,
        periscope,
        printingPress,
        robeOfRespect,
        sealOfAuthority,
        silverChalice,
        thinkingCap,
        wisdomIdol,
        witchTrunk
    )

    class BuyArtifactAction : SmartActionBuilder<AlchemistsDelegationGame.Model, Artifact>() {
        // TODO: Create an ActionCost class, which is Requirement and Effect combined
        val cost = using { action.parameter.cost }.requires {
            game.players[playerIndex].gold >= it
        }
        val costPerform = using { action.parameter.cost }.perform {
            game.players[playerIndex].gold -= it.coerceAtLeast(0)
        }
    }
    val buyArtifactAction = GamesApi.gameCreator(AlchemistsDelegationGame.Model::class).action("buyArtifact", Artifact::class).serializer { it.serialize() }

    class BuyArtifact(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        val actionable by viewOnly {
            val all = actionRaw(action.actionType).nextStepsAll().map { it.toString() }
            all.associateWith {
                actionRaw(action.actionType).choose(it).anyAvailable()
            }
        }
        override fun extraActions() = listOf(model.favors.allowFavors(Favors.FavorType.SHOPKEEPER))
        override fun extraHandlers() = listOf(buyArtifactAction to BuyArtifactAction()) as List<Pair<ActionType<AlchemistsDelegationGame.Model, Any>, SmartActionBuilder<AlchemistsDelegationGame.Model, Any>>>
        var usedDiscountCard = false

        fun goldModifier(playerIndex: Int): Int = -model.favors.favorsPlayed.cards.count { it == Favors.FavorType.SHOPKEEPER } + when {
            model.players[playerIndex].artifacts.cards.contains(discountCard) -> if (usedDiscountCard) -1 else -2
            else -> 0
        }
        val artifactsInGame by cards<Artifact>()
            .setup { zone ->
                for (level in 1..3) {
                    zone.cards.addAll(replayable.randomFromList("artifacts-$level", artifacts.filter { it.level == level }, 3) { a -> a.name })
                }
                zone
            }.view { it.cards.groupBy { a -> a.level }.values }
        val forSale: CardZone<Artifact> by cards<Artifact>().on(model.newRound) {
            val zone = value as CardZone<Artifact>
            val artifactsLevel = when (event) {
                1 -> 1
                4 -> 2
                6 -> 3
                else -> return@on
            }
            zone.cards.clear()
            artifactsInGame.asSequence().filter { it.card.level == artifactsLevel }.forEach { it.moveTo(zone) }
        }

        override val actionSpace by component { model.ActionSpace(ctx, "BuyArtifacts") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action = ActionTypeDefinition(buyArtifactAction) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
//            requires { game.players[playerIndex].gold >= action.parameter.cost - goldModifier(playerIndex) }
            options { forSale.cards }
            perform {
                actionSpace.resolveNext()
//                val cost = action.parameter.cost - goldModifier(playerIndex)
//                game.players[playerIndex].gold -= if (cost >= 0) cost else 0
                forSale.card(action.parameter).moveTo(game.players[playerIndex].artifacts)
                action.parameter.immediateEffect.invoke(this)
                game.favors.favorsPlayed.moveAllTo(game.favors.discardPile)
            }
            perform {
                log { "$player buys artifact ${action.name}" }
            }
        }
    }

}
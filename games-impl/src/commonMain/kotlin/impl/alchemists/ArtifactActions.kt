package net.zomis.games.impl.alchemists

import net.zomis.games.cards.CardZone
import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.ActionDefinition

object ArtifactActions {
    data class Artifact(
        val name: String,
        val description: String,
        val level: Int,
        val cost: Int,
        val victoryPoints: Int?
    ): GameSerializable {
        override fun serialize(): String = name
    }

    val altarOfGold = Artifact("Altar of Gold", "Immediate effect: Pay 1 to 8 gold pieces. Gain that many points of reputation.", 3, 1, 0)
    val amuletOfRhetoric = Artifact("Amulet of Rhetoric", "Immediate effect: Gain 5 points of reputation.", 2, 4, 0)
    val bootsOfSpeed = Artifact("Boots of Speed", "On an action space where you have at least one cube, you can perform that action again after everyone is done. Limit once per round. Can't be used to Sell Potions.",
            1, 4, 2)
    val bronzeCup = Artifact("Bronze Cup", "This artifact has no special effect, but will earn you victory points.", 3, 4, 4)
    val crystalCabinet = Artifact("Crystal Cabinet", "When scoring artifacts, this is worth 2 points for each artifact you own, including this one.", 3, 5, null)
    val discountCard = Artifact("Discount Card", "Your next artifact costs 2 gold less. After that, artifacts cost you 1 gold less.", 1, 3, 1) // TODO: Could be rule modification
    val featherInCap =
        Artifact("Feather in Cap", "During the exhibition: Set aside ingredients from potions you demonstrate successfully. When scoring artifacts, this cap is worth 1 point for each type of ingredient set aside.", 3, 3, null)
    val hypnoticAmulet =
        Artifact("Hypnotic Amulet", "Immediate effect: Draw 4 favor cards.", 2, 3, 1)
    val magicMirror =
        Artifact("Magic Mirror", "When scoring artifacts, this is worth 1 victory point for every 5 reputation points you had at the end of the final round.", 3, 4, null)
    val magicMortar = Artifact("Magic Mortar", "When you mix a potion, discard only one of the ingredients. A colleague chooses it randomly.", 1, 3, 1) // TODO: Could be rule modification?
    val periscope = Artifact("Periscope", "Immediately after a colleague sells or tests a potion, you may look at one of the ingredients. Choose It randomly. Limit once per round.", 1, 3, 1)
    val printingPress =
        Artifact("Printing Press", "You do not pay 1 gold to the bank when you publish or endorse a theory.", 1, 4, 2)// TODO: Could be rule modification
    val robeOfRespect =
        Artifact("Robe of Respect", "Whenever you gain reputation points, gain 1 more. This does not apply in the final round.", 1, 4, 0)// TODO: Could be rule modification
    val sealOfAuthority =
        Artifact("Seal of Authority", "When you publish or endorse a theory, gain 2 additional points of reputation.", 2, 4, 0)// TODO: Could be rule modification?
    val silverChalice =
        Artifact("Silver Chalice", "This artifact has no special effect, but will earn you victory points", 2, 4, 6)
    val thinkingCap =
        Artifact("Thinking Cap", "Immediate effect: Test up to two separate pairs of ingredients in you hand. Do not discard them.", 2, 4, 1)
    val wisdomIdol =
        Artifact("Wisdom Idol", "At the end of the game, Wisdom Idol is worth 1 point for each seal you have on a correct theory.", 3, 4, null)
    val witchTrunk =
        Artifact("Witch's Trunk", "Immediate effect: Draw 7 ingredients. | You no longer draw ingredients when choosing play order.", 2, 3, 2)// TODO: Could be rule modification
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

    class BuyArtifact(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override fun extraActions() = listOf(model.favors.allowFavors(Favors.FavorType.SHOPKEEPER))
        var usedDiscountCard = false
        var usedPeriscope = false
        var usedBootsOfSpeed: Boolean = false

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
            }.on(model.playerMixPotion) {
                val playerWithPeriscope = model.players.find { it.artifacts.cards.contains(periscope) } ?: return@on
                if (usedPeriscope || playerWithPeriscope.playerIndex == event.playerIndex) return@on
                model.queue.add(action<AlchemistsDelegationGame.Model, Boolean>("periscope", Boolean::class) {
                    precondition { playerIndex == playerWithPeriscope.playerIndex }
                    options { listOf(false, true) }
                    perform {
                        game.queue.removeAt(0)
                        if (!action.parameter) return@perform
                        usedPeriscope = true
                        val ingredient =
                            replayable.randomFromList("periscope", event.ingredients.toList(), 1) { it.serialize() }
                                .single()
                        val potion = game.alchemySolution.mixPotion(event.ingredients)
                        logSecret(playerIndex) { "${player(event.playerIndex)} used $ingredient to mix ${potion.textRepresentation}" }
                            .publicLog { "$player uses periscope to see one ingredient used to mix ${potion.textRepresentation}" }
                    }
                } as ActionDefinition<AlchemistsDelegationGame.Model, Any>)
            }.on(model.spaceDone) {
                if (usedBootsOfSpeed) return@on
                val playerWithBoots = model.players.find { it.artifacts.cards.contains(bootsOfSpeed) } ?: return@on
                model.queue.add(action<AlchemistsDelegationGame.Model, Boolean>("bootsOfSpeed", Boolean::class) {
                    precondition { playerIndex == playerWithBoots.playerIndex }
                    options { listOf(false, true) }
                    perform {
                        game.queue.removeAt(0)
                        if (!action.parameter) return@perform
                        usedBootsOfSpeed = true
                        game.queue.add(event.action as ActionDefinition<AlchemistsDelegationGame.Model, Any>)
                        log { "$player uses boots of speed at ${event.actionSpace.name}" }
                    }
                } as ActionDefinition<AlchemistsDelegationGame.Model, Any>)
            }.on(model.newRound) {
                usedPeriscope = false
                usedBootsOfSpeed = false
            }
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
        override val action = actionSerializable<AlchemistsDelegationGame.Model, Artifact>("buyArtifact", Artifact::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            requires { game.players[playerIndex].gold >= action.parameter.cost - goldModifier(playerIndex) }
            options { forSale.cards }
            perform {
                actionSpace.resolveNext()
                val cost = action.parameter.cost - goldModifier(playerIndex)
                game.players[playerIndex].gold -= if (cost >= 0) cost else 0
                forSale.card(action.parameter).moveTo(game.players[playerIndex].artifacts)
                game.favors.favorsPlayed.moveAllTo(game.favors.discardPile)
            }
            perform {
                val player = game.players[playerIndex]
                val buyAction = action
                if (action.parameter.victoryPoints == null) {
                    TODO("artifact not implemented yet")
                }
                log { "${this.player} buys artifact ${action.name}" }

                when (action.parameter) {
                    robeOfRespect -> TODO("artifact not implemented yet")
                    altarOfGold -> {
                        // Immediate effect: Pay 1 to 8 gold pieces. Gain that many points of reputation
                        game.queue.add(action<AlchemistsDelegationGame.Model, Int>("altarOfGold", Int::class) {
                            precondition { playerIndex == buyAction.playerIndex }
                            options { 1..8 }
                            requires { game.players[playerIndex].gold >= action.parameter }
                            perform {
                                game.players[playerIndex].gold -= action.parameter
                                game.players[playerIndex].reputation += action.parameter
                                log { "$player pays $action gold to get $action reputation" }
                            }
                        } as ActionDefinition<AlchemistsDelegationGame.Model, Any>)
                    }
                    amuletOfRhetoric -> player.reputation += 5
                    hypnoticAmulet -> {
                        // Immediate effect: Draw 4 favor cards
                        game.favors.deck.random(replayable, 4, "hypnoticAmulet") { it.name }
                            .forEach { game.favors.giveFavor(game, it, game.players[playerIndex]) }
                    }
                    thinkingCap -> {
                        // Immediate effect: Test up to two separate pairs of ingredients in you hand. Do not discard them
                        val freeTest = actionSerializable<AlchemistsDelegationGame.Model, PotionActions.IngredientsMix>("freeTest", PotionActions.IngredientsMix::class) {
                            precondition { playerIndex == buyAction.playerIndex }
                            choose {
                                PotionActions.chooseIngredients(this)
                            }
                            perform {
                                val result = game.alchemySolution.mixPotion(action.parameter.ingredients)
                                logSecret(playerIndex) { "$player mixed ingredients ${action.ingredients.toList()} and got result $result" }
                                    .publicLog { "$player tested a pair of ingredients and got $result" }
                            }
                        } as ActionDefinition<AlchemistsDelegationGame.Model, Any>
                        game.queue.add(freeTest)
                        game.queue.add(freeTest)
                    }
                    witchTrunk -> {
                        game.ingredients.deck.random(replayable, 7, "ingredients") { it.toString() }.forEach { it.moveTo(player.ingredients) }
                    }
                }
            }
        }
    }

}
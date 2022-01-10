package net.zomis.games.impl.alchemists

import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable

object ArtifactActions {
    data class Artifact(
        val name: String,
        val description: String,
        val level: Int,
        val cost: Int,
        val victoryPoints: Int
    ): GameSerializable {
        override fun serialize(): String = name
    }

    val altarOfGold = Artifact("Altar of Gold", "Immediate effect: Pay 1 to 8 gold pieces. Gain that many points of reputation.", 3, 1, 0)
    val amuletOfRhetoric = Artifact("Amulet of Rhetoric", "Immediate effect: Gain 5 points of reputation.", 2, 4, 0)
    val bootsOfSpeed = Artifact("Boots of Speed", "On an action space whee you have at least one cube, you can perform that action again after everyone is done. Limi once per round. Can't be used to Sell Potions.",
            1, 4, 2)
    val bronzeCup = Artifact("Bronze Cup", "This artifact has no special effect, but will earn you victory points.", 3, 4, 4)
    val crystalCabinet = Artifact("Crystal Cabinet", "When scoring artifacts, this is worth 2 points for each artifact you own, including this one.", 3, 5, 0)
    val discountCard = Artifact("Discount Card", "Your next artifact costs 2 gold less. After that, artifacts cost you 1 gold less.", 1, 3, 1)
    val featherInCap =
        Artifact("Feather in Cap", "During the exhibition: Set aside ingredients from potions you demonstrate successfully. When scoring artifacts, this cap is worth 1 point for each type of ingredient set aside.", 3, 3, 0)
    val hypnoticAmulet =
        Artifact("Hypnotic Amulet", "Immediate effect: Draw 4 favor cards.", 2, 3, 1)
    val magicMirror =
        Artifact("Magic Mirror", "When scoring artifacts, this is worth 1 victory point for every 5 reputation points you had at the end of the final round.", 3, 4, 0)
    val magicMortar = Artifact("Magic Mortar", "When you mix a potion, discard only one of the ingredients. A colleague chooses it randomly.", 1, 3, 1)
    val periscope = Artifact("Periscope", "Immediately after a colleague sells or tests a potion, you may look at one of the ingredients. Choose It randomly. Limit once per round.", 1, 3, 1)
    val printingPress =
        Artifact("Printing Press", "You do not pay 1 gold to the bank when you publish or endorse a theory.", 1, 4, 2)
    val robeOfRespect =
        Artifact("Robe of Respect", "Whenever you gain reputation points, gain 1 more. This does not apply in the final round.", 1, 4, 0)
    val sealOfAuthority =
        Artifact("Seal of Authority", "When you publish or endorse a theory, gain 2 additional points of reputation.", 2, 4, 0)
    val silverChalice =
        Artifact("Silver Chalice", "This artifact has no special effect, but will earn you victory points", 2, 4, 6)
    val thinkingCap =
        Artifact("Thinking Cap", "Immediate effect: Test up to two separate pairs of ingredients in you hand. Do not discard them.", 2, 4, 1)
    val wisdomIdol =
        Artifact("Wisdom Idol", "At the end of the game, Wisdom Idol is worth 1 point for each seal you have on a correct theory.", 3, 4, 0)
    val witchTrunk =
        Artifact("Witch's Trunk", "Immediate effect: Draw 7 ingredients. | You no longer draw ingredients when choosing play order.", 2, 3, 2)

    class BuyArtifact(model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override val actionSpace by component { model.ActionSpace(this.ctx, "BuyArtifacts") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, Artifact>("buyArtifact", Artifact::class) {

        }
    }

}
package net.zomis.games.impl.alchemists

sealed interface LogItem {
    data class Round(val round: Int) : LogItem

    data class FavorDiscard(val playerIndex: Int, val parameter: Favors.FavorType) : LogItem // TODO: private
    data class FavorUse(val playerIndex: Int, val favor: Favors.FavorType) : LogItem
    data class HerbalistDiscard(val playerIndex: Int, val ingredients: Pair<Ingredient, Ingredient>) : LogItem // TODO: private

    data class TurnOrderChoice(
        val player: AlchemistsDelegationGame.Model.Player,
        val parameter: AlchemistsDelegationGame.Model.TurnOrder,
    ) : LogItem

    data class ActionChoice(val playerIndex: Int, val chosen: List<AlchemistsDelegationGame.Model.ActionChoice>) : LogItem {
        // log { "$player chose actions ${chosen.map { it.spot.actionSpace.name }.sorted()}" }
    }
    data class Cancelled(val playerIndex: Int) : LogItem

    data class TakeIngredient(val playerIndex: Int, val ingredient: String?) : LogItem // TODO: Semi-private
    data class Transmute(val playerIndex: Int, val parameter: Ingredient) : LogItem // TODO: private

    data class Discount(val playerIndex: Int, val parameter: SellAction.SellHero.SellAction) : LogItem // TODO: private
    data class PotionSell(
        val playerIndex: Int,
        val ingredients: PotionActions.IngredientsMix,
        val request: AlchemistsPotion,
        val guarantee: SellAction.Guarantee,
        val sellResult: SellAction.SellResult
    ) : LogItem // TODO: Semi-private

    data class PublishTheory(val playerIndex: Int, val parameter: TheoryActions.TheoryAction) : LogItem // TODO: semi-private
    data class Debunk(
        val playerIndex: Int,
        val aspect: AlchemistsColor?,
        val ingredient: Ingredient?,
        val debunked: List<TheoryActions.Theory>
    ) : LogItem

    data class BuyArtifact(val playerIndex: Int, val parameter: ArtifactActions.Artifact) : LogItem
    data class AltarOfGold(val playerIndex: Int, val parameter: Int) : LogItem
    data class BootsOfSpeed(val playerIndex: Int, val actionSpace: String) : LogItem

    data class PotionTest( // TODO: semi-private
        val playerIndex: Int,
        val actionSpace: AlchemistsDelegationGame.Model.ActionSpace,
        val parameter: PotionActions.IngredientsMix,
        val result: AlchemistsPotion
    ) : LogItem

}

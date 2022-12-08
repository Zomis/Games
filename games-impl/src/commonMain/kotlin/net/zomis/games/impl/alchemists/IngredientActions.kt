package net.zomis.games.impl.alchemists

import net.zomis.games.cards.CardZone
import net.zomis.games.common.times
import net.zomis.games.context.Context
import net.zomis.games.context.Entity

object IngredientActions {

    class Ingredients(model: AlchemistsDelegationGame.Model, ctx: Context) : Entity(ctx), AlchemistsDelegationGame.HasAction {
        val discardPile = CardZone<Ingredient>().also { it.name = "Ingredients-discard" }
        val deck by cards<Ingredient>()
            .setup {
                it.name = "Ingredients-deck"
                it.cards.addAll(Ingredient.values().toList().times(5)); it
            }
            .publicView { it.size }
        val slots by cards<Ingredient>()
            .on(model.spaceDone) {
                // TODO: This should happen LATER than Speed of Boots, which is why we're temporarily checking for transmute instead
                if (event == model.transmute) value.cards.clear()
            }
            .on(model.newRound) {
                println("Refilling ingredients for round $event")
                deck.randomWithRefill(discardPile, replayable, 5, "ingredients-slots") { it.toString() }.forEach {
                    it.moveTo(value)
                }
            }.publicView { it.cards.map { i -> i.serialize() } }
        override val actionSpace by component { model.ActionSpace(ctx, "Ingredients") }
            .setup { it.initialize(if (playerCount == 4) listOf(1, 1) else listOf(1, 1, 1), playerCount) }
        override val action = action<AlchemistsDelegationGame.Model, String>("takeIngredient", String::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            options { listOf("") + slots.cards.distinct().map { it.toString() } }
            perform {
                actionSpace.resolveNext()
                if (action.parameter.isEmpty()) {
                    deck.randomWithRefill(discardPile, replayable, 1, "ingredientDeck") { it.toString() }
                        .forEach { it.moveTo(game.players[playerIndex].ingredients) }
                } else {
                    slots.card(slots.cards.first { it.toString() == action.parameter })
                        .moveTo(game.players[playerIndex].ingredients)
                }
                log { "$player took ingredient ${action.ifEmpty { "from deck" }}" }
            }
        }
    }

    class Transmute(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override val actionSpace by component { model.ActionSpace(ctx, "Transmute") }.setup { it.initialize(listOf(1, 2), playerCount) }
        override val action = actionSerializable<AlchemistsDelegationGame.Model, Ingredient>("transmute", Ingredient::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            options { game.players[playerIndex].ingredients.cards.distinct() }
            perform {
                actionSpace.resolveNext()
                game.players[playerIndex].ingredients.card(action.parameter).moveTo(game.ingredients.discardPile)
                game.players[playerIndex].gold += 1 + game.favors.favorsPlayed.cards.count { it == Favors.FavorType.SAGE }
                game.favors.favorsPlayed.moveAllTo(game.favors.discardPile)
                logSecret(playerIndex) { "$player transmuted ingredient $action" }.publicLog { "$player transmuted an ingredient" }
            }
        }
        override fun extraActions() = listOf(model.favors.allowFavors(Favors.FavorType.SAGE))
    }


}
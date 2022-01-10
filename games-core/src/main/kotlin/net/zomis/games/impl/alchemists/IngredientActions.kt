package net.zomis.games.impl.alchemists

import net.zomis.games.common.times
import net.zomis.games.context.Context
import net.zomis.games.context.Entity

object IngredientActions {

    class Ingredients(model: AlchemistsDelegationGame.Model, ctx: Context) : Entity(ctx), AlchemistsDelegationGame.HasAction {
        val deck by cards<Ingredient>()
            .setup { it.cards.addAll(Ingredient.values().toList().times(5)); it }
            .publicView { it.size }
        val slots by cards<Ingredient>()
            .on(model.newRound) {
                println("Refilling ingredients for round $event")
                value.cards.clear()
                deck.random(replayable, 5, "ingredients-slots") { it.toString() }.forEach { it.moveTo(value) }
            }
        override val actionSpace by component { model.ActionSpace(this.ctx, "Ingredients") }
            .setup { it.initialize(if (playerCount == 4) listOf(1, 1) else listOf(1, 1, 1), playerCount) }
        override val action by action<AlchemistsDelegationGame.Model, String>("takeIngredient", String::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            options { listOf("") + slots.cards.distinct().map { it.toString() } }
            perform {
                actionSpace.resolveNext()
                if (action.parameter.isEmpty()) {
                    deck.random(replayable, 1, "ingredientDeck") { it.toString() }
                        .forEach { it.moveTo(game.players[playerIndex].ingredients) }
                } else {
                    slots.card(slots.cards.first { it.toString() == action.parameter })
                        .moveTo(game.players[playerIndex].ingredients)
                }
            }
        }
    }

    class Transmute(model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override val actionSpace by component { model.ActionSpace(this.ctx, "Transmute") }.setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by action<AlchemistsDelegationGame.Model, Ingredient>("transmute", Ingredient::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            options { game.players[playerIndex].ingredients.cards.distinct() }
            perform {
                actionSpace.resolveNext()
                game.players[playerIndex].ingredients.cards.remove(action.parameter)
                game.players[playerIndex].gold++
            }
        }
    }


}
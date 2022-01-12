package net.zomis.games.impl.alchemists

import net.zomis.games.common.times
import net.zomis.games.common.toSingleList
import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable

object Favors {

    enum class FavorType(val count: Int): GameSerializable {
        ASSISTANT(4), // Add a cube, allow play multiple
        HERBALIST(4), // Draw 3 ingredients, discard 2, resolve one at a time
        ASSOCIATE(3), // Place cubes on top row, can use one per action space
        CUSTODIAN(3), // Drink potion before sell to hero, allow use multiple, can also be used in round 6
        SHOPKEEPER(2), // Discount by 1 gold when buying an artifact
        BARMAID(2), // If you mix an exact match, gain 1 reputation. Otherwise count potion as 1 step better. Stacks when playing multiple
        MERCHANT(2), // Allow selling potion on any slot
        SAGE(2), // Transmute gives 1 extra gold
        ;
        override fun serialize(): String = this.name
    }

    class FavorDeck(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx) {
        val playersDiscardingSetupFavor by value { mutableListOf<Int>() }.setup { it.addAll(0 until playerCount); it }

        val chooseFavor by actionSerializable<AlchemistsDelegationGame.Model, FavorType>("chooseFavor", FavorType::class) {
            precondition { playersDiscardingSetupFavor.contains(playerIndex) }
            options { game.players[playerIndex].favors.cards.distinct() }
            perform {
                playersDiscardingSetupFavor.remove(playerIndex)
                game.players[playerIndex].favors.cards.remove(action.parameter)
            }
        }

        val assistant by action<AlchemistsDelegationGame.Model, Unit>("assistant", Unit::class) {
            precondition { game.players[playerIndex].favors.cards.contains(FavorType.ASSISTANT) }
            perform {
                game.players[playerIndex].favors.cards.remove(FavorType.ASSISTANT)
                game.players[playerIndex].extraCubes++
            }
        }
        val herbalistDiscard by actionSerializable<AlchemistsDelegationGame.Model, PotionActions.IngredientsMix>("discard", PotionActions.IngredientsMix::class) {
            precondition { game.players[playerIndex].favors.cards.contains(FavorType.HERBALIST) }
            choose {
                recursive(emptyList<Ingredient>()) {
                    until { chosen.size == 2 }
                    parameter { PotionActions.IngredientsMix(playerIndex, chosen[0] to chosen[1]) }
                    optionsWithIds({ game.players[playerIndex].ingredients.cards.distinct().map { it.serialize() to it } }) {
                        recursion(it) { acc, next -> acc + next }
                    }
                }
            }
            perform {
                game.players[playerIndex].favors.cards.remove(FavorType.HERBALIST)
                game.players[playerIndex].ingredients.cards.remove(action.parameter.ingredients.first)
                game.players[playerIndex].ingredients.cards.remove(action.parameter.ingredients.second)
            }
        }

        val deck by cards(FavorType.values().flatMap { it.toSingleList().times(it.count) }.toMutableList())
            .publicView { it.size }
    }

}

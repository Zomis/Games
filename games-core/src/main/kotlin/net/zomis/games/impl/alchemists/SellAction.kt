package net.zomis.games.impl.alchemists

import net.zomis.games.common.PlayerIndex
import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable

object SellAction {

    data class Hero(val requests: List<AlchemistsPotion>)
    class SellHero(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        val heroes by component { mutableListOf<Hero>() }
            .setup {
                // Order of potions does matter for three-player games, where selling one may lock another
                val heroes = listOf(
                    Hero(listOf(Alchemists.blue.plus, Alchemists.red.plus, Alchemists.green.plus)),
                    Hero(listOf(Alchemists.blue.minus, Alchemists.red.minus, Alchemists.green.minus)),
                    Hero(listOf(Alchemists.red.minus, Alchemists.green.minus, Alchemists.blue.plus)),
                    Hero(listOf(Alchemists.green.minus, Alchemists.blue.minus, Alchemists.red.plus)),
                    Hero(listOf(Alchemists.red.plus, Alchemists.green.plus, Alchemists.blue.minus)),
                    Hero(listOf(Alchemists.green.plus, Alchemists.blue.plus, Alchemists.red.minus))
                )
                replayable.randomFromList("heroes", heroes, 5) { hero -> hero.requests.map { req -> req.textRepresentation }.joinToString("") }
                it
            }.on(model.newRound) {
                if (event >= 2) value.removeFirst()
            }.publicView { it.take(if (model.round == 1) 1 else 2) }

        enum class SellResult(val price: Int) {
            CORRECT_COLOR_AND_SIGN(4),
            CORRECT_SIGN_WRONG_COLOR(3),
            BLOCKED(2),
            WRONG_SIGN(1)
        }

        enum class Guarantee(val level: Int) {
            WRONG_SIGN(0),
            BLOCKED(1),
            SAME_COLOR(2),
            EXACT_MATCH(3);

            fun result(request: AlchemistsPotion, result: AlchemistsPotion): SellResult {
                return when {
                    result.blocked -> SellResult.BLOCKED
                    result.sign != request.sign -> SellResult.WRONG_SIGN
                    result.color != request.color -> SellResult.CORRECT_SIGN_WRONG_COLOR
                    else -> SellResult.CORRECT_COLOR_AND_SIGN
                }
            }
        }
        class SellAction(var discount: Int?, var ingredients: PotionActions.IngredientsMix?, val guarantee: Guarantee?, val slot: Int?):
            GameSerializable {
            override fun serialize(): String = "$discount/${ingredients?.serialize()}/${guarantee?.level}/$slot"
        }

        val discounts = mutableListOf<Pair<PlayerIndex, Int>>()
        var sellOrder = emptyList<PlayerIndex>()
        val playerCount get() = actionSpace.rows.count { it != null }
        val slots = mutableListOf<Int?>(null, null, null)

        fun reset() {
            discounts.clear()
            sellOrder = emptyList()
        }

        override fun actionAvailable(): Boolean = model.round >= 2
        override val actionSpace by component { model.ActionSpace(this.ctx, "Sell") }
            .setup { it.initialize(listOf(2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, SellAction>("sell", SellAction::class) {
            if (discounts.size < playerCount && playerCount >= 2) {
                precondition { playerIndex in actionSpace.rows.map { it?.first } } // player is selling
                precondition { playerIndex !in discounts.map { it.first } } // player has not chosen discount
                options { (0..3).map { SellAction(it, null, null, null) } }
                requires { action.parameter.discount != null }
                perform {
                    discounts.add(playerIndex to action.parameter.discount!!)
                    // TODO: Merchant and Barmaid favors
                    actionSpace.rows.sortBy { row ->
                        if (row == null) -1
                        else discounts.first { it.first == row.first }.second
                    }
                    sellOrder = discounts.sortedBy { it.second }.map { it.first }
                }
            } else {
                precondition { playerIndex == actionSpace.nextPlayerIndex() }
                requires { action.parameter.ingredients != null && action.parameter.slot != null && action.parameter.guarantee != null }
                choose {
                    options({ (0..2).filter { slots[it] == null } }) { slot ->
                        options({ Guarantee.values().toList() }) { guarantee ->
                            recursive(emptyList<Ingredient>()) {
                                until { chosen.size == 2 }
                                parameter { SellAction(null,
                                    PotionActions.IngredientsMix(playerIndex, chosen[0] to chosen[1]), guarantee, slot) }
                                optionsWithIds({
                                    game.players[playerIndex].ingredients.cards.distinct().minus(chosen.toSet()).map { it.toString() to it }
                                }) {
                                    recursion(it) { acc, i -> acc + i }
                                }
                            }
                        }
                    }
                }
                perform {
                    slots[action.parameter.slot!!] = playerIndex
                    val result = game.alchemySolution.mixPotion(action.parameter.ingredients!!.ingredients)
                    val request = heroes[0].requests[action.parameter.slot!!]
                    val sellResult = action.parameter.guarantee!!.result(request, result)
                    game.players[playerIndex].gold += sellResult.price
                }
            }
        }
    }

}
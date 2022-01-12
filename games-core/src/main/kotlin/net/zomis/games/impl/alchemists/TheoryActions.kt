package net.zomis.games.impl.alchemists

import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable

object TheoryActions {

    class Theory(val seals: MutableList<Seal>, val ingredient: Ingredient, var alchemical: AlchemistsChemical) {
        // TODO: Conflicts affect conferences, grants, top alchemist award. No one can endorse a theory in conflict
        fun getOwnersOfNotProperlyHedgedSeals(solution: AlchemistsChemical): List<AlchemistsDelegationGame.Model.Player> {
            val diffs = diff(solution, this.alchemical)
            return seals.filter { !it.properlyHedged(diffs) }.map { it.owner }
        }
        private fun diff(x: AlchemistsChemical, y: AlchemistsChemical): List<AlchemistsColor> {
            return AlchemistsColor.values().filter { x.properties.getValue(it).sign != y.properties.getValue(it).sign }
        }
    }

    data class Seal(val hedge: AlchemistsColor?, val victoryPoints: Int, val owner: AlchemistsDelegationGame.Model.Player): GameSerializable {
        override fun serialize(): String = "($hedge/$victoryPoints)"
        fun properlyHedged(differences: List<AlchemistsColor>): Boolean {
            return when (differences.count()) {
                0 -> true
                1 -> when (hedge) {
                    null -> false
                    else -> differences.first() == hedge
                }
                else -> false
            }
        }
    }

    fun seals(player: AlchemistsDelegationGame.Model.Player): List<Seal> {
        return listOf(
            Seal(null, 5, player),
            Seal(null, 5, player),
            Seal(null, 3, player),
            Seal(null, 3, player),
            Seal(null, 3, player),
            Seal(AlchemistsColor.BLUE, 0, player),
            Seal(AlchemistsColor.BLUE, 0, player),
            Seal(AlchemistsColor.GREEN, 0, player),
            Seal(AlchemistsColor.GREEN, 0, player),
            Seal(AlchemistsColor.RED, 0, player),
            Seal(AlchemistsColor.RED, 0, player)
        )
    }

    class TheoryAction(val ingredient: Ingredient, val alchemical: AlchemistsChemical, val seal: Seal): GameSerializable {
        override fun serialize(): String = "$ingredient/${alchemical.representation}/${seal.serialize()}"
    }

    class DebunkAction(
        val ingredientsMix: PotionActions.IngredientsMix?,
        val ingredient: Ingredient?, val aspect: AlchemistsColor?
    ): GameSerializable {
        override fun serialize(): String = "$ingredientsMix/$ingredient/$aspect"
    }

    class TheoryBoard(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx) {
        fun find(theory: TheoryAction): Theory? = theories.find { it.ingredient == theory.ingredient }
            ?.also { check(it.alchemical == theory.alchemical) }
        val theories by component { mutableListOf<Theory>() }
    }

    class DebunkTheory(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override fun actionAvailable(): Boolean = model.round >= 2
        override val actionSpace by component { model.ActionSpace(this.ctx, "PublishTheory") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, TheoryAction>("publishTheory", TheoryAction::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            choose {
            }
            perform {
                actionSpace.resolveNext()
            }
        }
    }

    class PublishTheory(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        fun goldBankCost(playerIndex: Int): Int
            = if (model.players[playerIndex].artifacts.cards.contains(ArtifactActions.printingPress)) 0 else 1

        override fun actionAvailable(): Boolean = model.round >= 2
        override val actionSpace by component { model.ActionSpace(this.ctx, "PublishTheory") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, TheoryAction>("publishTheory", TheoryAction::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            choose {
                optionsWithIds({
                    Ingredient.values().toList()
                        .minus(game.theoryBoard.theories.map { it.ingredient }.toSet())
                        .map { it.toString() to it }
                }) { ingredient ->
                    optionsWithIds({
                        Alchemists.alchemyValues
                            .minus(game.theoryBoard.theories.map { it.alchemical }.toSet())
                            .map { it.representation to it }
                    }) { alchemical ->
                        optionsWithIds({ game.players[playerIndex].seals.cards.map { it.toString() to it } }) { seal ->
                            parameter(TheoryAction(ingredient, alchemical, seal))
                        }
                    }
                }
            }
            requires {
                val existingSeals = game.theoryBoard.theories.filter { it.ingredient == action.parameter.ingredient }.sumOf { it.seals.size }
                game.players[playerIndex].gold >= goldBankCost(playerIndex) + existingSeals
            }
            requires {
                playerIndex !in (game.theoryBoard.find(action.parameter)?.seals?.map { it.owner.playerIndex } ?: emptyList())
            }
            perform {
                actionSpace.resolveNext()
                game.players[playerIndex].gold -= goldBankCost(playerIndex)
                val existingTheory = game.theoryBoard.theories.find { it.ingredient == action.parameter.ingredient }
                if (existingTheory != null) {
                    check(existingTheory.alchemical == action.parameter.alchemical)
                    existingTheory.seals.add(action.parameter.seal)
                } else {
                    game.theoryBoard.theories.add(Theory(mutableListOf(action.parameter.seal), action.parameter.ingredient, action.parameter.alchemical))
                }
                game.players[playerIndex].seals.card(action.parameter.seal).remove()
            }
        }
    }


}
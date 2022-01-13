package net.zomis.games.impl.alchemists

import net.zomis.games.context.ActionFactory
import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.ActionDefinition
import net.zomis.games.dsl.flow.GameFlowActionScope

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
        val ingredientsMix: PotionActions.IngredientsMix? = null,
        val expectedResult: AlchemistsPotion? = null,
        val ingredient: Ingredient? = null, val aspect: AlchemistsColor? = null
    ): GameSerializable {
        override fun serialize(): String = "${ingredientsMix?.serialize()}/${expectedResult?.textRepresentation}/${ingredient?.serialize()}/${aspect?.name}"
        fun findDebunkedTheories(solution: Alchemists.AlchemistsSolution, theoryBoard: TheoryBoard): List<Theory> {
            return if (ingredientsMix != null) {
                TODO("master debunking not implemented yet")
            } else {
                val theory = theoryBoard.theories.single { it.ingredient == ingredient }
                val actual = solution.valueOf(ingredient!!)
                if (theory.alchemical.properties[aspect] != actual.properties[aspect]) listOf(theory) else emptyList()
            }
        }
    }

    class TheoryBoard(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx) {
        fun find(theory: TheoryAction): Theory? = theories.find { it.ingredient == theory.ingredient }
            ?.also { check(it.alchemical == theory.alchemical) }
        val theories by component { mutableListOf<Theory>() }
    }

    class DebunkTheory(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override fun actionAvailable(playerIndex: Int, chosen: List<AlchemistsDelegationGame.Model.ActionChoice>): Boolean = model.round >= 2
        override val actionSpace by component { model.ActionSpace(this.ctx, "PublishTheory") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, DebunkAction>("publishTheory", DebunkAction::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            choose {
                if (model.master) {
                    optionsWithIds({ Alchemists.possiblePotions().map { it.textRepresentation to it } }) { expectedResult ->
                        recursive(emptyList<Ingredient>()) {
                            until { chosen.size == 2 }
                            parameter {
                                DebunkAction(
                                    ingredientsMix = PotionActions.IngredientsMix(playerIndex, chosen[0] to chosen[1]),
                                    expectedResult = expectedResult
                                )
                            }
                            optionsWithIds({
                                Ingredient.values().toList().minus(chosen.toSet()).map { it.toString() to it }
                            }) {
                                recursion(it) { acc, i -> acc + i }
                            }
                        }
                    }
                } else {
                    optionsWithIds({ game.theoryBoard.theories.map { it.ingredient.serialize() to it } }) { theory ->
                        optionsWithIds({ AlchemistsColor.values().map { it.name to it } }) { color ->
                            parameter(DebunkAction(ingredient = theory.ingredient, aspect = color))
                        }
                    }
                }
            }
            perform {
                actionSpace.resolveNext()
                if (game.master) {
                    TODO("Master debunking not implemented yet")
                    // Master: Pick potion to mix (using any ingredients) and verify if that mix gives that result or not.
                    // Then check which ones are not possible given that knowledge. Can cause conflict, can cause debunking of one or two theories.
                }
                val debunked = action.parameter.findDebunkedTheories(game.alchemySolution, game.theoryBoard)
                if (debunked.isEmpty()) {
                    game.players[playerIndex].reputation--
                } else {
                    // If debunked at least one, give option of direct publishing
                    val debunkAction = action
                    if (game.publishTheory.actionSpace.has(playerIndex)) {
                        game.queue.add(action<AlchemistsDelegationGame.Model, Boolean>("instantPublish", Boolean::class) {
                            precondition { playerIndex == debunkAction.playerIndex }
                            options { listOf(true, false) }
                            perform {
                                game.queue.removeAt(0)
                                if (action.parameter) {
                                    game.publishTheory.actionSpace.resolveNext(playerIndex)
                                    game.queue.add(createPublishFromDebunkAction(playerIndex, debunked))
                                }
                            }
                        } as ActionDefinition<AlchemistsDelegationGame.Model, Any>)
                    }
                }
            }
        }
    }

    private fun createPublishFromDebunkAction(playerIndex: Int, debunked: List<Theory>): ActionDefinition<AlchemistsDelegationGame.Model, Any> {
        return ActionFactory<AlchemistsDelegationGame.Model, TheoryAction>("instantPublish", TheoryAction::class) {
            theoryAction(this, playerIndex)
            requires { action.parameter.ingredient in debunked.map { it.ingredient } || action.parameter.alchemical in debunked.map { it.alchemical } }
        } as ActionFactory<AlchemistsDelegationGame.Model, Any>
    }

    fun theoryAction(scope: GameFlowActionScope<AlchemistsDelegationGame.Model, TheoryAction>, playerIndex: Int?) {
        scope.precondition { this.playerIndex == playerIndex }
        scope.choose {
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
                    optionsWithIds({ game.players[this.playerIndex].seals.cards.map { it.toString() to it } }) { seal ->
                        parameter(TheoryAction(ingredient, alchemical, seal))
                    }
                }
            }
        }
        scope.requires {
            val existingSeals = game.theoryBoard.theories.filter { it.ingredient == action.parameter.ingredient }.sumOf { it.seals.size }
            game.players[this.playerIndex].gold >= goldBankCost(game, this.playerIndex) + existingSeals
        }
        scope.requires {
            playerIndex !in (game.theoryBoard.find(action.parameter)?.seals?.map { it.owner.playerIndex } ?: emptyList())
        }
        scope.requires { (game.theoryBoard.find(action.parameter)?.seals?.size ?: 0) < 3 }
        scope.perform {
            game.players[this.playerIndex].gold -= goldBankCost(game, this.playerIndex)
            val existingTheory = game.theoryBoard.theories.find { it.ingredient == action.parameter.ingredient }
            if (existingTheory != null) {
                check(existingTheory.alchemical == action.parameter.alchemical)
                for (player in existingTheory.seals.map { it.owner }) {
                    game.players[this.playerIndex].gold--
                    player.gold++
                }
                existingTheory.seals.add(action.parameter.seal)
            } else {
                game.theoryBoard.theories.add(Theory(mutableListOf(action.parameter.seal), action.parameter.ingredient, action.parameter.alchemical))
            }
            val reputationGain = if (game.players[this.playerIndex].artifacts.cards.contains(ArtifactActions.sealOfAuthority)) 3 else 1
            game.players[this.playerIndex].reputation += reputationGain
            game.players[this.playerIndex].seals.card(action.parameter.seal).remove()
        }
    }
    fun goldBankCost(game: AlchemistsDelegationGame.Model, playerIndex: Int): Int
        = if (game.players[playerIndex].artifacts.cards.contains(ArtifactActions.printingPress)) 0 else 1

    class PublishTheory(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override fun actionAvailable(playerIndex: Int, chosen: List<AlchemistsDelegationGame.Model.ActionChoice>): Boolean = model.round >= 2
        override val actionSpace by component { model.ActionSpace(this.ctx, "PublishTheory") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, TheoryAction>("publishTheory", TheoryAction::class) {
            theoryAction(this, actionSpace.nextPlayerIndex())
            perform {
                actionSpace.resolveNext(playerIndex)
            }
        }
    }


}
package net.zomis.games.impl.alchemists

import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.ActionChoicesScope
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.flow.GameFlowActionScope

object PotionActions {

    class IngredientsMix(val playerIndex: Int, val ingredients: Pair<Ingredient, Ingredient>): GameSerializable {
        override fun serialize(): String =
            ingredients.toList().joinToString("+") { it.toString() }
        override fun toString(): String = serialize()
    }
    fun chooseIngredients(scope: ActionChoicesScope<AlchemistsDelegationGame.Model, IngredientsMix>) {
        scope.recursive(emptyList<Ingredient>()) {
            until { chosen.size == 2 }
            parameter { IngredientsMix(playerIndex, chosen[0] to chosen[1]) }
            optionsWithIds({
                game.players[playerIndex].ingredients.cards.distinct().minus(chosen.toSet()).map { it.toString() to it }
            }) {
                recursion(it) { acc, i -> acc + i }
            }
        }
    }

    class TestStudent(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        val actionable by viewOnly {
            actionRaw(action.actionType).nextStepsAll().mapValues { true }
        }
        override fun actionAvailable(playerIndex: Int, chosen: List<AlchemistsDelegationGame.Model.ActionChoice>): Boolean
            = model.round <= 5
        var poisoned by component { false }
        override val actionSpace by component { model.ActionSpace(ctx, "TestStudent") }
            .setup { it.initialize(listOf(1, 1), playerCount) }
        override val action = actionSerializable<AlchemistsDelegationGame.Model, IngredientsMix>("testStudent", IngredientsMix::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            requires {
                game.players[playerIndex].ingredients.cards.containsAll(action.parameter.ingredients.toList())
            }
            requires {
                model.players[playerIndex].gold >= if (poisoned) 1 else 0
            }
            choose { chooseIngredients(this) }
            perform {
                actionSpace.resolveNext()
                val result = model.alchemySolution.mixPotion(action.parameter.ingredients)
                model.playerMixPotion.invoke(action.parameter)
                if (poisoned) model.players[playerIndex].gold--
                if (result.sign == AlchemistsSign.NEGATIVE) poisoned = true
                logSecret(playerIndex) { "$player mixed ingredients ${action.ingredients.toList()} and got result $result" }
                    .publicLog { "$player tested a potion on a student and got $result" }
            }
            requires { action.parameter.ingredients.toList().distinct().size == 2 }
        }
    }

    class Custodian(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        val actionable by viewOnly {
            actionRaw(action.actionType).nextStepsAll()
        }
        override fun actionAvailable(playerIndex: Int, chosen: List<AlchemistsDelegationGame.Model.ActionChoice>): Boolean? {
            val have = model.players[playerIndex].favors.cards.count { it == Favors.FavorType.CUSTODIAN }
            val want = chosen.filter { it.spot == this }.sumOf { it.count }
            return when {
                have > want -> true
                have == want -> null
                else -> false
            }
        }
        override val actionSpace by component { model.ActionSpace(ctx, "Custodian") }
            .setup { it.initialize(listOf(1, 1, 1, 1, 1, 1, 1), playerCount) }
        override val action = actionSerializable<AlchemistsDelegationGame.Model, IngredientsMix>("custodian", IngredientsMix::class) {
            drinkAction(actionSpace, this)
        }
    }

    fun drinkAction(actionSpace: AlchemistsDelegationGame.Model.ActionSpace, scope: GameFlowActionScope<AlchemistsDelegationGame.Model, IngredientsMix>) {
        scope.precondition { playerIndex == actionSpace.nextPlayerIndex() }
        scope.choose { chooseIngredients(this) }
        scope.requires {
            game.players[playerIndex].ingredients.cards.containsAll(action.parameter.ingredients.toList())
        }
        scope.perform {
            actionSpace.resolveNext()
            val result = game.alchemySolution.mixPotion(action.parameter.ingredients)
            game.playerMixPotion.invoke(action.parameter)
            logSecret(playerIndex) { "$player mixed ingredients ${action.ingredients.toList()} and got result $result" }
                .publicLog { "$player drank a potion and got $result" }
            if (result.negative) {
                when (result.color) {
                    AlchemistsColor.BLUE -> game.players[playerIndex].reputation--
                    AlchemistsColor.GREEN -> game.turnPicker.options.firstOrNull { it.chosenBy == null && !it.choosable }?.chosenBy = playerIndex
                    AlchemistsColor.RED -> game.players[playerIndex].extraCubes--
                    else -> throw IllegalStateException("Unexpected potion: $result")
                }
            }
        }
    }

    class TestSelf(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        val actionable by viewOnly {
            actionRaw(action.actionType).nextStepsAll()
        }
        override fun actionAvailable(playerIndex: Int, chosen: List<AlchemistsDelegationGame.Model.ActionChoice>): Boolean = model.round <= 5
        override val actionSpace by component { model.ActionSpace(ctx, "TestSelf") }
            .setup { it.initialize(listOf(1, 1), playerCount) }
        override val action = actionSerializable<AlchemistsDelegationGame.Model, IngredientsMix>("testSelf", IngredientsMix::class) {
            drinkAction(actionSpace, this)
        }
    }

    class Exhibition(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        val actionable by viewOnly {
            actionRaw(action.actionType).nextStepsAll()
        }
        override fun actionAvailable(playerIndex: Int, chosen: List<AlchemistsDelegationGame.Model.ActionChoice>): Boolean = model.round >= 6
        override val actionSpace by component { model.ActionSpace(ctx, "Exhibition") }
            .setup { it.initialize(if (playerCount == 4) listOf(1, 1, 1) else listOf(1, 1, 1, 1), playerCount) }
        override val action = actionSerializable<AlchemistsDelegationGame.Model, IngredientsMix>("exhibit", IngredientsMix::class) {
            precondition { false }
        }
    }

}
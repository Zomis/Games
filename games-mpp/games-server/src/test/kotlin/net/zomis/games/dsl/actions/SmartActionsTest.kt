package net.zomis.games.dsl.actions

import kotlinx.coroutines.test.runTest
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.flow.actions.SmartAction
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.dsl.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SmartActionsTest {

    class ModelMultiple(val values: MutableList<Int> = mutableListOf())
    class ModelSimple(var value: Int = 0)
    val factorySimple = GamesApi.gameCreator(ModelSimple::class)
    val factoryComplex = GamesApi.gameCreator(ModelMultiple::class)
    val change = factorySimple.action("change", SmartAction::class)
    val change2 = factorySimple.action("change2", SmartAction::class)
    val changeMultiple = factoryComplex.action("add", SmartAction::class)
    val simpleModifyGame = factorySimple.game("modify old style") {
        setup {
            playersFixed(1)
            init { ModelSimple() }
        }
        gameFlowRules {}
        gameFlow {
            step("change") {
                class MyActionHandler: SmartActionBuilder<ModelSimple, SmartAction>() {
                    val bonusChoice = choice("multiplier", optional = true) { 1..4 }
                    val number = exampleChoices("number", optional = false) { 6..10 } // Does not require to be within range
                    val changeEffect = using { chosen(number) to chosenOptional(bonusChoice) }.perform {
                        game.value += it.first * (it.second ?: 1)
                    }
                    // TODO: Figure out if and how this can affect other handlers. Does anything need to be injected? Or can the lambdas have access to stuff?
                }
                actionHandler(change) {
                    val number = choice("number", optional = false) { 1..10 }
                    using { chosen(number) }.perform {
                        game.value += it
                    }
                }
                actionHandler(change2, MyActionHandler())
                // TODO: Support complex choices, and multiple choices
                // TODO: If something is an option 1..10, allow only those options in actual action (But remember Dixit story!)
                // TODO: Add `exampleOptions { 1..10 }` (without automatic requires) (choices must have either examples or actual options)
                // TODO: Change perform by +1
                // TODO: Add/Remove/Limit options
                // TODO: Recursive/Nested options is same as before, it's just that that's just *one* choice of potentially many. `finalizeChoice()` in DSL?
                // TODO: Avalon: Choose mission and team at the same time. If no mission chosen, use teamSize.max() of all possible missions.

                actionHandler(change) {
                    change {
                        handlers.first().effect[0].ofType(Int::class).modify { it + 1 }
                    }
                }
                // actionHandler(change2).change..., .invoke, .effect...
                actionHandler(change2) {
                    change {
                        val handler = handlers.filterIsInstance<MyActionHandler>().first()
                        handler.changeEffect.modify { it.first.plus(1) to it.second }
                    }
                }
            }
            eliminations.singleWinner(0)
        }
    }
    val complexModifyGame = factoryComplex.game("modify complex") {
        setup {
            playersFixed(1)
            init { ModelMultiple() }
        }
        gameFlowRules {}
        gameFlow {
            step("change") {
                class MyActionHandler: SmartActionBuilder<ModelMultiple, SmartAction>() {
                    val bonusChoice = choice("multiplier", optional = true) { 1..4 }
                    val number = exampleChoices("number", optional = false) { 6..10 } // Does not require to be within range
                    val changeEffect = using { chosen(number) to chosenOptional(bonusChoice) }.perform {
                        game.values += it.first * (it.second ?: 1)
                    }
                    // TODO: Figure out if and how this can affect other handlers. Does anything need to be injected? Or can the lambdas have access to stuff?
                }
                actionHandler(changeMultiple) {
                    val number = choice("number", optional = false) { 1..10 }
                    using { chosen(number) }.perform {
                        game.values += it
                    }
                }
                actionHandler(changeMultiple, MyActionHandler())
                // TODO: Support complex choices, and multiple choices
                // TODO: If something is an option 1..10, allow only those options in actual action (But remember Dixit story!)
                // TODO: Add `exampleOptions { 1..10 }` (without automatic requires) (choices must have either examples or actual options)
                // TODO: Change perform by +1
                // TODO: Add/Remove/Limit options
                // TODO: Recursive/Nested options is same as before, it's just that that's just *one* choice of potentially many. `finalizeChoice()` in DSL?
                // TODO: Avalon: Choose mission and team at the same time. If no mission chosen, use teamSize.max() of all possible missions.

                actionHandler(changeMultiple) {
                    change {
                        handlers.first().effect[0].ofType(Int::class).modify { it + 1 }
                    }
                }
                // actionHandler(change2).change..., .invoke, .effect...
                actionHandler(changeMultiple) {
                    change {
                        val handler = handlers.filterIsInstance<MyActionHandler>().first()
                        handler.changeEffect.modify { it.first.plus(1) to it.second }
                    }
                }
            }
            eliminations.singleWinner(0)
        }
    }

    @Test
    fun nestedComplexMultipleChoices() = runTest {
        val blocking = BlockingGameListener()
        val g = GamesImpl.game(complexModifyGame).setup().startGame(this, 1) {
            listOf(blocking)
        }
        blocking.await()
        Assertions.assertEquals(emptyList<Int>(), g.model.values)
        blocking.awaitAndPerform(0, change, SmartAction(mapOf("numbers" to listOf(2, 3, 5, 7), "multiplier" to 4)))
        blocking.await()
        Assertions.assertEquals(listOf(2,3,5,7).map { it * 4 }, g.model.values)
    }

    @Test
    fun modifyingEffect() = runTest {
        val blocking = BlockingGameListener()
        val g = GamesImpl.game(simpleModifyGame).setup().startGame(this, 1) {
            listOf(blocking)
        }
        blocking.await()
        Assertions.assertEquals(0, g.model.value)
        blocking.awaitAndPerform(0, change, SmartAction(mapOf("number" to 2)))
        blocking.await()
        Assertions.assertEquals(3, g.model.value)
    }

}
package net.zomis.games.dsl.actions

import kotlinx.coroutines.test.runTest
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.dsl.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SmartActionsTest {

    class ModelSimple(var value: Int = 0)
    val factorySimple = GamesApi.gameCreator(ModelSimple::class)
    val change = factorySimple.action("change", Int::class)
    val change2 = factorySimple.action("change2", Int::class)
    val simpleModifyGame = factorySimple.game("modify old style") {
        setup {
            playersFixed(1)
            init { ModelSimple() }
        }
        gameFlowRules {}
        gameFlow {
            step("change") {
                class MyActionHandler : SmartActionBuilder<ModelSimple, Int>() {
                    //                    val bonusChoice = choice("multiplier", optional = true) { 1..4 }
                    val number =
                        exampleChoices("number", optional = false) { 6..10 } // Does not require to be within range
                    val changeEffect = using { action.parameter }.perform {
                        game.value += it //.first * (it.second ?: 1)
                    }
                    // TODO: Figure out if and how this can affect other handlers. Does anything need to be injected? Or can the lambdas have access to stuff?
                }
                actionHandler(change) {
                    val number = choice("number", optional = false) { 1..10 }
                    using { action.parameter }.perform {
                        println("performing $it on ${game.value}")
                        game.value += it
                    }
                }
                actionHandler(change2, MyActionHandler())
                // TODO: Change perform by +1
                // TODO: If something is an option 1..10, allow only those options in actual action (But remember Dixit story!)
                // TODO: Add `exampleOptions { 1..10 }` (without automatic requires) (choices must have either examples or actual options)
                // TODO: Add/Remove/Limit options
                // TODO: Support complex choices, and multiple choices
                // TODO: Recursive/Nested options is same as before, it's just that that's just *one* choice of potentially many. `finalizeChoice()` in DSL?
                // TODO: Avalon: Choose mission and team at the same time. If no mission chosen, use teamSize.max() of all possible missions.

                actionHandler(change) {
                    change {
                        handlers.first().effect[0].ofType(Int::class).modify {
                            println("modifying $it")
                            it + 1
                        }
                    }
                }
                // actionHandler(change2).change..., .invoke, .effect...
                actionHandler(change2) {
                    change {
                        val handler = handlers.filterIsInstance<MyActionHandler>().first()
                        handler.changeEffect.modify { it + 1 }
                    }
                }
            }
            eliminations.singleWinner(0)
        }
    }

    @Test
    fun modifyingEffect() = runTest {
        val blocking = BlockingGameListener()
        val g = GamesImpl.game(simpleModifyGame).setup().startGame(this, 1) {
            listOf(blocking)
        }
        blocking.await()
        Assertions.assertEquals(0, g.model.value)
        blocking.awaitAndPerform(0, change, 2)
        blocking.await()
        Assertions.assertEquals(3, g.model.value)
    }

}
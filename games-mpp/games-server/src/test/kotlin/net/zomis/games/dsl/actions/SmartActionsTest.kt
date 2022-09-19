package net.zomis.games.dsl.actions

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.listeners.BlockingGameListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
                    // val bonusChoice = choice("multiplier", optional = true) { 1..4 }
                    val number =
                        exampleChoices("number", optional = false) { 6..10 } // Does not require to be within range
                    val requirement = using { action.parameter }.requires {
                        it in 1..10
                    }
                    val changeEffect = using { action.parameter }.perform {
                        game.value += it //.first * (it.second ?: 1)
                    }
                }
                actionHandler(change) {
                    val number = choice("number", optional = false) { 1..10 }
                    using { action.parameter }.perform {
                        println("performing $it on ${game.value}")
                        game.value += it
                    }
                }
                actionHandler(change2, MyActionHandler())
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

    lateinit var g: Game<ModelSimple>
    lateinit var blocking: BlockingGameListener

    suspend fun initializeGame(testScope: TestScope) {
        blocking = BlockingGameListener()
        g = GamesImpl.game(simpleModifyGame).setup().startGame(testScope, 1) {
            listOf(blocking)
        }
        blocking.await()
    }

    @Nested
    @DisplayName("using handler class")
    inner class UsingHandler {
        val actionType = change2
        @Test
        fun modifyingEffect() = runTest {
            initializeGame(this)
            Assertions.assertEquals(0, g.model.value)
            blocking.awaitAndPerform(0, actionType, 2)
            blocking.await()
            Assertions.assertEquals(3, g.model.value)
        }

        @Test
        fun listExampleOptions() = runTest {
            initializeGame(this)
            val available = g.actions.type(actionType)!!.availableActions(0, null).map { it.parameter }
            Assertions.assertEquals(listOf(6, 7, 8, 9, 10), available)
            g.stop()
        }

        @Test
        fun performOutsideOptions() = runTest {
            initializeGame(this)
            blocking.awaitAndPerform(0, actionType, 2)
            blocking.await()
            Assertions.assertEquals(3, g.model.value)
        }

        @Test
        fun performIllegal() = runTest {
            initializeGame(this)
            blocking.awaitAndPerform(0, actionType, -2)
            blocking.await()
            Assertions.assertEquals(0, g.model.value)
            g.stop()
        }
    }

    @Nested
    @DisplayName("using direct handler dsl")
    inner class UsingDsl {
        val actionType = change
        @Test
        fun modifyingEffect() = runTest {
            initializeGame(this)
            Assertions.assertEquals(0, g.model.value)
            blocking.awaitAndPerform(0, actionType, 2)
            blocking.await()
            Assertions.assertEquals(3, g.model.value)
        }

        @Test
        fun listExampleOptions() = runTest {
            initializeGame(this)
            val available = g.actions.type(actionType)!!.availableActions(0, null).map { it.parameter }
            Assertions.assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), available)
            g.stop()
        }

        @Test
        fun `perform outside options should be denied`() = runTest {
            initializeGame(this)
            blocking.awaitAndPerform(0, actionType, 15)
            blocking.await()
            Assertions.assertEquals(0, g.model.value)
            g.stop()
        }

        @Test
        fun performIllegal() = runTest {
            initializeGame(this)
            blocking.awaitAndPerform(0, actionType, -2)
            blocking.await()
            Assertions.assertEquals(0, g.model.value)
            g.stop()
        }
    }

}
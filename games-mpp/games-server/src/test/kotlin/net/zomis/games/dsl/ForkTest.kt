package net.zomis.games.dsl

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.dsl.listeners.IllegalActionListener
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.server2.ais.AIAlphaBetaConfig
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.AlphaBetaAIFactory
import net.zomis.games.server2.ais.ServerAlphaBetaAIs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sign

class ForkTest {

    class Model {
        var value: Int = 0
        var startingValue = 0
        val changes = mutableListOf<Int>()
        override fun toString(): String = "Model($startingValue + $changes = $value)"

        fun change(parameter: Int) {
            this.value += parameter
            this.changes.add(parameter)
        }
    }
    private val factory = GamesApi.gameCreator(Model::class)
    val changeValue = factory.action("changeValue", Int::class)

    @Test
    fun preventIllegalActions() = runTest {

        val spec = factory.game("CheatingTest") {
            setup {
                playersFixed(1)
                init { Model() }
            }
            gameFlow {
                loop {
                    step("loop") {
                        yieldAction(changeValue) {
                            requires {
                                action.parameter < 0
                            }
                            options { -10..10 }
                            perform {
                                game.value++
                                if (game.value >= 3) {
                                    eliminations.eliminateRemaining(WinResult.WIN)
                                }
                            }
                        }
                    }
                }
            }
            gameFlowRules {

            }
        }
        val blockingGameListener = BlockingGameListener()
        val game = GamesImpl.game(spec).setup().startGame(this, 1) {
            listOf(blockingGameListener)
        }
        blockingGameListener.await()
        blockingGameListener.awaitAndPerform(0, changeValue, 10)
        blockingGameListener.await()
        println(game.model.value)
        game.stop()
    }

    @Test
    fun alphaBeta() = runTest {
        val level = 6
        val aiConfig = AlphaBetaAIFactory(null,"DSL-TTT", "AlphaBeta",
            level, false, ServerAlphaBetaAIs.model(ServerAlphaBetaAIs::heuristicTTT)
        )
        val config = aiConfig.toAlphaBetaConfig(aiConfig.configurations.first { it.first == level })
        val gameAI = AIRepository.createAlphaBetaAI("AlphaBetaTest", config as AIAlphaBetaConfig<Any>)
        val blocking = BlockingGameListener()
        val game = GamesImpl.game(DslTTT.game).setup().startGame(this, 2) {
            listOf(
                blocking,
                IllegalActionListener(exception = false),
                gameAI.gameListener(it, 0, delayOverride = 100),
                gameAI.gameListener(it, 1, delayOverride = 100),
            )
        }
        blocking.awaitGameEnd()
        println(game.view(0))
        Assertions.assertTrue(game.isGameOver())
    }

    @Test
    fun gameFlow() = runTest {
        println("Running forkTest")
        val forksCreated = AtomicInteger()
        val spec = factory.game("ForkTest") {
            setup {
                playersFixed(1)
                init { Model() }
                onStart {
                    val startingValue = this.replayable.int("start") {
                        9 - forksCreated.get() // All forks should re-use the replayable value, so this should only be evaluated once.
                    }
                    game.startingValue = startingValue
                    game.value = startingValue
                }
            }
            gameFlow {
                loop {
                    game.change(replayable.int("stepLoop") {
                        (forksCreated.get() - 1).also { println(it) }
                    })
                    step("step") {
                        val options = listOf(-1, 1) // 42 is not an option, but allowed as parameter
                        val forks = options.associateWith {
                            println("Create fork for option $it, current value $game")
                            val g = forkGame {
                                println("Creating fork ${forksCreated.incrementAndGet()}")
                                performAction(changeValue, 0, 40)
                            }
                            println("Fork ended up with $g model ${g?.model} after evaluating option $it with current $game")
                            g
                        }
                        // NOTE: Randomness placed here causes problems with forks as fork is created before this line.
                        yieldAction(changeValue) {
                            requires {
                                println("Evaluating requirement for $action (sign ${action.parameter.sign}) with forks $forks models ${forks.map { it.value?.model }}")
                                if (forks.any { it.value == null }) return@requires true // is fork, ignore requirement from forks
                                when (action.parameter.sign) {
                                    -1 -> forks.getValue(-1)!!.model.value > -50
                                    0 -> true
                                    1 -> forks.getValue(1)!!.model.value < 50
                                    else -> false
                                }
                            }
                            options { options.map { it * 10 } + 0 }
                            perform {
                                game.change(action.parameter)
                            }
                        }
                    }
                }
            }
            gameFlowRules {
                beforeReturnRule("view") {
                    view("value") { game.value }
                    view("startingValue") { game.startingValue }
                    view("changes") { game.changes }
                }
            }
        }

        val blocking = BlockingGameListener()
        val game = GamesImpl.game(spec).setup().startGame(this, 1) {
            listOf(blocking)
        }
        blocking.awaitAndPerform(0, changeValue, 10)
        blocking.await()

        val fork = game.copy().game
        println("Original: ${game.model} vs. fork: ${fork.model}")
        Assertions.assertEquals(game.model.value, fork.model.value)


        val allowed = game.actions.type(changeValue)!!.availableActions(0, null).toList()
        allowed.forEach { println("Allowed: $it") }
        println("Forks created: $forksCreated")
        game.stop()
        fork.stop()
        Assertions.assertEquals(2, allowed.size)
    }


}
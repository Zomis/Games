package net.zomis.games.dsl.actions

import kotlinx.coroutines.test.runTest
import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.ActionChoicesRecursiveSpecScope
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.ActionSampleSize
import net.zomis.games.dsl.impl.Game
import net.zomis.games.impl.SpiceRoadDsl
import net.zomis.games.impl.SpiceRoadGameModel
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.server2.ServerGames
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InfiniteActionsTest {

    data class MyList(val list: MutableList<Int>)
    private fun recursiveSpec(): ActionChoicesRecursiveSpecScope<MyList, List<Int>, MyList>.() -> Unit {
        return {
            intermediateParameter { chosen.isNotEmpty() }
            parameter { MyList(chosen.toMutableList()) }
            options({ 1..10 }) { add ->
                recursion(add) { list, e -> list + e }
//                            options({ -4..-1 }) { remove ->
//                                recursion(add + remove) { list, e -> list + e }
//                            }
            }
        }
    }

    val factory = GamesApi.gameCreator(MyList::class)
    val combine = factory.action("Combine", MyList::class)
    val game = factory.game("InfiniteActions") {
        setup {
            playersFixed(1)
            init { MyList(mutableListOf()) }
        }
        actionRules {
            action(combine) {
                choose {
                    recursive(emptyList(), recursiveSpec())
                }
                effect {
                    game.list.addAll(action.parameter.list)
                }
            }
            allActions.after {
                if (game.list.sum() > 1000) {
                    eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
        }
    }

    val gameFlow = factory.game("InfiniteActionsFlow") {
        setup {
            playersFixed(1)
            init { MyList(mutableListOf()) }
        }
        gameFlow {
            loop {
                step("step") {
                    yieldAction(combine) {
                        choose {
                            recursive(emptyList(), recursiveSpec())
                        }
                        perform { game.list.addAll(action.parameter.list) }
                    }
                }
            }
        }
        gameFlowRules {
            rule("end game") {
                appliesWhen { game.list.sum() > 1000 }
                effect {
                    eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
        }
    }

    private fun testAvailableActions(game: Game<MyList>) {
        Assertions.assertTrue(game.eliminations.playerIndices.any { playerIndex ->
            game.actions.types().any { it.availableActions(playerIndex, null).any() }
        })
        val result = game.actions.type(combine)!!
            .availableActions(0, ActionSampleSize(listOf(2, 2, 2, 2, 0)))
            .toList()

        Assertions.assertEquals(30, result.size)
        Assertions.assertEquals(2, result.filter { it.parameter.list.size == 1 }.size)
        Assertions.assertEquals(4, result.filter { it.parameter.list.size == 2 }.size)
        Assertions.assertEquals(8, result.filter { it.parameter.list.size == 3 }.size)
        Assertions.assertEquals(16, result.filter { it.parameter.list.size == 4 }.size)
    }

    @Test
    fun splendor() = runTest {
        val setup = ServerGames.entrypoint("Splendor")!!.setup()
        val blocking = BlockingGameListener()
        val game = setup.startGame(this, 4) {
            listOf(blocking)
        }
        blocking.await()
        val a = game.actions.type("takeMoney")!!.availableActions(0, null).toList()
        Assertions.assertTrue(a.isNotEmpty())
        game.stop()
    }

    @Test
    fun spiceRoad() = runTest {
        val setup = ServerGames.entrypoint("Spice Road")!!.setup()
        val blocking = BlockingGameListener()
        val game = setup.startGame(this, 2) {
            listOf(blocking)
        } as Game<SpiceRoadGameModel>
        blocking.await()
        val actionType = game.actions.type("play")!!
        val a = actionType.availableActions(0, null).toList().map { (it.parameter as SpiceRoadDsl.PlayParameter) }
        val b = actionType.withChosen(0, listOf(game.model.players[0].hand.cards.first { it.upgrade == 2 }, SpiceRoadGameModel.Spice.YELLOW))
            .depthFirstActions(null)
        println(a)
        println(b.toList())
        Assertions.assertTrue(a.none { it.remove.count == 3 })
        Assertions.assertTrue(a.none { (it.add.spice[SpiceRoadGameModel.Spice.GREEN] ?: 0) > 1 })
        Assertions.assertTrue(b.any())
        Assertions.assertTrue(a.size > 2)
        game.stop()
    }

    @Test
    fun limitedEvaluation() = runTest {
        val blocking = BlockingGameListener()
        val game = GamesImpl.game(game).setup().startGame(this, 1) {
            listOf(blocking)
        }
        blocking.await()
        testAvailableActions(game)

        val actionType = game.actions.type(combine)!!
        val withChosen = actionType.withChosen(0, listOf(10, 10, 10, 10, 10))
        val next = withChosen.nextOptions().toList()
        Assertions.assertEquals(10, next.size)
        next.forEach {
            Assertions.assertEquals(5, it.previouslyChosen.size)
            it.previouslyChosen.forEach { choice -> Assertions.assertEquals(10, choice) }
        }
        (1..10).forEach { v ->
            Assertions.assertTrue(next.any { it.choiceValue == v })
            Assertions.assertTrue(next.any { it.choiceKey == v })
        }
        game.stop()
    }

    @Test
    fun limitedEvaluationGameFlow() = runTest {
        val blocking = BlockingGameListener()
        val game = GamesImpl.game(gameFlow).setup().startGame(this, 1) {
            listOf(blocking)
        }
        blocking.await()
        testAvailableActions(game)

        val actionType = game.actions.type(combine)!!
        val withChosen = actionType.withChosen(0, listOf(10, 10, 10, 10, 10))
        val next = withChosen.nextOptions().toList()
        Assertions.assertEquals(10, next.size)
        next.forEach {
            Assertions.assertEquals(5, it.previouslyChosen.size)
            it.previouslyChosen.forEach { choice -> Assertions.assertEquals(10, choice) }
        }
        (1..10).forEach { v ->
            Assertions.assertTrue(next.any { it.choiceValue == v })
            Assertions.assertTrue(next.any { it.choiceKey == v })
        }
        game.stop()
    }

}
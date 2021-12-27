package net.zomis.games.dsl.actions

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.ActionChoicesRecursiveSpecScope
import net.zomis.games.dsl.ActionChoicesScope
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.ActionSampleSize
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameImpl
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
    fun splendor() {
        val setup = ServerGames.entrypoint("Splendor")!!.setup()
        val game = setup.createGameWithDefaultConfig(4)
        val a = game.actions.type("takeMoney")!!.availableActions(0, null).toList()
        Assertions.assertTrue(a.isNotEmpty())
    }

    @Test
    fun limitedEvaluation() {
        val game = GamesImpl.game(game).setup().createGameWithDefaultConfig(1)
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
    }

}
package net.zomis.games.dsl.actions

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.ActionChoicesNextScope
import net.zomis.games.dsl.ActionChoicesStartScope
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.ActionSampleSize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InfiniteActionsTest {

    data class MyList(val list: MutableList<Int>)
    val factory = GamesApi.gameCreator(MyList::class)
    val combine = factory.action("Combine", MyList::class)
    val game = factory.game("InfiniteActions") {
        setup {
            playersFixed(1)
            init { MyList(mutableListOf()) }
        }
        rules {
            action(combine) {
                choose {
                    fun recursive(scope: ActionChoicesStartScope<MyList, MyList>, list: List<Int>) {
                        if (scope is ActionChoicesNextScope && list.isNotEmpty()) {
                            scope.parameter(MyList(list.toMutableList()))
                        }
                        scope.options({ 1..10 }) { value ->
                            recursive(this, list + value)
                        }
                    }
                    recursive(this, emptyList())
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

    @Test
    fun limitedEvaluation() {
        val game = GamesImpl.game(game).setup().createGame(1, Unit)
        val result = game.actions.type(combine)
            .availableActions(0, ActionSampleSize(listOf(2, 2, 2, 2)))
            .toList()

        Assertions.assertEquals(30, result.size)
        Assertions.assertEquals(2, result.filter { it.parameter.list.size == 1 }.size)
        Assertions.assertEquals(4, result.filter { it.parameter.list.size == 2 }.size)
        Assertions.assertEquals(8, result.filter { it.parameter.list.size == 3 }.size)
        Assertions.assertEquals(16, result.filter { it.parameter.list.size == 4 }.size)
    }

}
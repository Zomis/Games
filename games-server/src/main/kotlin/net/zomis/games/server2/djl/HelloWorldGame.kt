package net.zomis.games.server2.djl

import net.zomis.games.WinResult
import net.zomis.games.dsl.createActionType
import net.zomis.games.dsl.createGame

object HelloWorldGame {

    data class HelloWorldModel(val values: MutableList<Boolean>, var points: Int)
    val action = createActionType("play", Int::class)
    val game = createGame<HelloWorldModel>("HelloWorld") {
        setup(Int::class) {
            this.defaultConfig { 4 }
            this.init { HelloWorldModel((0 until config).map { false }.toMutableList(), 0) }
            this.players(1..1)
        }
        rules {
            action(action) {
                options { 0 until 4 }
                effect {
                    game.points += if (game.values[action.parameter]) -1 else 1
                    game.values[action.parameter] = true
                }
            }
            allActions.after {
                0.takeIf { game.values.all { b -> b } }?.let { eliminations.eliminateRemaining(WinResult.WIN) }
            }
        }
        view {
            this.value("board") { it.values }
            this.value("scores") { game -> listOf(0).map { game.points } }
        }
    }

}
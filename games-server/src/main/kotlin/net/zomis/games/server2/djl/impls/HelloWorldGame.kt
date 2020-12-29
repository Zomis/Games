package net.zomis.games.server2.djl.impls

import net.zomis.games.WinResult
import net.zomis.games.dsl.GameCreator

object HelloWorldGame {

    data class Model(val values: MutableList<Boolean>, var points: Int)
    val factory = GameCreator(Model::class)
    internal val playHelloWorldDJL = factory.action("play", Int::class)
    val game = factory.game("HelloWorld") {
        setup(Int::class) {
            playersFixed(1)
            defaultConfig { 4 }
            init { Model(MutableList(config) { false }, 0) }
        }
        actionRules {
            action(playHelloWorldDJL) {
                options { 0 until 4 }
                effect {
                    game.points += if (game.values[action.parameter]) -1 else 1
                    game.values[action.parameter] = true
                }
            }
            allActions.after {
                when {
                    game.points < -10 -> eliminations.eliminateRemaining(WinResult.LOSS)
                    game.points == game.values.size -> eliminations.eliminateRemaining(WinResult.WIN)
                    game.values.all { it } -> eliminations.eliminateRemaining(WinResult.DRAW)
                }
            }
            view("board") { game.values }
            view("points") { game.points }
        }
    }

}

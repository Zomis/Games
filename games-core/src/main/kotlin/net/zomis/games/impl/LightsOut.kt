package net.zomis.games.impl

import net.zomis.games.api.Games
import net.zomis.games.api.GamesApi
import net.zomis.games.common.Direction4
import net.zomis.games.common.Point

object LightsOut {

    class Config(val width: Int, val height: Int, val numbers: Int, val wrapAround: Boolean)
    class Model(val config: Config) {
        val map = Games.components.grid(config.width, config.height) {
            x, y -> 0
        }

        fun click(point: Point) {
            val flip = Direction4.values().map { it.delta() }.plus(Point(0, 0)).map { it + point }
            flip.filter {
                config.wrapAround || map.isOnMap(it.x, it.y)
            }.map {
                map.wrapAround(it)
            }.forEach {
                val old = map.get(it.x, it.y)
                map.set(it.x, it.y, (old + 1) % config.numbers)
            }
        }

        fun total(): Map<Int, Int> = map.all().groupingBy { it.value }.eachCount()

        lateinit var cleanClicks: List<List<Int>>
        fun computeCleanClicks() {
            if (config.wrapAround) {
                cleanClicks = emptyList()
                return
            }
            /* Analyze tool to check how clicks in the first row affects bottom row */
            cleanClicks = (0 until config.width).map { firstRowClick ->
                val copyMap = Model(config)
                copyMap.click(Point(firstRowClick, 0))
                // Click on row 1 until row 0 is 0.
                (1 until copyMap.map.sizeY).forEach { y ->
                    (0 until copyMap.map.sizeX).forEach { x ->
                        while (copyMap.map.get(x, y - 1) != 0) {
                            copyMap.click(Point(x, y))
                        }
                    }
                }
                (0 until config.width).map { x ->
                    copyMap.map.get(x, copyMap.map.sizeY - 1)
                }
            }
        }
    }

    object Game {
        val factory = GamesApi.gameCreator(Model::class)
        val click = factory.action("click", Point::class)
        val game = factory.game("LightsOut") {
            setup(Config::class) {
                this.playersFixed(1)
                defaultConfig { Config(10, 10, 2, false) }
                onStart {
                    val all = game.map.all().toList().map { Point(it.x, it.y) }
                    val ints = replayable.ints("map") {
                        repeat(1000) {
                            game.click(all.random())
                        }
                        game.map.all().map { it.value }
                    }
                    for (i in game.map.all().iterator().withIndex()) {
                        i.value.value = ints[i.index]
                    }
                }
                init { Model(config).also { it.computeCleanClicks() } }
            }
            gameFlow {
                loop {
                    step("single step") {
                        yieldAction(click) {
                            precondition { true }
                            requires { true }
                            options { game.map.points() }
                            perform {
                                game.click(action.parameter)
                            }
                        }
                    }
                }
            }
            gameFlowRules {
                beforeReturnRule("setup") {
                    view("grid") {
                        game.map.view { it }
                    }
                    view("cleanClicks") {
                        mapOf("grid" to game.cleanClicks)
                    }
                    view("totals") { game.total() }
                }
            }
        }
    }

}
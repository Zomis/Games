package net.zomis.games.server2.djl

import net.zomis.games.Map2DX
import net.zomis.games.PlayerEliminationCallback
import net.zomis.games.WinResult
import net.zomis.games.dsl.Point
import net.zomis.games.dsl.createActionType
import net.zomis.games.dsl.createGame

object GridWorldGame {

    enum class GridWorldTile(val ch: Char, vararg val floats: Float) {
        EMPTY('_', 0f, 0f),
        PLAYER('P', 1f, 0f),
        GOAL('G', 0f, 1f),
        BLOCK('#', 0f, -1f),
        FAIL('F', 0f, -1f),
        ;
    }
    data class GridWorldModel(val eliminations: PlayerEliminationCallback, val map: Map2DX<GridWorldTile>) {
        fun move(parameter: Int) {
            val player = map.all().find { it.value == GridWorldTile.PLAYER }!!
            val delta = when (parameter) {
                0 -> Point(-1, 0)
                1 -> Point(1, 0)
                2 -> Point(0, -1)
                3 -> Point(0, 1)
                else -> throw IllegalArgumentException("Invalid move: $parameter")
            }
            val newTile = map.point(player.x + delta.x, player.y + delta.y).rangeCheck(map)
            val value = newTile?.value ?: GridWorldTile.BLOCK
            if (value == GridWorldTile.GOAL) {
                eliminations.result(0, WinResult.WIN)
            }
            if (value == GridWorldTile.FAIL) {
                eliminations.result(0, WinResult.LOSS)
            }
            if (value == GridWorldTile.EMPTY) {
                player.value = GridWorldTile.EMPTY
                newTile!!.value = GridWorldTile.PLAYER
            }
        }
    }
    data class GridWorldConfig(
        val width: Int, val height: Int, val random: Long,
        val goals: Int, val fails: Int, val blocks: Int
    )
    val actionType = createActionType("move", Int::class)
    val game = createGame<GridWorldModel>("GridWorld") {
        setup(GridWorldConfig::class) {
            players(1..1)
            defaultConfig { GridWorldConfig(3, 2, 42L, 1, 3, 2) }
            init { GridWorldModel(eliminationCallback, generateMap(config)) }
        }
        logic {
            val options = (0..3).toList()
            this.intAction(actionType, { options }) {
                allowed { true }
                effect { it.game.move(it.parameter) }
            }
        }
        view {
            this.value("board") {game ->
                game.map.all().map { it.value.ch }
            }
        }
    }
    fun stateMapper(state: GridWorldModel): FloatArray {
        return state.map.all().map { it.value }.flatMap { it.floats.toList() }.toFloatArray()
    }

    object Maps {
        val noFails6x6 =  """
        ____G
        _#_##
        _#__#
        __#_P
        ____#
        _____"""

        val oneEach3x2 = """
            __G
            P_F
        """.trimIndent()
    }

    private fun generateMap(config: GridWorldConfig): Map2DX<GridWorldTile> {
        val mapString = Maps.oneEach3x2.trimIndent().trim().split('\n').map { it.trim() }
        val size = mapString.map { it.length }
        val map2D = Map2DX(size.max()!!, size.size) {_, _ -> GridWorldTile.EMPTY }
        mapString.forEachIndexed { y, s ->
            s.forEachIndexed { x, c ->
                map2D.set(x, y, GridWorldTile.values().find { it.ch == c }!!)
            }
        }
        return map2D
    }

}
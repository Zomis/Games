package net.zomis.games.server2.djl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.WinResult
import net.zomis.games.common.Direction4
import net.zomis.games.components.Grid
import net.zomis.games.components.GridImpl
import net.zomis.games.dsl.GameCreator

object GridWorldGame {

    enum class GridWorldTile(val ch: Char, vararg val floats: Float) {
        EMPTY('_', 0f, 0f),
        PLAYER('P', 1f, 0f),
        GOAL('G', 0f, 1f),
        BLOCK('#', 0f, -1f),
        FAIL('F', 0f, -1f),
        ;
    }
    data class GridWorldModel(val eliminations: PlayerEliminationsWrite, val map: Grid<GridWorldTile>) {
        fun move(parameter: Direction4) {
            val player = map.all().find { it.value == GridWorldTile.PLAYER }!!
            val delta = parameter.delta()
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
    val factory = GameCreator(GridWorldModel::class)
    val gridWorldMove = factory.action("move", Direction4::class).serializer { it.order() }
    val game = factory.game("GridWorld") {
        setup(GridWorldConfig::class) {
            players(1..1)
            defaultConfig { GridWorldConfig(3, 2, 42L, 1, 3, 2) }
            init { GridWorldModel(eliminationCallback, generateMap(config)) }
        }
        actionRules {
            action(gridWorldMove) {
                options { Direction4.values().asIterable() }
                effect { game.move(action.parameter) }
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

    private fun generateMap(config: GridWorldConfig): Grid<GridWorldTile> {
        val mapString = Maps.oneEach3x2.trimIndent().trim().split('\n').map { it.trim() }
        val size = mapString.map { it.length }
        val map2D = GridImpl(size.maxOrNull()!!, size.size) { _, _ -> GridWorldTile.EMPTY }
        mapString.forEachIndexed { y, s ->
            s.forEachIndexed { x, c ->
                map2D.set(x, y, GridWorldTile.values().find { it.ch == c }!!)
            }
        }
        return map2D
    }

}
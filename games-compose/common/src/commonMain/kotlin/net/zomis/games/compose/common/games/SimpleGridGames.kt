package net.zomis.games.compose.common.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object SimpleGridGames {
    private val tileSize = 48
    private val padding = 8

    @Composable
    fun TTT(view: Any) {
        if (view !is Map<*, *>) return
        val map = view as Map<String, Any>
        val board = view["board"] as Map<String, Any>

        val currentPlayer = view["currentPlayer"] as Int
        val actionName = view["actionName"] as String
        val width = board["width"] as? Int ?: return
        val height = board["height"] as? Int ?: return
        val grid = board["grid"] as List<List<Map<String, Any>>>
        Column {
            Text(map.toString())

            Box {
                repeat (width * height) {
                    val x = it % width
                    val y = it / width
                    val owner = grid[y][x]["owner"] as Int?
                    val modifier = Modifier.requiredSize(tileSize.dp)
                        .offset((tileSize * x).dp, (tileSize * y).dp)
                    if (owner != null) {
                        Piece(owner, modifier = modifier)
                    } else {
                        Piece(2, modifier = modifier)
//                        Box(Modifier.size(32.dp))
                    }
                }
            }
        }
    }

}
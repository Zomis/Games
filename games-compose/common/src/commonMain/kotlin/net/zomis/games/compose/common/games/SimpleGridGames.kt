package net.zomis.games.compose.common.games

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.zomis.games.compose.common.game.GameClient

object SimpleGridGames {
    private val tileSize = 48
    private val padding = 8

    @Composable
    fun TTT(view: Any, gameClient: GameClient, playerIndex: Int) {
        println("Recompose TTT")

        if (view !is Map<*, *>) return
        val board = view["board"] as Map<String, Any>

        val currentPlayer = view["currentPlayer"] as Int
        val actionName = view["actionName"] as String
        val width = board["width"] as? Int ?: return
        val height = board["height"] as? Int ?: return
        val grid = board["grid"] as List<List<Map<String, Any>>>

        GridView(
            width, height, grid, modifier = Modifier,
            tileSize = tileSize.dp, tilePadding = padding.dp,
            onClick = { point, value ->
                gameClient.postAction(actionName, point)
            }, clickable = { x, y, piece ->
                currentPlayer == gameClient.playerIndex.value && piece["owner"] == null
            }, tile = { x, y, piece ->
                val owner = piece["owner"] as Int?
                if (owner != null) Piece(owner, modifier = Modifier.border(1.dp, color = Color.White).size(tileSize.dp))
//                else Piece(3, modifier = Modifier.border(1.dp, color = Color.White).size(tileSize.dp))
            }
        )
    }

}
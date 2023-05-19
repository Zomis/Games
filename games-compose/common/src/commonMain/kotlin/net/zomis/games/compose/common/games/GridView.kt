package net.zomis.games.compose.common.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.times
import net.zomis.games.components.Point

@Composable
fun <T> GridView(
    width: Int,
    height: Int,
    grid: List<List<T>>,
    modifier: Modifier,
    tileSize: Dp,
    tilePadding: Dp,
    onClick: (Point, T) -> Unit,
    clickable: (x: Int, y: Int, piece: T) -> Boolean,
    tile: @Composable (x: Int, y: Int, piece: T) -> Unit,
) {
    Box(modifier = Modifier.requiredSize(tileSize * width, tileSize * height).background(Color.Yellow).then(modifier)) {
        repeat (width * height) {
            val x = it % width
            val y = it / width
            val model = grid[y][x]

            val tileModifier = Modifier
//                .padding(tilePadding)
                .requiredSize(tileSize)
                .absoluteOffset(x * tileSize, y * tileSize)
                .background(Color.Red)

            Box(tileModifier.clickable(enabled = clickable.invoke(x, y, model)) {
                onClick.invoke(Point(x, y), model)
            }) {
                tile.invoke(x, y, model)
            }
        }
    }

}
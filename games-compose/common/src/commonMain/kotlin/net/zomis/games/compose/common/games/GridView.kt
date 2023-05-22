package net.zomis.games.compose.common.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import androidx.compose.ui.unit.toSize
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

/**
 * Auto-scaling gridview. Make sure that the modifier has both width and height constraint set.
 *
 * Adapted from https://stackoverflow.com/a/67709572/1310566
 */
@Composable
fun <T> GridViewAutoScale(
    gridWidth: Int,
    gridHeight: Int,
    grid: List<List<T>>,
    modifier: Modifier,
    onClick: (Point, T) -> Unit,
    clickable: (x: Int, y: Int, piece: T) -> Boolean,
    tile: @Composable (x: Int, y: Int, piece: T) -> Unit,
) {
    var size by remember { mutableStateOf (Size.Zero) }
    val width = with(LocalDensity.current) {
        (size.width / gridWidth).toDp()
    }
    val height = with(LocalDensity.current) {
        (size.height / gridHeight).toDp()
    }
    val minSize = min(width, height)
    Column(modifier.onGloballyPositioned { coordinates ->
        size = coordinates.size.toSize()
    }) {
        repeat(gridHeight) { y ->
            Row {
                repeat(gridWidth) { x ->
                    val model = grid[y][x]
                    val tileModifier = Modifier
                        .requiredSize(
                            width = minSize,
                            height = minSize,
                        )
                        .background(Color.Red)

                    Box(tileModifier.clickable(enabled = clickable.invoke(x, y, model)) {
                        onClick.invoke(Point(x, y), model)
                    }) {
                        tile.invoke(x, y, model)
                    }
                }
            }
        }
    }
}

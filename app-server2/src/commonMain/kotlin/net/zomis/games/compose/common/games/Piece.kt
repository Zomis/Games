package net.zomis.games.compose.common.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

val colors = listOf(
    Color.Blue,
    Color.Red,
    Color.Green,
    Color.Yellow,
    Color.Magenta,
    Color(red = 1f, green = 0.5f, blue = 0f), // Orange
    Color.White,
    Color.Black,
)

@Composable
fun Piece(owner: Int, modifier: Modifier) {
    require(owner >= 0)
    require(owner < colors.size)
    val color = colors[owner]
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = color)
    ) {}
}

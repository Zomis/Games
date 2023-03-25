package net.zomis.games.compose.common

import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.runtime.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.Game
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.SplendorGame

@Composable
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }
    var game by remember { mutableStateOf<Game<SplendorGame>?>(null) }

    LaunchedEffect(Unit) {
        val dsl = GamesImpl.game(DslSplendor.splendorGame).setup().startGame(this, 2) {
            emptyList()
        }
        game = dsl
    }

    Button(onClick = {
        text = "Hello, ${game?.model?.board?.asSequence()?.groupingBy { it.card.discounts.entries().toList().first().resource }?.eachCount()}"
    }) {
        Text(text)
    }
}

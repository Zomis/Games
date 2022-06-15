package me.simon.common

import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.SplendorGame

@Composable
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }
    val game by remember {
        val dsl = GamesImpl.game(DslSplendor.splendorGame).setup().let { it.createGame(2, it.configs()) }
        mutableStateOf(dsl)
    }

    Button(onClick = {
        text = "Hello, ${game.model.board.asSequence().groupingBy { it.card.discounts.moneys.toList().first().first }.eachCount()}"
    }) {
        Text(text)
    }
}

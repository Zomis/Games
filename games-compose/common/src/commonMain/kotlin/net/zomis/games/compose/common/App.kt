package net.zomis.games.compose.common

import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.runtime.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.Game
import net.zomis.games.impl.DslSplendor
import net.zomis.games.impl.SplendorGame

@Composable
fun App(appModel: AppModel) {
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("Hello, World!") }
    var game by remember { mutableStateOf<Game<SplendorGame>?>(null) }

    LaunchedEffect(Unit) {
        game = GamesImpl.game(DslSplendor.splendorGame).setup().startGame(this, 2) {
            emptyList()
        }
        text = "Hello, ${game!!.model.board.asSequence().groupingBy { it.card.discounts.entries().toList().first().resource }.eachCount()}"
    }

    Button(onClick = {
        text = "Hello, ${game!!.model.board.asSequence().groupingBy { it.card.discounts.entries().toList().first().resource }.eachCount()}"
        scope.launch {
            val response: HttpResponse = appModel.ktorClient.get("https://ktor.io/")
            text = response.bodyAsText()
        }
    }) {
        Text(text)
    }
}

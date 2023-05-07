package net.zomis.games.compose.common

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import net.zomis.games.server2.invites.PlayerInfo
import org.jetbrains.skia.Image as SkiaImage

suspend fun loadPicture(httpClient: HttpClient, url: String): ImageBitmap {
    val image = httpClient.use { client ->
        client.get(url).readBytes()
    }
    return SkiaImage.makeFromEncoded(image).toComposeImageBitmap()
}

@Composable
fun PlayerImage(httpClient: HttpClient, player: PlayerInfo) {
    if (player.picture == null) return
    // TODO: Store previously loaded images in a Map<URL, ImageBitmap> cache?
    var image by remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(player.picture) {
        image = loadPicture(httpClient = httpClient, url = player.picture!!)
    }
    if (image != null) {
        Image(bitmap = image!!, contentDescription = null)
    }
}

@Composable
@Preview
fun PlayerImagePreview() {
    val httpClient = HttpClient(CIO)
    PlayerImage(httpClient, TestData.testPlayerInfo)
}

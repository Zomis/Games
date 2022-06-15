import me.simon.common.App
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.zomis.games.api.GamesApi

fun main() = application {
    println(GamesApi)
    Window(onCloseRequest = ::exitApplication) {
        MaterialTheme {
            App()
        }
    }
}
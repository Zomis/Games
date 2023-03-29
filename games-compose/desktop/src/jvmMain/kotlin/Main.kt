import net.zomis.games.compose.common.App
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.zomis.games.api.GamesApi
import net.zomis.games.compose.common.AppModel

fun main() = application {
    println(GamesApi)
    Window(onCloseRequest = ::exitApplication) {
        MaterialTheme {
            App(AppModel())
        }
    }
}
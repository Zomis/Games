package net.zomis.games.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.codedisaster.steamworks.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.Dispatchers
import net.zomis.games.compose.common.*
import net.zomis.games.compose.common.gametype.SupportedGames
import net.zomis.games.compose.server2.RootContent
import net.zomis.games.compose.server2.DefaultRootComponent as Server2Root
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.reflect.KClass

class DesktopPlatform : PlatformTools {
    private val mapper = jacksonObjectMapper()

    override fun runOnUiThread(block: () -> Unit) = runOnUiThreadDesktop(block)

    override fun toJson(value: Any): String = mapper.writeValueAsString(value)

    override fun <T : Any> fromJson(json: Any, type: KClass<T>): T {
        println("Json convert: $json (of type ${json::class} to $type")
        if (json::class == type) {
            println("Json quick return")
            return json as T
        }
        println("Json slow return")
        return mapper.convertValue(json, type.java)
    }

}

internal fun <T> runOnUiThreadDesktop(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return block()
    }

    var error: Throwable? = null
    var result: T? = null

    SwingUtilities.invokeAndWait {
        try {
            result = block()
        } catch (e: Throwable) {
            error = e
        }
    }

    error?.also { throw it }

    @Suppress("UNCHECKED_CAST")
    return result as T
}

@OptIn(ExperimentalDecomposeApi::class)
fun main() {
    val lifecycle = LifecycleRegistry()
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        install(WebSockets)
    }
    val clientConfig = ClientConfig()

    application {
        val coroutineScope = CoroutineScope(Dispatchers.Default, lifecycle)
        val root = runOnUiThreadDesktop {
            Server2Root(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                httpClient = httpClient,
                localStorage = FileLocalStorage(Path.of("localStorage")),
                mainScope = coroutineScope,
                platformTools = DesktopPlatform(),
                clientConfig = clientConfig,
                gameTypeStore = SupportedGames(DesktopPlatform())
            )
        }

        val windowState = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center))

        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = {
                this.exitApplication()
            },
            state = windowState,
            title = "Zomis' Games"
        ) {
            MaterialTheme(/*colors = darkColors()*/) {
                Surface {
                    RootContent(component = root, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
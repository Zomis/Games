package net.zomis.games.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.*
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zomis.games.compose.common.network.ClientConnection
import kotlin.coroutines.CoroutineContext

typealias Content = @Composable () -> Unit

fun <T : Any> T.asContent(content: @Composable (T) -> Unit): Content = { content(this) }

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    val localStorage: LocalStorage

    sealed interface Child {
        class LoginChild(val component: DefaultLoginComponent) : Child
        class HomeChild(val component: DefaultHomeComponent) : Child
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val platformTools: PlatformTools,
    private val mainScope: CoroutineScope,
    private val httpClient: HttpClient,
    override val localStorage: LocalStorage,
) : ComponentContext by componentContext, RootComponent {

    private val navigation = StackNavigation<Configuration>()

    private val _stack = childStack(
        source = navigation,
        initialConfiguration = Configuration.Login,
        handleBackButton = true,
        childFactory = ::child
    )

    override val stack: Value<ChildStack<*, RootComponent.Child>> = _stack

    private fun child(configuration: Configuration, componentContext: ComponentContext): RootComponent.Child =
        when (configuration) {
            is Configuration.Login -> RootComponent.Child.LoginChild(
                loginComponent(componentContext)
            )
            is Configuration.Home -> RootComponent.Child.HomeChild(
                homeComponent(componentContext, configuration.connection)
            )
            else -> TODO()
        }

    private fun homeComponent(componentContext: ComponentContext, connection: ClientConnection): DefaultHomeComponent
        = DefaultHomeComponent(componentContext, connection)

    private fun loginComponent(componentContext: ComponentContext)
        = DefaultLoginComponent(
            componentContext = componentContext,
            httpClient = httpClient,
            localStorage = localStorage,
            context = mainScope.coroutineContext
    ) {
        // https://arkivanov.github.io/Decompose/getting-started/quick-start/
        platformTools.runOnUiThread {
            navigation.push(Configuration.Home(it))
        }
    }

}

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    Children(
        stack = component.stack,
        modifier = modifier,
        animation = stackAnimation(fade() + scale())
    ) {
        when (val child = it.instance) {
            is RootComponent.Child.LoginChild -> LoginContent(component = child.component)
            is RootComponent.Child.HomeChild -> HomeContent(component = child.component)
        }
    }
}
package net.zomis.games.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.*
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.StackAnimation
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import io.ktor.client.*

typealias Content = @Composable () -> Unit

fun <T : Any> T.asContent(content: @Composable (T) -> Unit): Content = { content(this) }

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        class LoginChild(val component: DefaultLoginComponent) : Child
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val httpClient: HttpClient,
    private val database: Int
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
            else -> TODO()
        }

    private fun loginComponent(componentContext: ComponentContext)
        = DefaultLoginComponent(
            componentContext = componentContext,
            httpClient = httpClient,
            // https://arkivanov.github.io/Decompose/getting-started/quick-start/
        )

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
        }
    }
}
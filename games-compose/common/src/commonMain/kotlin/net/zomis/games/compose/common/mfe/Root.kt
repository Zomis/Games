package net.zomis.games.compose.common.mfe

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.compose.common.*
import net.zomis.games.compose.common.game.DefaultGameComponent
import net.zomis.games.compose.common.game.GameComponent
import net.zomis.games.compose.common.game.GameContent

typealias Content = @Composable () -> Unit

fun <T : Any> T.asContent(content: @Composable (T) -> Unit): Content = { content(this) }

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    val localStorage: LocalStorage

    sealed interface Child {
        class MenuChild(val component: MenuComponent) : Child
        class LocalGameChild(val component: MfeLocalGameComponent) : Child
        class GameChild(val component: GameComponent) : Child
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val platformTools: PlatformTools,
    private val mainScope: CoroutineScope,
    private val httpClient: HttpClient,
    private val clientConfig: ClientConfig,
    override val localStorage: LocalStorage,
) : ComponentContext by componentContext, RootComponent {
    private val gameTypeStore = MfeGameTypes(platformTools)

    private val navigation = StackNavigation<Configuration>()
    private val navigator: Navigator<Configuration> = PlatformNavigator(platformTools, navigation)

    private val _stack = childStack(
        source = navigation,
        initialConfiguration = Configuration.Menu,
        handleBackButton = true,
        childFactory = ::child
    )

    override val stack: Value<ChildStack<*, RootComponent.Child>> = _stack

    private fun child(configuration: Configuration, componentContext: ComponentContext): RootComponent.Child =
        when (configuration) {
            is Configuration.Menu -> RootComponent.Child.MenuChild(
                DefaultMenuComponent(navigator)
            )
            is Configuration.LocalGame -> RootComponent.Child.LocalGameChild(
                DefaultMfeLocalGameComponent(
                    componentContext, configuration.ai,
                    gameTypeStore.defaultGameType
                )
            )
            is Configuration.OnlineGame -> RootComponent.Child.GameChild(
                DefaultGameComponent(
                    componentContext, configuration.connection,
                    configuration.gameStarted,
                    gameTypeStore.getGameType(configuration.gameStarted.gameType)!!,
                )
            )
            else -> throw UnsupportedOperationException("Unknown child for configuration: $configuration")
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
            is RootComponent.Child.MenuChild -> MenuScreen(component = child.component)
            is RootComponent.Child.LocalGameChild -> LocalGameContent(component = child.component)
            is RootComponent.Child.GameChild -> GameContent(component = child.component)
            else -> throw UnsupportedOperationException("Unknown child: $child")
        }
    }
}
package net.zomis.games.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation.stackAnimation
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.zomis.games.compose.common.gametype.GameTypeStore
import net.zomis.games.compose.common.lobby.*
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message

typealias Content = @Composable () -> Unit

fun <T : Any> T.asContent(content: @Composable (T) -> Unit): Content = { content(this) }

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    val localStorage: LocalStorage

    sealed interface Child {
        class LoginChild(val component: LoginComponent) : Child
        class HomeChild(val component: HomeComponent) : Child
        class CreateInviteChild(val component: CreateInviteComponent) : Child
        class ViewInviteChild(val component: InviteComponent) : Child
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val platformTools: PlatformTools,
    private val gameTypeStore: GameTypeStore,
    private val mainScope: CoroutineScope,
    private val httpClient: HttpClient,
    private val clientConfig: ClientConfig,
    override val localStorage: LocalStorage,
) : ComponentContext by componentContext, RootComponent {

    private val navigation = StackNavigation<Configuration>()
    private val navigator: Navigator = PlatformNavigator(platformTools, navigation)

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
            is Configuration.CreateInvite -> RootComponent.Child.CreateInviteChild(
                DefaultCreateInviteComponent(componentContext, configuration.invitePrepare,
                    gameDetails = configuration.gameTypeDetails) {
                    mainScope.launch {
                        configuration.connection.send(ClientToServerMessage.InvitePrepareStart(it.gameType, it.genericGameOptions.toServerOptions(), it.gameSpecificOptions))
                    }
                }
            )
            is Configuration.ViewInvite -> RootComponent.Child.ViewInviteChild(
                DefaultViewInviteComponent(componentContext, configuration.connection)
            )
            else -> throw UnsupportedOperationException("Unknown child for configuration: $configuration")
        }

    private fun homeComponent(componentContext: ComponentContext, connection: ClientConnection): DefaultHomeComponent
        = DefaultHomeComponent(componentContext, connection, InvitationsStoreImpl(mainScope, connection), navigator)

    private fun loginComponent(componentContext: ComponentContext)
        = DefaultLoginComponent(
            componentContext = componentContext,
            httpClient = httpClient,
            localStorage = localStorage,
            context = mainScope.coroutineContext,
            clientConfig = clientConfig,
    ) { connection ->
        // https://arkivanov.github.io/Decompose/getting-started/quick-start/
        println("connection 1")
        platformTools.runOnUiThread {
            navigation.push(Configuration.Home(connection))
        }
        mainScope.launch {
            if (true) return@launch
            connection.messages.collect { message ->
                when (message) {
                    is Message.InvitePrepare -> {
                        val gameTypeDetails = gameTypeStore.getGameType(message.gameType) ?: return@collect
                        navigator.navigateTo(
                            Configuration.CreateInvite(message, gameTypeDetails, connection)
                        )
                    }
                    else -> {}
                }
            }
        }
        println("connection 2")
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
            is RootComponent.Child.CreateInviteChild -> CreateInviteContent(component = child.component)
            is RootComponent.Child.ViewInviteChild -> InviteContent(component = child.component)
            else -> throw UnsupportedOperationException("Unknown child: $child")
        }
    }
}
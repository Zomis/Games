package net.zomis.games.compose.common.gametype

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.Value
import net.zomis.games.compose.common.PlatformTools
import net.zomis.games.compose.common.game.GameClient
import net.zomis.games.compose.common.games.MFE
import net.zomis.games.compose.common.games.NoThanksGameView
import net.zomis.games.compose.common.games.SimpleGridGames
import net.zomis.games.compose.common.games.viewtest.ViewModel2
import net.zomis.games.dsl.flow.VIEWMODEL_VIEW_KEY
import net.zomis.games.impl.NoThanks
import net.zomis.games.impl.minesweeper.ViewModel as MFEViewModel
import net.zomis.games.server2.ServerGames
import kotlin.reflect.KClass

class SupportedGames(private val platformTools: PlatformTools) : GameTypeStore {

    /*
    Game name, composable component, play time, description, screenshot (or generated random state!),
    players count (from server),
    special view types for Log
    some rule examples / rule descriptions
    Link to Board Game Geek

    Other Specials from Vue Client:
    - Set: resetActions = false
    */
    private val games = mutableMapOf<String, GameTypeDetails>()

    override val gameTypes: List<String> get() = games.keys.toList()
    override fun getGameType(gameType: String): GameTypeDetails? = games[gameType]

    init {
        addGame("NoThanks") {
            val view = it.view.subscribeAsState()
            fromViewModel(view.value, NoThanks.ViewModel::class) { result ->
                NoThanksGameView(result, it.gameClient)
            }
        }
        addGame("DSL-TTT") {
            SimpleGridGames.TTT(it.view.subscribeAsState().value, it.gameClient, it.gameClient.playerIndex.subscribeAsState().value)
        }
        addGame("MFE") {
            fromViewModel(it.view.subscribeAsState().value, ViewModel2::class) { result ->
                MFE(result, it.gameClient)
            }
        }
    }

    @Composable
    private fun <T: Any> fromViewModel(value: Any, viewModelClass: KClass<T>, result: @Composable (T) -> Unit) {
        if (value is Unit) return
        if (value is Map<*, *> && value.isEmpty()) return
        if (value is Map<*, *> && value.contains(VIEWMODEL_VIEW_KEY)) {
            val map = value as Map<String, Any>
            val viewModel = platformTools.fromJson(map.getValue(VIEWMODEL_VIEW_KEY), viewModelClass)
            result.invoke(viewModel)
            return
        }
        throw UnsupportedOperationException("Unknown view value for $viewModelClass: $value")
    }

    private fun addGame(gameType: String, component: @Composable (GameViewDetails) -> Unit) {
        games[gameType] = GameTypeDetailsImpl(
            gameType = gameType,
            gameEntryPoint = ServerGames.entrypoint(gameType)!!,
            component = component,
        )
    }

    interface GameViewDetails {
        val view: Value<Any>
        val gameClient: GameClient
    }

    class GameViewDetailsImpl(
        override val view: Value<Any>,
        override val gameClient: GameClient,
    ) : GameViewDetails

}
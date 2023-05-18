package net.zomis.games.compose.common.game

import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message
import net.zomis.games.dsl.impl.LogEntry

class NetworkGameClient(
    startedMessage: Message.GameStarted,
    private val connection: ClientConnection,
    private val scope: CoroutineScope,
    override val playerIndex: Int,
    override val eliminations: Value<PlayerEliminationsRead>,
    override val view: Value<Any>,
    override val logs: Value<List<LogEntry>>,
) : GameClient {
    override val gameType: String = startedMessage.gameType
    private val gameId = startedMessage.gameId

    override suspend fun performAction(actionType: String, serializedParameter: Any) {
        connection.send(ClientToServerMessage.GameActionPerform(gameType, gameId, actionType, serializedParameter))
    }

    override fun postAction(actionType: String, serializedParameter: Any) {
        scope.launch {
            performAction(actionType, serializedParameter)
        }
    }
}
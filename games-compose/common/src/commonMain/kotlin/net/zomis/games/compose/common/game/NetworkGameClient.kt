package net.zomis.games.compose.common.game

import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.Message
import net.zomis.games.dsl.impl.LogEntry

class NetworkGameClient(
    startedMessage: Message.GameStarted,
    connection: ClientConnection,
    scope: CoroutineScope,
    override val playerIndex: Int,
    override val eliminations: Value<PlayerEliminationsRead>,
    override val view: Value<Any>,
    override val logs: Value<List<LogEntry>>,
) : GameClient {
    override val gameType: String = startedMessage.gameType

    override suspend fun performAction(actionType: String, serializedParameter: Any) {
        TODO("Not yet implemented")
    }
}
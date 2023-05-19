package net.zomis.games.compose.common.game

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.compose.common.network.ClientConnection
import net.zomis.games.compose.common.network.ClientToServerMessage
import net.zomis.games.compose.common.network.Message
import net.zomis.games.dsl.impl.LogEntry
import net.zomis.games.server2.invites.PlayerInfo
import net.zomis.games.server2.invites.PlayerInfoId

class NetworkGameClient(
    startedMessage: Message.GameStarted,
    private val connection: ClientConnection,
    private val scope: CoroutineScope,
    override val playerIndex: Value<Int>,
    override val eliminations: Value<PlayerEliminationsRead>,
    override val view: Value<Any>,
    override val logs: Value<List<LogEntry>>,
) : GameClient {
    override val playerCount: Int = startedMessage.players.size
    override val gameType: String = startedMessage.gameType
    private val gameId = startedMessage.gameId
    override val players: Value<List<PlayerInfo>> = MutableValue(startedMessage.players).map { it.map(PlayerInfoId::toPlayerInfo) }

    override suspend fun performAction(actionType: String, serializedParameter: Any) {
        connection.send(ClientToServerMessage.GameActionMove(gameType, gameId, playerIndex.value, actionType, serializedParameter))
    }

    override fun postAction(actionType: String, serializedParameter: Any) {
        scope.launch {
            performAction(actionType, serializedParameter)
        }
    }
}
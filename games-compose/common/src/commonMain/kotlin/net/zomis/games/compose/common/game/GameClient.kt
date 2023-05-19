package net.zomis.games.compose.common.game

import com.arkivanov.decompose.value.Value
import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.dsl.impl.LogEntry
import net.zomis.games.server2.invites.PlayerInfo

interface GameClient {
    val playerIndices: IntRange get() = 0 until playerCount
    val playerCount: Int
    val gameType: String
    val playerIndex: Value<Int>
    val eliminations: Value<PlayerEliminationsRead>
    val view: Value<Any>
    val logs: Value<List<LogEntry>>
    val players: Value<List<PlayerInfo>>
    suspend fun performAction(actionType: String, serializedParameter: Any)
    fun postAction(actionType: String, serializedParameter: Any)
    // fun browseActions(actionType: String, choices: List<Any>): ...
    // actionTypes: Value<List<String>>

//    val actionsInput: Channel<Actionable<T, out Any>> // TODO: This should be some kind of step-by-step approach...

    // for use in compose to unify different "screens"
    // to be used for local play,
    // remote play (communicate with Server using JSON or something else),
    // and possibly also for watching replays

    /*
    get viewModel for player: view, (selected action so far), playerIndex

    surrender (not implemented yet...)
    get permissions for players?
    get eliminated players
    */

}

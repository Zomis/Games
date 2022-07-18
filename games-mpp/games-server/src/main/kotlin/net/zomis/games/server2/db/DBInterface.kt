package net.zomis.games.server2.db

import net.zomis.games.dsl.GameListener
import net.zomis.games.listeners.ReplayListener
import net.zomis.games.server2.ClientLoginEvent
import net.zomis.games.server2.PlayerDatabaseInfo
import net.zomis.games.server2.games.ServerGame

interface DBInterface {
    fun listUnfinished(): Set<DBGameSummary>

    fun loadGame(gameId: String): DBGame?
    fun loadGameIgnoreErrors(gameId: String): DBGame?
    fun gameListener(serverGame: ServerGame, replayListener: ReplayListener): GameListener

    fun cookieAuth(cookie: String): PlayerDatabaseInfo?

    fun authenticate(event: ClientLoginEvent)
}

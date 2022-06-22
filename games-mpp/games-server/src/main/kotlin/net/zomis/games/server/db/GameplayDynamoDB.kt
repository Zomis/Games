package net.zomis.games.server.db

import net.zomis.games.dsl.GameplayCallbacks

@Deprecated("Replaced by GameListeners")
class GameplayDynamoDB<T: Any>(val gameId: String): GameplayCallbacks<T>() {

}

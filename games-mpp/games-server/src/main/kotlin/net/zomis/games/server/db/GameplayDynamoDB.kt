package net.zomis.games.server.db

import net.zomis.games.dsl.GameplayCallbacks

class GameplayDynamoDB<T: Any>(val gameId: String): GameplayCallbacks<T>() {

}

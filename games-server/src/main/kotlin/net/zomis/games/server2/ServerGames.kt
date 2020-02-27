package net.zomis.games.server2

import net.zomis.games.dsl.DslTTT
import net.zomis.games.dsl.DslTTT3D
import net.zomis.games.dsl.DslUR

object ServerGames {

    val games = mutableMapOf<String, Any>(
        "DSL-TTT" to DslTTT().game,
        "DSL-Connect4" to DslTTT().gameConnect4,
        "DSL-UTTT" to DslTTT().gameUTTT,
        "DSL-Reversi" to DslTTT().gameReversi,
        "Quixo" to DslTTT().gameQuixo,
        "Artax" to DslTTT().gameArtax,
        "DSL-TTT3D" to DslTTT3D().game,
        "DSL-UR" to DslUR().gameUR
    )

}

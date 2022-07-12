package net.zomis.games.server2.ais

import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ais.gamescorers.*
import net.zomis.games.server2.games.GameTypeRegisterEvent

class ServerScoringAIs(private val aiRepository: AIRepository) {
    fun setup(events: EventSystem) {
        listOf(
            LiarsDiceScorer.ais(),
            HanabiScorers.ais()
        )
    }

}
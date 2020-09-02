package net.zomis.games.server2.ais

import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ais.gamescorers.*
import net.zomis.games.server2.games.GameTypeRegisterEvent

class ServerScoringAIs(private val aiRepository: AIRepository) {
    fun setup(events: EventSystem) {
        listOf(
            SplendorScorers.ais(),
            SetScorers.ais(),
            AvalonScorers.ais(),
            DungeonMayhemScorers.ais(), SkullScorers.ais(),
            URScorers.ais(),
            ArtaxScorers.ais(),
            LiarsDiceScorer.ais(),
            HanabiScorers.ais()
        ).flatten().groupBy { it.gameType }.forEach { entry ->
            events.listen("Register scoring AIs in ${entry.key}", GameTypeRegisterEvent::class, { it.gameType == entry.key }) {
                entry.value.forEach {factory ->
                    aiRepository.createScoringAI(events, factory)
                }
            }
        }
    }

}
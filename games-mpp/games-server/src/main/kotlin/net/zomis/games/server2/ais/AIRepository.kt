package net.zomis.games.server2.ais

import net.zomis.bestOf
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.GameListenerFactory
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.*
import net.zomis.games.server2.Client
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.ServerGame

object AIRepository {

    fun analyze(gameType: String, game: Game<Any>, aiName: String, playerIndex: Int): AIAnalyzeResult? {
        // Find AI in all AI types
        val setup = ServerGames.setup(gameType) ?: return null

        val scoring = setup.scorerAIs.find { it.name == aiName }
        if (scoring != null) {
            return AIAnalyze().scoring(game, scoring, playerIndex)
        }

        val alphaBetaFactory = ServerAlphaBetaAIs.ais().find { it.gameType == gameType }
        val alphaBetaConfig = alphaBetaFactory?.configurations?.find { alphaBetaFactory.aiName(it.first, it.second) == aiName }?.let {
            AIAlphaBetaConfig(alphaBetaFactory, it.first, it.second)
        }
        if (alphaBetaConfig != null) {
            return AIAnalyze().alphaBeta(game, alphaBetaConfig as AIAlphaBetaConfig<Any>, playerIndex)
        }
        return null
    }

    fun queryableAIs(gameType: String): List<String> {
        val alphaBetaAIs = ServerAlphaBetaAIs.ais().filter { it.gameType == gameType }.flatMap {
            it.configurations.map { conf -> it.aiName(conf.first, conf.second) }
        }
        val scoringAIs = ServerGames.setup(gameType)?.scorerAIs?.map { it.name } ?: emptyList()
        return scoringAIs.sorted() + alphaBetaAIs.sorted()
    }

    fun createAIs(events: EventSystem, games: Collection<GameSpec<Any>>) {
        val setups = games.map { GamesImpl.game(it).setup() }
        createRandomAI(setups, events)
        createScoringAIs(setups, events)
        createOtherAIs(setups, events)
        createAlphaBetaAIs(events)
    }

    private fun createRandomAI(setups: List<GameSetupImpl<Any>>, events: EventSystem) {
        val randomAI = GameAI<Any>("#AI_Random") {
            action {
                GameAIs.randomActionable(game, playerIndex)
            }
        }
        val randomAIs = setups.filter { it.useRandomAI }.associate { it.gameType to randomAI }
        ServerAI(randomAIs.keys.toList(), "#AI_Random", randomAIs.gameListenerFactory()).register(events)
    }

    private fun createScoringAIs(setups: List<GameSetupImpl<Any>>, events: EventSystem) {
        // Take all AIs, group by name. Then group by gameType
        setups.flatMap { it.scorerAIs }.groupBy { it.name }.forEach { (name, list) ->
            val gameTypes = list.associate { it.gameType to GameAI<Any>(name) {
                val controller = it.createController()
                action {
                    controller.invoke(this)
                }
            } }
            ServerAI(gameTypes.keys.toList(), name, gameTypes.gameListenerFactory()).register(events)
        }
    }
    private fun createOtherAIs(setups: List<GameSetupImpl<Any>>, events: EventSystem) {
        data class OtherAI(val gameType: String, val name: String, val controller: GameAI<Any>)
        val otherAIs = setups.flatMap { setup ->
            setup.otherAIs.map { OtherAI(setup.gameType, it.name, it) }
        }
        otherAIs.groupBy { it.name }.forEach { (name, list) ->
            val gameTypes = list.associate { it.gameType to it.controller }

            ServerAI(gameTypes.keys.toList(), name, gameTypes.gameListenerFactory()).register(events)
        }
    }
    private fun createAlphaBetaAIs(events: EventSystem) {
        val alphaBetas = ServerAlphaBetaAIs.ais()
        alphaBetas.flatMap { it.names }.distinct().forEach { name ->
            val gameTypes = alphaBetas.filter { it.names.contains(name) }.associate { abAI -> abAI.gameType to GameAI<Any>(name) {
                val factory = abAI as AlphaBetaAIFactory<Any>
                val configuration = factory.configurations.first { factory.aiName(it.first, it.second) == name }
                val alphaBetaConfig = AIAlphaBetaConfig(factory, configuration.first, configuration.second)
                action {
                    val options = alphaBetaConfig.evaluateActions(game, playerIndex)
                    val move = options.bestOf { it.second }.random()
                    move.first
                }
            }
            }
            ServerAI(gameTypes.keys.toList(), name, gameTypes.gameListenerFactory()).register(events)
        }
    }

}

private fun Map<String, GameAI<Any>>.gameListenerFactory(): GameListenerFactory {
    return GameListenerFactory { game, playerIndex ->
        this[game.gameType]?.gameListener(game, playerIndex) ?: throw IllegalArgumentException("${game.gameType} is not in ${this.keys}")
    }
}

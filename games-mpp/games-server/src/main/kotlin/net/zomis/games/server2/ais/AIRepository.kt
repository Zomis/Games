package net.zomis.games.server2.ais

import net.zomis.bestOf
import net.zomis.core.events.EventSystem
import net.zomis.games.ais.noAvailableActions
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.scorers.ScorerController
import net.zomis.games.server2.Client
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.games.ServerGame

interface ServerGameAIScope {
    val serverGame: ServerGame
    val playerIndex: Int
    val client: Client
}
class ServerGameAIContext(
    override val serverGame: ServerGame,
    override val playerIndex: Int,
    override val client: Client
): ServerGameAIScope
typealias ServerGameAI = ServerGameAIScope.() -> PlayerGameMoveRequest?

class AIRepository {

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
        val randomAIs = setups.filter { it.useRandomAI }
        ServerAI(randomAIs.map { it.gameType }, "#AI_Random", { _, _ -> null }) {
            ServerAIs.randomAction(serverGame, client, playerIndex)
        }.register(events)
    }

    private fun createScoringAIs(setups: List<GameSetupImpl<Any>>, events: EventSystem) {
        // Take all AIs, group by name. Then group by gameType
        setups.flatMap { it.scorerAIs }.groupBy { it.name }.forEach { (name, list) ->
            val gameTypes = list.associate { it.gameType to it.createController() }

            ServerAI(gameTypes.keys.toList(), name, listenerFactory = { _, _ -> null }) {
                val obj = serverGame.obj!!
                val controllerContext = GameControllerContext(obj, playerIndex)
                val action = gameTypes.getValue(serverGame.gameType.type).invoke(controllerContext)
                if (action != null) PlayerGameMoveRequest(client, serverGame, playerIndex, action.actionType, action.parameter, false)
                else null
            }.register(events)
        }
    }
    private fun createOtherAIs(setups: List<GameSetupImpl<Any>>, events: EventSystem) {
        data class OtherAI(val gameType: String, val name: String, val controller: GameController<Any>)
        val otherAIs = setups.flatMap { setup ->
            setup.otherAIs.map { OtherAI(setup.gameType, it.first, it.second) }
        }
        otherAIs.groupBy { it.name }.forEach { (name, list) ->
            val gameTypes = list.associate { it.gameType to it.controller }

            ServerAI(gameTypes.keys.toList(), name, { _, _ -> null }) {
                val obj = serverGame.obj!!
                val controllerContext = GameControllerContext(obj, playerIndex)
                val action =  gameTypes.getValue(serverGame.gameType.type).invoke(controllerContext)
                if (action != null) PlayerGameMoveRequest(client, serverGame, playerIndex, action.actionType, action.parameter, false)
                else null
            }.register(events)
        }
    }
    private fun createAlphaBetaAIs(events: EventSystem) {
        val alphaBetas = ServerAlphaBetaAIs.ais()
        alphaBetas.flatMap { it.names }.distinct().forEach { name ->
            val gameTypes = alphaBetas.filter { it.names.contains(name) }.map { it.gameType }
            ServerAI(gameTypes, name, { _, _ -> null }) {
                val factory = alphaBetas.first { it.gameType == serverGame.gameType.type } as AlphaBetaAIFactory<Any>
                val configuration = factory.configurations.first { factory.aiName(it.first, it.second) == name }
                val alphaBetaConfig = AIAlphaBetaConfig(factory, configuration.first, configuration.second)
                val model = serverGame.obj!!
                if (noAvailableActions(model, playerIndex)) {
                    return@ServerAI null
                }

                val options = alphaBetaConfig.evaluateActions(model, playerIndex)
                val move = options.bestOf { it.second }.random()
                return@ServerAI PlayerGameMoveRequest(client, serverGame, playerIndex, move.first.actionType, move.first.parameter, false)
            }.register(events)
        }
    }

}

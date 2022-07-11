package net.zomis.games.server2.ais

import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.GameSpec
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.scorers.ScorerController
import net.zomis.games.server2.Client
import net.zomis.games.server2.games.GameTypeRegisterEvent
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

    private class AIRepositoryForGame<T: Any> {
        val scoringAIs = mutableMapOf<String, ScorerController<T>>()
        val alphaBetaAIs = mutableMapOf<String, AlphaBetaAIFactory<T>>()
        val otherAIs = mutableMapOf<String, ServerGameAI>()
    }
    private val gameTypeAIs = mutableMapOf<String, AIRepositoryForGame<Any>>()

    private fun <T: Any> repositoryForGameType(gameType: String): AIRepositoryForGame<T> {
        gameTypeAIs.computeIfAbsent(gameType) { AIRepositoryForGame() }
        return gameTypeAIs[gameType]!! as AIRepositoryForGame<T>
    }

    fun <T: Any> createScoringAI(events: EventSystem, factory: ScorerController<T>) {
        val repo = repositoryForGameType<T>(factory.gameType)
        repo.scoringAIs[factory.name] = factory
        val scoringFactory = AIFactoryScoring()
        scoringFactory.createAI(events, factory.gameType, factory.name, factory.createController())
    }

    private fun <T: Any> alphaBetaConfigurations(factory: AlphaBetaAIFactory<T>): List<Pair<Int, AlphaBetaSpeedMode>> {
        return (0 until factory.maxLevel).map {level ->
            level to AlphaBetaSpeedMode.NORMAL
        }.let {
            if (factory.useSpeedModes) {
                it.plus(factory.maxLevel to AlphaBetaSpeedMode.QUICK)
                  .plus(factory.maxLevel to AlphaBetaSpeedMode.SLOW)
            } else {
                it.plus(factory.maxLevel to AlphaBetaSpeedMode.NORMAL)
            }
        }
    }

    fun <T: Any> createAlphaBetaAIs(events: EventSystem, factory: AlphaBetaAIFactory<T>) {
        val repo = repositoryForGameType<T>(factory.gameType)
        repo.alphaBetaAIs[factory.namePrefix] = factory
        val abFactory = AIFactoryAlphaBeta()
        alphaBetaConfigurations(factory).forEach {
            abFactory.createAlphaBetaAI(factory, events, it.first, it.second)
        }
    }

    fun analyze(gameType: String, game: Game<Any>, aiName: String, playerIndex: Int): AIAnalyzeResult? {
        // Find AI in all AI types
        val gameTypeRepo = repositoryForGameType<Any>(gameType)
        val scoring = gameTypeRepo.scoringAIs[aiName]
        if (scoring != null) {
            return AIAnalyze().scoring(game, scoring, playerIndex)
        }

        val alphaBetaConfig = gameTypeRepo.alphaBetaAIs.entries.mapNotNull {entry ->
            val factory = entry.value
            val configs = alphaBetaConfigurations(factory)
            val config = configs.find { factory.aiName(it.first, it.second) == aiName }
            if (config != null) {
                AIAlphaBetaConfig(factory, config.first, config.second)
            } else null
        }.firstOrNull()
        if (alphaBetaConfig != null) {
            return AIAnalyze().alphaBeta(game, alphaBetaConfig, playerIndex)
        }
        return null
    }

    fun queryableAIs(gameType: String): List<String> {
        val gameTypeRepo = repositoryForGameType<Any>(gameType)
        val abNames = gameTypeRepo.alphaBetaAIs.flatMap {factory ->
            val abConfig = alphaBetaConfigurations(factory.value)
            abConfig.map { factory.value.aiName(it.first, it.second) }
        }
        return gameTypeRepo.scoringAIs.keys.sorted() + abNames
    }

    fun createAIs(events: EventSystem, games: Collection<GameSpec<Any>>) {
        val setups = games.map { GamesImpl.game(it).setup() }

        val randomAIs = setups.filter { it.useRandomAI }
        ServerAI(randomAIs.map { it.gameType }, "#AI_Random", { _, _ -> null }) {
            ServerAIs.randomAction(serverGame, client, playerIndex)
        }.register(events)

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

}

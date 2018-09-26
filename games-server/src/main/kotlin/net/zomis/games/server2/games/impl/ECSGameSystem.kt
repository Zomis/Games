package net.zomis.games.server2.games.impl

import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.core.*
import net.zomis.games.ecs.ActiveBoard
import net.zomis.games.ecs.Parent
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.StartupEvent
import net.zomis.games.server2.games.GameStartedEvent
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.GameTypeRegisterEvent
import net.zomis.games.server2.games.IllegalMoveEvent
import net.zomis.games.server2.getTextOrDefault
import kotlin.reflect.KClass

data class ECSActionRequest(val game: net.zomis.games.server2.games.Game, val player: Int, val moveType: String, val move: Any) {
    fun illegalMove(reason: String): IllegalMoveEvent {
        return IllegalMoveEvent(game, player, moveType, move, reason)
    }
}

data class ECSGameStartedEvent(val game: Game)

class ECSGameSystem(private val gameSystem: GameSystem, val gameType: String, private val factory: () -> Game) {
    private val logger = KLoggers.logger(this)

    fun register(events: EventSystem) {
        events.listen("start ECS Game $gameType", GameStartedEvent::class, {
            it.game.gameType.type == gameType
        }, {gameStartedEvent ->
            val game = factory.invoke()
            gameStartedEvent.game.obj = game
            game.execute(ECSGameStartedEvent(game))

            sendFullData(game, gameStartedEvent.game)

            game.system {
                it.listen("component update in $gameType", UpdateEntityEvent::class, {true}, {updateEvent ->
                    sendComponentData(gameStartedEvent.game, updateEvent.entity, updateEvent.componentClass, updateEvent.value)
                })
            }
        })
        events.listen("move in $gameType", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "action" &&
                it.data.get("game").asText() == gameType && it.data.has("game")
        }, {
            val gameId = it.data.get("gameId").asText()

            val serverGame = gameSystem.gameTypes[gameType]?.runningGames?.get(gameId) ?:
                throw IllegalArgumentException("No such game: $gameId in gameType $gameType")
            val playerIndex = serverGame.players.indexOf(it.client)
            val game = serverGame.obj as Game
            val players = game.core.component(Players::class)
            val expectedId = players.players[playerIndex].id
            val actualId = it.data.get("performer").asText()
            if (expectedId != actualId) {
                throw IllegalArgumentException("Unexpected performer: $actualId expected $expectedId")
            }

            val actionableId = it.data.get("action").asText()
            val actionable = game.entityById(actionableId) ?:
                throw IllegalArgumentException("Unknown entity: $actionableId")
            game.execute(ActionEvent(actionable, players.players[playerIndex], true))

//            TODO("Inform all players about the fact that this action has been performed")
        })
        events.listen("register $gameType", StartupEvent::class, {true}, {
            events.execute(GameTypeRegisterEvent(gameType))
        })
    }

    private fun sendComponentData(serverGame: net.zomis.games.server2.games.Game, entity: Entity,
          componentClass: KClass<*>, value: Component) {
        val playersComponent = entity.game.core.componentOrNull(Players::class)
        val data: Pair<String, Any?>? = if (playersComponent == null) componentData(value, null) else null
        serverGame.broadcast {client ->
            if (data != null) {
                return@broadcast wrapComponentData(entity, data)
            }
            // Is there a Players component or not?
            val index = serverGame.players.indexOf(client)
            val playerEntity = playersComponent!!.players[index]
            return@broadcast wrapComponentData(entity, componentData(value, playerEntity))
        }
    }

    private fun wrapComponentData(entity: Entity, component: Pair<String, Any?>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["type"] = "Update"
        result["id"] = entity.id
        result["component"] = mapOf("type" to component.first, "value" to component.second)
        return result
    }

    private fun sendFullData(game: Game, serverGame: net.zomis.games.server2.games.Game) {
        val playersComponent = game.core.componentOrNull(Players::class)
        val data: Any? = if (playersComponent == null) constructECSData(game, null) else null
        serverGame.broadcast {client ->
            if (data != null) {
                return@broadcast data
            }
            // Is there a Players component or not?
            val index = serverGame.players.indexOf(client)
            val playerEntity = playersComponent!!.players[index]
            return@broadcast constructECSData(game, playerEntity)
        }
    }

    private fun constructECSData(game: Game, viewer: Entity?): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        data["type"] = "GameData"
        data["game"] = entityData(game.core, viewer)
        return data
    }

    private fun entityData(entity: Entity, viewer: Entity?): Any {
        val data = mutableMapOf<String, Any?>()
        data["id"] = entity.id
        for (component in entity.components()) {
            val componentData = componentData(component, viewer)
            data[componentData.first] = componentData.second
        }
        return data
    }

    private fun componentData(component: Component, viewer: Entity?): Pair<String, Any?> {
        return when (component) {
            is Actionable -> "actionable" to true
            is Tile -> "tile" to component
            is Parent -> "parent" to component.parent.id
            is Players -> "players" to component.players.map { entityData(it, viewer) }
            is Container2D -> "grid" to component.container.map {row ->
                row.map {entity ->
                    entityData(entity, viewer)
                }
            }
            is Player -> "player" to mapOf(
                "index" to component.index,
                "position" to component.resultPosition,
                "winner" to component.winner
            )
            is OwnedByPlayer -> "owner" to component.owner?.index
            is PlayerTurn -> "currentPlayer" to component.currentPlayer.index
            is ActiveBoard -> "activeBoard" to if (component.active != null) component.active!! else null
            else -> {
                throw IllegalArgumentException("No serialization setup for component of type ${component::class}: $component")
            }
        }
    }

}
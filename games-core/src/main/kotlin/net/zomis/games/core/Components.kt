package net.zomis.games.core

open class Component
open class FixedComponent: Component()
open class DataComponent: Component()
open class ContainerComponent: Component()
open class DynamicComponent: Component()

open class LogicComponent: Component()
class Targetable: LogicComponent()
class Actionable: LogicComponent()
data class ActionEvent(val actionable: Entity, val initiatedBy: Entity, var allowed: Boolean)

//import kotlin.reflect.KClass
//data class LimitedVisibility(val componentClass: KClass<*>, val sees: (Entity) -> Any): LogicComponent()
data class EntityComponent<out T: Component>(val entity: Entity, val component: T)


data class Tile(val x: Int, val y: Int): FixedComponent()
data class Container2D(val container: List<List<Entity>>): ContainerComponent()

enum class WinStatus { WIN, LOSS, DRAW }
data class Player(val index: Int, var result: WinStatus?, var resultPosition: Int?): DataComponent() {
    val eliminated: Boolean
        get() = result != null
}
data class OwnedByPlayer(var owner: Player?): DataComponent()
data class Players(val players: List<Entity>): ContainerComponent() {
    fun eliminate(index: Int, result: WinStatus, position: Int): Players {
        players[index].updateComponent(Player::class) {
            it.result = result
            it.resultPosition = position
        }
        return this
    }

    fun getResultPosition(result: WinStatus): Int {
        // if no one else has been eliminated, the player is at 1st place. Because the player itself has not been eliminated, it should get increased below.
        var playerResultPosition = players.size + 1
        if (result != WinStatus.LOSS) {
            playerResultPosition = 0
        }

        var posTaken: Boolean
        do {
            playerResultPosition += if (result == WinStatus.LOSS) -1 else +1
            posTaken = this.players.asSequence().map { it.component(Player::class) }.any { it.eliminated && it.resultPosition == playerResultPosition }
        } while (posTaken)
        return playerResultPosition
    }

    fun eliminate(index: Int, result: WinStatus): Players {
		return this.eliminate(index, result, getResultPosition(result))
    }

    fun eliminateRemaining(result: WinStatus): Players {
        val position = getResultPosition(result)
        players.forEachIndexed {index, player ->
            if (!player.component(Player::class).eliminated) {
                eliminate(index, result, position)
            }
        }
        return this
    }
}
data class PlayerTurn(var currentPlayer: Player): DataComponent()


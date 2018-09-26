package net.zomis.games.ecs

import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.core.*

data class ActiveBoard(var active: Tile?): DataComponent()
data class Parent(val parent: Entity): FixedComponent()

private val Entity.activeBoard: Tile?
    get() = component(ActiveBoard::class).active

class UTTT {

    private val logger = KLoggers.logger(this)

    fun setup(): Game {
        val game = Game()

        val players = (0..1).map { Player(it, null, null) }.map {
            return@map game.createEntity().add(it)
        }
        val boards = (0..2).map {y ->
            (0..2).map {x ->
                val area = game.createEntity()
                area.add(Tile(x, y)).add(OwnedByPlayer(null)).add(createTiles(area))
                area
            }
        }

        game.core.add(Players(players))
            .add(PlayerTurn(players[0].component(Player::class)))
            .add(Container2D(boards))
            .add(ActiveBoard(null))
        game.system(this::actionableClick)

        return game
    }

    fun actionableClick(events: EventSystem) {
        events.listen("click", ActionEvent::class, {
                true
        }, {
            val core = it.actionable.game.core
            val tile = it.actionable
            val ownedByPlayer = tile.component(OwnedByPlayer::class)
            val played = tile.component(OwnedByPlayer::class).owner != null
            val activeTile = core.component(ActiveBoard::class).active
            if (ownedByPlayer.owner != null) {
                logger.info("Move not allowed because already owned: $tile")
                return@listen
            }
            val parent = tile.component(Parent::class).parent.component(Tile::class)
            if (activeTile != null && parent != activeTile) {
                logger.info("Wrong parent. Active is $activeTile but parent is $parent")
                return@listen
            }

            val currentPlayer = core.component(PlayerTurn::class).currentPlayer
            val correctPlayer = it.initiatedBy.game.core.component(PlayerTurn::class).currentPlayer == currentPlayer
            val allowed = !played && correctPlayer
            if (!allowed) {
                logger.info("Move not allowed: played $played currectPlayer $correctPlayer")
                return@listen
            }

            it.actionable.updateComponent(OwnedByPlayer::class) {component ->
                component.owner = it.initiatedBy.component(Player::class)
            }

            val destination = it.actionable.component(Tile::class)
            val players = it.actionable.game.core.component(Players::class).players
            it.actionable.game.core.updateComponent(PlayerTurn::class) {playerTurn ->
                playerTurn.currentPlayer = players[(playerTurn.currentPlayer.index + 1) % players.size].component(Player::class)
            }
            it.actionable.game.core.updateComponent(ActiveBoard::class) {activeBoard ->
                activeBoard.active = destination
            }
        })
    }

    private fun createTiles(parent: Entity): Component {
        return Container2D((0..2).map {y ->
            (0..2).map {x ->
                parent.game.createEntity()
                .add(Tile(x, y))
                .add(OwnedByPlayer(null))
                .add(Parent(parent))
                .add(Actionable())
            }
        })
    }

}
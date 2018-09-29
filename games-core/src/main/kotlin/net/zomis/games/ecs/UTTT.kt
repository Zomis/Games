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
            .add(OwnedByPlayer(null))
            .add(PlayerTurn(players[0].component(Player::class)))
            .add(Container2D(boards))
            .add(ActiveBoard(null))
        game.system(this::actionableClick)

        return game
    }

    fun actionableClick(events: EventSystem) {
        events.listen("click allowed check", ActionAllowedCheck::class, {true}, {
            val core = it.actionable.game.core
            val tile = it.actionable
            val ownedByPlayer = tile.component(OwnedByPlayer::class)
            val played = tile.component(OwnedByPlayer::class).owner != null
            val activeTile = core.component(ActiveBoard::class).active
            if (ownedByPlayer.owner != null) {
                return@listen it.deny("Move not allowed because already owned: $tile")
            }
            val parent = tile.component(Parent::class).parent.component(Tile::class)
            if (activeTile != null && parent != activeTile) {
                return@listen it.deny("Wrong parent. Active is $activeTile but parent is $parent")
            }

            val currentPlayer = core.component(PlayerTurn::class).currentPlayer
            val correctPlayer = it.initiatedBy.game.core.component(PlayerTurn::class).currentPlayer == currentPlayer
            val allowed = !played && correctPlayer
            if (!allowed) {
                return@listen it.deny("Move not allowed: played $played currectPlayer $correctPlayer")
            }
        })

        events.listen("click", ActionEvent::class, {
                true
        }, {
            val core = it.actionable.game.core
            it.actionable.updateComponent(OwnedByPlayer::class) {component ->
                component.owner = it.initiatedBy.component(Player::class)
            }

            val destination = it.actionable.component(Tile::class)
            val players = it.actionable.game.core.component(Players::class).players
            core.updateComponent(PlayerTurn::class) {playerTurn ->
                playerTurn.currentPlayer = players[(playerTurn.currentPlayer.index + 1) % players.size].component(Player::class)
            }
            core.updateComponent(ActiveBoard::class) {activeBoard ->
                val target = it.actionable.game.core.component(Container2D::class).container[destination.y][destination.x]
                activeBoard.active = if (target.component(OwnedByPlayer::class).owner != null) null else destination
            }
            checkWinner(it.actionable.component(Parent::class).parent)
            val winner = checkWinner(it.actionable.game.core)
            if (winner != null) {
                core.component(Players::class).eliminate(winner.index, WinStatus.WIN).eliminateRemaining(WinStatus.LOSS)
            }
        })
    }

    private fun checkWinner(entity: Entity): Player? {
        val grid = entity.component(Container2D::class).container

        val range = (0..2)

        for (xx in range) {
            val horizontal = checkWins(range.map { grid[it][xx].component(OwnedByPlayer::class).owner })
            if (horizontal != null) {
                return setWinner(entity, horizontal)
            }

            val vertical = checkWins(range.map { grid[xx][it].component(OwnedByPlayer::class).owner })
            if (vertical != null) {
                return setWinner(entity, vertical)
            }
        }

        val diagonalOne = checkWins(listOf(grid[0][0], grid[1][1], grid[2][2]).map { it.component(OwnedByPlayer::class).owner })
        if (diagonalOne != null) {
            return setWinner(entity, diagonalOne)
        }
        val diagonalTwo = checkWins(listOf(grid[0][2], grid[1][1], grid[2][0]).map { it.component(OwnedByPlayer::class).owner })
        if (diagonalTwo != null) {
            return setWinner(entity, diagonalTwo)
        }
        return null
    }

    private fun setWinner(entity: Entity, player: Player): Player? {
        return entity.updateComponent(OwnedByPlayer::class) {
            it.owner = player
        }.owner
    }

    private fun checkWins(map: Iterable<Player?>): Player? {
        val distinct = map.distinct()
        if (distinct.size != 1 || distinct.firstOrNull() == null) {
            return null
        }
        return map.first()
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
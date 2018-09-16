package net.zomis.games.ecs

import net.zomis.core.events.EventSystem
import net.zomis.games.core.*

class UTTT {

    fun setup(): Game {
        val game = Game()

        val players = (0..1).map { Player(it, null, null) }.map {
            return@map Entity().add(it)
        }
        game.core.add(Players(players))
        game.core.add(PlayerTurn(players[0].component(Player::class)))

        val boards = (0..2).map {x ->
            (0..2).map {y -> Entity().add(Tile(x, y)).add(OwnedByPlayer(null)).add(createTiles()) }
        }

        game.core.add(Container2D(boards))
        game.system(this::actionableClick)

        return game
    }

    fun actionableClick(p1: EventSystem) {
        p1.listen("click", ActionEvent::class, {true}, {
        })
    }

    private fun createTiles(): Component {
        return Container2D((0..2).map {y ->
            (0..2).map {x ->
                Entity()
                .add(Tile(x, y))
                .add(OwnedByPlayer(null))
                .add(Actionable())}
        })
    }

}
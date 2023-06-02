package net.zomis.games.impl.minesweeper

import net.zomis.games.dsl.ReplayStateI

object Setup {

    fun generate(game: Flags.Model, replayable: ReplayStateI, mineCount: Int) {
        game.grid.all().forEach {
            it.value.clear()
        }
        val mines = replayable.randomFromList("mines", game.grid.all().map { it.value }.toList(), mineCount) { it.toStateString() }
        mines.forEach {
            it.mineValue = 1
        }
        game.recount()
    }

}
package net.zomis.games.api

import net.zomis.games.cards.CardZone
import net.zomis.games.components.ExpandableGrid
import net.zomis.games.components.Grid
import net.zomis.games.components.GridImpl

val Games.components get() = GamesComponents

object GamesComponents {

    fun <T> grid(x: Int, y: Int, init: (x: Int, y: Int) -> T): Grid<T> = GridImpl(x, y, init)
    fun <T> expandableGrid(chunkSize: Int = 16): ExpandableGrid<T>
            = ExpandableGrid(chunkSize = chunkSize)

    fun <T> cardZone(list: MutableList<T> = mutableListOf()): CardZone<T> {
        return CardZone(list)
    }

}

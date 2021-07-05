package net.zomis.games.api

import net.zomis.games.Map2DX
import net.zomis.games.cards.CardZone

object Games {

    val api = GamesApi
    val components = GamesComponents

}

object GamesComponents {

    fun <T> grid(x: Int, y: Int, init: (x: Int, y: Int) -> T): Map2DX<T> {
        return Map2DX(x, y, init)
    }

    fun <T> cardZone(list: MutableList<T> = mutableListOf()): CardZone<T> {
        return CardZone(list)
    }

}
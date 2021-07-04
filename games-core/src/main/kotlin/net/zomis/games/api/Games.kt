package net.zomis.games.api

import net.zomis.games.Map2DX

object Games {

    val api = GamesApi
    val components = GamesComponents

}

object GamesComponents {

    fun <T> grid(x: Int, y: Int, init: (x: Int, y: Int) -> T): Map2DX<T> {
        return Map2DX(x, y, init)
    }

}
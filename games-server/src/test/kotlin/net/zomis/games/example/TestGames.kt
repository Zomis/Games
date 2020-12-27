package net.zomis.games.example

import net.zomis.games.api.GamesApi
import net.zomis.games.dsl.GameSpec

class TestGameModel

object TestGames {
    val factory = GamesApi.gameCreator(TestGameModel::class)

    val testGameType = factory.game("TestGameType") {}
    val otherGameType = factory.game("OtherGameType") {}
    val gameTypeA = factory.game("A") {}
    val gameTypeB = factory.game("B") {}

    fun gameType(name: String): GameSpec<Any> = factory.game(name) {} as GameSpec<Any>
}

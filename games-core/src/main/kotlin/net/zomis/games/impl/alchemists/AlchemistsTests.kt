package net.zomis.games.impl.alchemists

import net.zomis.games.dsl.GameDsl

object AlchemistsTests {
    fun tests(dsl: GameDsl<AlchemistsModel>) {
        dsl.testCase(2) {
            state("startingPlayer", 1)
            TODO("make turn order action")
        }
    }

}

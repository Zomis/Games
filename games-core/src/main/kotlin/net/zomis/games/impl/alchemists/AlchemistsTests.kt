package net.zomis.games.impl.alchemists

import net.zomis.games.dsl.GameDsl

object AlchemistsTests {
    fun tests(dsl: GameDsl<AlchemistsModel>) {
        dsl.testCase(2) {
            state("startingPlayer", 1)
            initializeGame()
            action(1, AlchemistsGame.turnOrder, game.turnOrderPlacements.spaces[2].zone)
            actionNotAllowed(0, AlchemistsGame.turnOrder, game.turnOrderPlacements.spaces[2].zone)
            actionNotAllowed(0, AlchemistsGame.turnOrder, game.turnOrderPlacements.spaces.last().zone)
            action(0, AlchemistsGame.turnOrder, game.turnOrderPlacements.spaces[3].zone)
        }
    }

}

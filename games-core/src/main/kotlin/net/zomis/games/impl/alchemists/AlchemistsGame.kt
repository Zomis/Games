package net.zomis.games.impl.alchemists

import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone

class AlchemistsModel(playerCount: Int) {

    class Player(val playerIndex: Int) {
        val ingredients = CardZone<Alchemists.Ingredient>()
    }
    val solution = Alchemists.alchemyValues.shuffled().zip(Alchemists.Ingredient.values())
    val players = (0 until playerCount).map { Player(it) }

}

object AlchemistsGame {

    val factory = GamesApi.gameCreator(AlchemistsModel::class)

    val game = factory.game("Alchemists") {
        setup {
            players(2..4)
            init { AlchemistsModel(playerCount) }
        }
        gameFlow {

        }
        gameFlowRules {

        }
    }
}

package net.zomis.games.dsl.games

import net.zomis.games.dsl.GameTest
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.impl.SkullCard
import net.zomis.games.impl.SkullGame
import net.zomis.games.impl.SkullGameModel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SkullTest {

    val dsl = SkullGame.game
    lateinit var game: GameImpl<SkullGameModel>
    lateinit var test: GameTest<SkullGameModel>

    @BeforeEach
    fun setup() {
        val setup = GameSetupImpl(dsl)
        game = setup.createGame(3, setup.getDefaultConfig())
        test = GameTest(game)
    }

    @Test
    fun startingPosition() {
        test.expectPossibleActions(1, 0)
        test.expectPossibleActions(2, 0)

        val actions = test.expectPossibleActions(0, 4)
        Assertions.assertEquals(1, actions.count { it.parameter == SkullCard.SKULL })
        Assertions.assertEquals(3, actions.count { it.parameter == SkullCard.FLOWER })
        Assertions.assertTrue(game.model.players.all { it.points == 0 })

        test.performAction(0, "play", SkullCard.FLOWER)
        test.expectPossibleActions(2, 0)
        Assertions.assertEquals(1, game.model.currentPlayerIndex)
        test.performAction(1, "play", SkullCard.FLOWER)
        Assertions.assertEquals(2, game.model.currentPlayerIndex)
        test.performAction(2, "play", SkullCard.FLOWER)
        test.expectPossibleActions(0, 1 + 2 + 3) // Play 1 skull, play 2 flowers, 3 different bets
        test.performAction(0, "bet", 1)
        test.expectPossibleActions(1, 2 + 1) // bet 2, bet 3, pass
        test.performAction(1, "bet", 2)
        test.expectPossibleActions(2, 1 + 1) // bet 3, pass
        test.performAction(2, "pass", 1)
        test.expectPossibleActions(0, 1 + 1) // bet 3, pass
        test.performAction(0, "bet", 3)

        Assertions.assertEquals(0, game.model.currentPlayerIndex)
        test.expectPossibleActions(0, 1) // choose self
        test.performAction(0, "choose", game.model.players[0])
        Assertions.assertEquals(0, game.model.currentPlayerIndex)
        test.expectPossibleActions(0, 2) // choose another player
        test.performAction(0, "choose", game.model.players[2])
        test.expectPossibleActions(0, 1) // choose last player
        Assertions.assertTrue(game.model.players.all { it.points == 0 })
        test.performAction(0, "choose", game.model.players[1])

        Assertions.assertEquals(1, game.model.players[0].points)
        Assertions.assertEquals(0, game.model.currentPlayerIndex)


        test.performAction(0, "play", SkullCard.FLOWER)
        test.performAction(1, "play", SkullCard.SKULL)
        test.performAction(2, "play", SkullCard.SKULL)
        test.performAction(0, "bet", 3)
        test.performAction(0, "choose", game.model.players[0])
        test.performAction(0, "choose", game.model.players[1])
        Assertions.assertEquals(3, game.model.players[0].totalCards)
        // With the default options, currentPlayer should not change if you lose a bet.
        Assertions.assertEquals(0, game.model.currentPlayerIndex)
    }

}
package net.zomis.games.server2.scorers

import net.zomis.games.dsl.Action
import net.zomis.games.dsl.GameCreator
import net.zomis.games.scorers.Scorer
import net.zomis.games.scorers.ScorerContext
import net.zomis.games.scorers.ScorerFactory
import net.zomis.games.scorers.ScorerScope
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ScorerGame(val value: String)

class ScorerTest {

    val factory = GameCreator(ScorerGame::class)
    val gameDsl = factory.game("ScorerGame") {
        // Unused in this test (so far)
    }
    val theGame = ScorerGame("Hello World!")
    val testFactory = ScorerFactory<ScorerGame>(gameDsl)
    val actionType = factory.action("Remove", String::class)
    val theAction = Action(theGame, 0, "Remove", "ae")

    fun <T: Any> assertScore(scorer: Scorer<ScorerGame, T>, expected: Double) {
        val scope = ScorerContext(theGame, 0, theAction as Action<ScorerGame, Any>, mutableMapOf())
        val actual = scorer.score(scope as ScorerScope<ScorerGame, T>)
        Assertions.assertEquals(expected, actual ?: 0.0, 0.000001)
    }

    @Test
    fun simpleScorer() {
        val parameterLength = testFactory.action(actionType) { action.parameter.length.toDouble() }
        assertScore(parameterLength, 2.0)
    }

    @Test
    fun conditionalScorer() {
        val containsE = testFactory.actionConditional(actionType) { action.parameter.contains("e") }
        assertScore(containsE, 1.0)
    }

    @Test
    fun isAction() {
        val isAction = testFactory.isAction(actionType)
        assertScore(isAction, 1.0)
    }



}
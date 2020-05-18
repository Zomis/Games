package net.zomis.games.server2.scorers

import net.zomis.games.dsl.Action
import net.zomis.games.server2.ais.scorers.Scorer
import net.zomis.games.server2.ais.scorers.ScorerContext
import net.zomis.games.server2.ais.scorers.ScorerFactory
import net.zomis.games.server2.ais.scorers.ScorerScope
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ScorerGame(val value: String)

class ScorerTest {

    val theGame = ScorerGame("Hello World!")
    val testFactory = ScorerFactory<ScorerGame>()
    val theAction = Action<ScorerGame, Any>(theGame, 0, "Remove", "ae")

    fun <T: Any> assertScore(scorer: Scorer<ScorerGame, T>, expected: Double) {
        val scope = ScorerContext(theGame, 0, theAction, mutableMapOf())
        val actual = scorer.score(scope as ScorerScope<ScorerGame, T>)
        Assertions.assertEquals(expected, actual ?: 0.0, 0.000001)
    }

    @Test
    fun simpleScorer() {
        val parameterLength = testFactory.simple { (action.parameter as String).length.toDouble() }
        assertScore(parameterLength, 2.0)
    }

    @Test
    fun conditionalScorer() {
        val containsE = testFactory.conditional { (action.parameter as String).contains("e") }
        assertScore(containsE, 1.0)
    }

    @Test
    fun conditionalType() {
        val containsE = testFactory.conditionalType(String::class) { action.parameter.count { it == 'e' }.toDouble() }
        assertScore(containsE, 1.0)
    }



}
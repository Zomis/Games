package net.zomis.games.scorers

import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameSpec

typealias ScorerAnalyzeProvider<T, Z> = (ScorerContext<T>) -> Z?

class ScorerContext<T : Any>(
    override val model: T,
    override val playerIndex: Int,
    override val action: Actionable<T, Any>,
    private val providersInvoked: MutableMap<ScorerAnalyzeProvider<T, Any>, Any?>
): ScorerScope<T, Any> {

    override fun <Z> require(analyzeProvider: ScorerAnalyzeProvider<T, Z>): Z? {
        if (providersInvoked.containsKey(analyzeProvider)) {
            return providersInvoked.get(analyzeProvider) as Z?
        }
        val value = analyzeProvider(this)
        providersInvoked[analyzeProvider] = value
        return value
    }

}

interface ScorerScope<T : Any, A: Any> {
    val model: T
    val playerIndex: Int
    val action: Actionable<T, A>
    fun <Z> require(analyzeProvider: ScorerAnalyzeProvider<T, Z>): Z?
}

class ScorerFactory<T : Any>(val gameSpec: GameSpec<T>) {

    fun <A> provider(provider: (ScorerContext<T>) -> A?): ScorerAnalyzeProvider<T, A> = provider
    fun isAction(action: ActionType<T, *>): Scorer<T, Any> = this.action(action) { 1.0 }
    fun <A: Any> action(action: ActionType<T, A>, function: ScoreFunction<T, A>): Scorer<T, Any> {
        return Scorer { if (this.action.actionType == action.name) function(this as ScorerScope<T, A>) else null }
    }
    fun <A: Any> actionConditional(action: ActionType<T, A>, function: ScorerScope<T, A>.() -> Boolean): Scorer<T, Any> {
        return Scorer { if (this.action.actionType == action.name) if (function(this as ScorerScope<T, A>)) 1.0 else 0.0 else null }
    }
    fun ai(name: String, vararg config: Scorer<T, Any>) = ScorerController(gameSpec.name, name, *config)

}

typealias ScoreFunction<T, A> = ScorerScope<T, A>.() -> Double?
class Scorer<T : Any, A : Any>(private val scoreFunction: ScoreFunction<T, A>) {
    fun score(scope: ScorerScope<T, A>): Double? {
        return scoreFunction.invoke(scope)
    }

    fun weight(weight: Int): Scorer<T, A> {
        return this.weight(weight.toDouble())
    }
    fun weight(weight: Double): Scorer<T, A> {
        val self = this
        return Scorer { (self.scoreFunction(this)?: return@Scorer null) * weight }
    }
    fun multiply(next: Scorer<T, A>): Scorer<T, A> {
        val old = this
        return Scorer { old.score(this)?.times(next.scoreFunction(this)?:0.0) }
    }

}

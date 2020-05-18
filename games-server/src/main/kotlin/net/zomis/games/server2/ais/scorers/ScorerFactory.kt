package net.zomis.games.server2.ais.scorers

import net.zomis.games.dsl.Actionable
import kotlin.reflect.KClass

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

class ScorerFactory<T : Any> {

//    operator fun invoke(): Scorer<T, Any> {
//        return
//    }

    fun simple(scoreFunction: ScoreFunction<T, Any>): Scorer<T, Any> {
        return Scorer(scoreFunction)
    }

    fun conditional(condition: ScorerScope<T, Any>.() -> Boolean): Scorer<T, Any> {
        return Scorer { if (condition(this)) 1.0 else null }
    }

    fun <S: Any> conditionalType(clazz: KClass<S>, scoreFunction: ScoreFunction<T, S>): Scorer<T, S> {
        return Scorer { if (clazz.isInstance(action.parameter)) scoreFunction(this) else null }
    }

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

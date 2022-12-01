package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.random.Random

data class ActionNextParameter<T: Any, P: Any>(
    val actionType: ActionType<T, P>,
    val chosen: List<Any>,
    val parameter: P
)
data class ActionNextChoice<T: Any, P: Any>(
    val actionType: ActionType<T, *>,
    val previouslyChosen: List<Any>,
    val choiceKey: Any,
    val choiceValue: Any,
    val nextRecursive: (ActionChoicesRecursiveSpecScope<T, Any, P>.(Any) -> Unit)? = null,
    val recursiveBlock: (ActionChoicesRecursiveSpecScope<T, Any, P>.() -> Unit)? = null,
    val nextBlock: (ActionChoicesScope<T, P>.(Any) -> Unit)? = null,
)

class ActionComplexImpl<T: Any, P: Any>(
    val actionType: ActionType<T, P>,
    val context: ActionOptionsContext<T>,
    private val block: ActionChoicesScope<T, P>.() -> Unit
) {
/*
Some possible use-cases:
- Given choices XYZ, what are the immediate next steps? (choices + parameters)
- Given choices XYZ, and using an ActionSampleSize, give me a sequence of possible actions
*/

    fun start(): ActionComplexNextImpl<T, P> {
        val scope = ActionComplexBlockRun(actionType, emptyList(), emptyList(), context)
        block.invoke(scope)
        return scope.createNext()
    }

    fun withChosen(chosen: List<Any>): ActionComplexNextImpl<T, P> {
        val scope = ActionComplexBlockRun(actionType, emptyList(), chosen, context)
        block.invoke(scope)
        return scope.createNext()
    }

}

class ActionComplexBlockRun<T: Any, P: Any>(
    private val actionType: ActionType<T, P>,
    private val chosen: List<Any>,
    private val upcomingChoices: List<Any>,
    override val context: ActionOptionsContext<T>
): ActionChoicesScope<T, P> {
    private var blockRun: () -> ActionComplexNextImpl<T, P> = {
        ActionComplexNextImpl(actionType, context, 0, chosen, choices.asSequence(), parameters.asSequence())
    }
    private val choices = mutableListOf<ActionNextChoice<T, P>>()
    private val parameters = mutableListOf<ActionNextParameter<T, P>>()

    override fun parameter(parameter: P) {
        if (upcomingChoices.isNotEmpty()) {
            return
        }
        parameters.add(ActionNextParameter(actionType, chosen, parameter))
    }

    private fun <E: Any> internalOptions(options: ActionOptionsScope<T>.() -> List<Pair<Any, E>>, next: ActionChoicesScope<T, P>.(E) -> Unit) {
        val evaluated = options(context)
        if (upcomingChoices.isNotEmpty()) {
            val nextChosenKey = upcomingChoices.first()
            val nextChosenList = upcomingChoices.subList(1, upcomingChoices.size)
            // first = choice key (serialized in some way), second = choice value.
            // It's probably okay to contain multiple of the same here, as some choices may serialize to the same value. They should be equivalent.
            val nextE = evaluated.firstOrNull { it.first == nextChosenKey || it.second == nextChosenKey }
                ?: throw IllegalStateException("Expected it.first == $nextChosenKey (${nextChosenKey::class}) but evaluated contains $evaluated")

            val nextScope = ActionComplexBlockRun(actionType, chosen + nextE.second, nextChosenList, context)
            next.invoke(nextScope, nextE.second)
            blockRun = nextScope.blockRun
        } else {
            choices.addAll(evaluated.map { ActionNextChoice(actionType, chosen, it.first, it.second,
                nextBlock = next as ActionChoicesScope<T, P>.(Any) -> Unit)
            })
        }
    }

    override fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesScope<T, P>.(E) -> Unit) {
        val outerThis = this
        return this.internalOptions({ options(outerThis.context).map { it to it } }, next)
    }

    override fun <E : Any> optionsWithIds(options: ActionOptionsScope<T>.() -> Iterable<Pair<String, E>>, next: ActionChoicesScope<T, P>.(E) -> Unit) {
        val outerThis = this
        return this.internalOptions({ options(outerThis.context).map { it.first to it.second } }, next)
    }

    override fun <C : Any> recursive(base: C, options: ActionChoicesRecursiveSpecScope<T, C, P>.() -> Unit) {
        val recursiveContext = ActionRecursiveImpl(context, actionType, base, chosen, upcomingChoices, options)
        recursiveContext.evaluate(true)
        blockRun = recursiveContext.blockRun
    }

    fun createNext(): ActionComplexNextImpl<T, P> = blockRun()

}

class ActionComplexNextImpl<T: Any, P: Any>(
    override val actionType: ActionType<T, P>,
    private val context: ActionOptionsContext<T>,
    private val recursiveChosen: Any,
    override val chosen: List<Any>,
    private val nextChoices: Sequence<ActionNextChoice<T, P>>,
    private val nextParameters: Sequence<ActionNextParameter<T, P>>
) : ActionComplexChosenStep<T, P> {

    override val playerIndex: Int = context.playerIndex

    override fun nextOptions(): Sequence<ActionNextChoice<T, P>> = nextChoices
    override fun parameters(): Sequence<ActionNextParameter<T, P>> = nextParameters

    private fun <E> List<E>.randomSample(count: Int?, random: Random): List<E> {
        if (count == null) return this
        val indices = this.indices.toMutableList()
        val result = mutableListOf<E>()
        repeat(count) {
            if (indices.isEmpty()) {
                throw IllegalArgumentException("No more items after $it/$count. Result is $result, remaining is $this")
            }
            result.add(this[indices.removeAt(random.nextInt(indices.size))])
        }
        return result
    }

    override fun depthFirstActions(sampling: ActionSampleSize?): Sequence<ActionNextParameter<T, P>> {
        return sequence {
            yieldAll(nextParameters)
            val (nextSampleSize, nextActionSampleSize) = sampling?.nextSample() ?: (null to null)
            val samples: List<ActionNextChoice<T, P>> = nextChoices.toList().randomSample(nextSampleSize, Random.Default)

            /*
            * Evaluate options
            * - Pick X of them (sample size)
            *   - Evaluate those options
            *     - Pick X of them (sample size)
            *
            * Store all parameters in one place
            * Store intermediate options in another
            */

            for (it in samples) {
                if (it.nextBlock != null) {
                    val nextScope = ActionComplexBlockRun(actionType, chosen + it.choiceValue, emptyList(), context)
                    it.nextBlock.invoke(nextScope, it.choiceValue)
                    yieldAll(nextScope.createNext().depthFirstActions(nextActionSampleSize))
                }
                if (it.nextRecursive != null) {
                    val nextScope = ActionRecursiveImpl(context, actionType, recursiveChosen, chosen + it.choiceValue, emptyList(), it.recursiveBlock!!)
                    it.nextRecursive.invoke(nextScope, it.choiceValue)
                    nextScope.evaluate(false)
                    yieldAll(nextScope.blockRun.invoke().depthFirstActions(nextActionSampleSize))
                }
            }
        }
    }

    override fun actionKeys(): List<ActionInfoKey> {
        val parameters = parameters().map { ActionInfoKey(actionType.serialize(it.parameter), actionType.name, emptyList(), true) }
        val choices = nextOptions().map { ActionInfoKey(it.choiceKey, actionType.name, emptyList(), false) }
        return parameters.toList() + choices.toList()
    }
}
class ActionComplexChosenStepEmpty<T: Any, P: Any>(
    override val actionType: ActionType<T, P>,
    override val playerIndex: Int,
    override val chosen: List<Any>
): ActionComplexChosenStep<T, P> {

    override fun nextOptions(): Sequence<ActionNextChoice<T, P>> = emptySequence()
    override fun parameters(): Sequence<ActionNextParameter<T, P>> = emptySequence()
    override fun depthFirstActions(sampling: ActionSampleSize?): Sequence<ActionNextParameter<T, P>> = emptySequence()
    override fun actionKeys(): List<ActionInfoKey> = emptyList()

}

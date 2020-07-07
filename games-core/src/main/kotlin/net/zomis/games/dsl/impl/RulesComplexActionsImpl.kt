package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.random.Random

class RulesActionTypeComplexKeys<T : Any, A : Any>(
    override val context: ActionOptionsContext<T>,
    private val actionType: ActionType<T, A>,
    private val chosen: List<Any>,
    private val yielder: (List<ActionInfo<T, A>>) -> Unit
): ActionChoicesNextScope<T, A> {

    override fun parameter(action: A) {
        // Ignore parameter if we haven't resolved all the chosen steps yet
        if (chosen.isEmpty()) {
            yielder(listOf(ActionInfo(actionType, action, null)))
        }
    }

    private fun <E: Any> internalEvaluation(options: ActionOptionsScope<T>.() -> List<Pair<ActionInfoKey, E>>, next: ActionChoicesNextScope<T, A>.(E) -> Unit) {
        val evaluated = options(context)
        if (chosen.isNotEmpty()) {
            val nextChosenKey = chosen[0]
            val nextChosenList = chosen.subList(1, chosen.size)
            val nextE = evaluated.single { it.first.serialized == nextChosenKey }

            val nextScope = RulesActionTypeComplexKeys(context, actionType, nextChosenList, yielder)
            next.invoke(nextScope, nextE.second)
        } else {
            yielder(evaluated.map { ActionInfo(actionType, null, it.first.serialized) })
            // TODO: Figure out if the below commented code can be removed. Or perhaps use `evaluateParametersOnly` as last step?
/*
            if (!evaluateOptions) {
                return
            }
            evaluated.forEach {
                val nextScope = RulesActionTypeComplexKeys<T, A>(context, emptyList(), false, {}, actionYielder)
                next.invoke(nextScope, it)
            }
*/
        }
    }

    override fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesNextScope<T, A>.(E) -> Unit)
        = this.internalEvaluation({ options(context).map { ActionInfoKey(it, actionType, emptyList(), false) to it } }, next)

    override fun <E : Any> optionsWithIds(options: ActionOptionsScope<T>.() -> Iterable<Pair<String, E>>, next: ActionChoicesNextScope<T, A>.(E) -> Unit)
        = this.internalEvaluation({ options(context).map { ActionInfoKey(it.first, actionType, emptyList(), false) to it.second } }, next)

}

class RulesActionTypeComplexAvailableActions<T: Any, A: Any>(
    val context: ActionOptionsContext<T>,
    private val actionType: ActionType<T, A>,
    private val chosen: List<Any>,
    private val yielder: (A) -> Unit

) {

    private fun <E> List<E>.randomSample(count: Int, random: Random): MutableList<E> {
        val indices = this.indices.toMutableList()
        val result = mutableListOf<E>()
        repeat(count) {
            result.add(this[indices.removeAt(random.nextInt(indices.size))])
        }
        return result
    }

    fun availableActions(
        actionSampleSize: ActionSampleSize?,
        block: ActionChoicesStartScope<T, A>.() -> Unit
    ) {
        var nextOptions = mutableListOf<Any>()
        val yielder: (List<ActionInfo<T, A>>) -> Unit = {list ->
            list.forEach { actionInfo ->
                val parameter = actionInfo.parameter
                if (parameter != null) yielder(parameter)
                else nextOptions.add(actionInfo.nextStep!!)
            }
        }

        block.invoke(RulesActionTypeComplexKeys(context, actionType, chosen, yielder))
        if (actionSampleSize?.sampleSizes?.isEmpty() == true) {
            return
        }

        var nextSample: ActionSampleSize? = actionSampleSize
        if (actionSampleSize != null) {
            val (next, sample) = actionSampleSize.nextSample()
            nextSample = sample
            if (nextOptions.size > next) {
                nextOptions = nextOptions.randomSample(next, Random.Default)
            }
        }

        nextOptions.forEach {
            RulesActionTypeComplexAvailableActions(context, actionType, chosen + it, this.yielder)
                .availableActions(nextSample, block)
        }
        /*
        * Evaluate options
        * - Pick X of them (sample size)
        *   - Evaluate those options
        *     - Pick X of them (sample size)
        *
        * Store all parameters in one place
        * Store intermediate options in another
        */
    }


}

class RulesActionTypeComplex<T : Any, A : Any>(
    val context: ActionOptionsContext<T>,
    private val actionType: ActionType<T, A>,
    val options: ActionChoicesStartScope<T, A>.() -> Unit
) {

    fun availableActions(actionSampleSize: ActionSampleSize?): Iterable<Actionable<T, A>> {
        // If needed later, it's easy to add `chosen: List<Any>` to parameters of this method and pass them on
        val actionParameters = mutableListOf<A>()
        RulesActionTypeComplexAvailableActions(context, actionType, emptyList()) {
            actionParameters.add(it)
        }.availableActions(actionSampleSize, options)
        return actionParameters.map(this::createAction)
    }

    fun createAction(parameter: A): Actionable<T, A>
        = Action(context.game, context.playerIndex, context.actionType, parameter)

    fun availableActionKeys(previouslySelected: List<Any>): List<ActionInfoKey> {
        val results = mutableListOf<ActionInfo<T, A>>()
        val yielder: (List<ActionInfo<T, A>>) -> Unit = { list -> results.addAll(list) }

        val nextScope = RulesActionTypeComplexKeys(context, actionType, previouslySelected, yielder)
        this.options.invoke(nextScope)
        return results.map { action ->
            ActionInfoKey(action.parameter?.let { actionType.serialize(it) } ?: action.nextStep!!,
                actionType.name, emptyList(), action.parameter != null)
        }
    }

}


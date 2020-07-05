package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*

class RulesActionTypeComplexNext<T : Any, A : Any>(override val context: ActionOptionsContext<T>, val yielder: (A) -> Unit): ActionChoicesNextScope<T, A> {
    override fun parameter(action: A) {
        yielder(action)
    }

    override fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesNextScope<T, A>.(E) -> Unit) {
        val evaluated = options(context)
        evaluated.forEach {
            val nextScope = RulesActionTypeComplexNext(context, yielder)
            next.invoke(nextScope, it)
        }
    }

    override fun <E : Any> optionsWithIds(options: ActionOptionsScope<T>.() -> Iterable<Pair<String, E>>, next: ActionChoicesNextScope<T, A>.(E) -> Unit) {
        val evaluated = options(context)
        evaluated.forEach {
            val nextScope = RulesActionTypeComplexNext(context, yielder)
            next.invoke(nextScope, it.second)
        }
    }
}

class RulesActionTypeComplexNextOnly<T : Any, A : Any>(
    override val context: ActionOptionsContext<T>,
    private val chosen: List<Any>,
    private val evaluateOptions: Boolean,
    private val nextYielder: (List<Pair<String?, Any>>) -> Unit,
    private val actionYielder: (A) -> Unit
): ActionChoicesNextScope<T, A> {
    override fun parameter(action: A) {
        // Ignore parameter if we haven't resolved all the chosen steps yet
        if (chosen.isEmpty()) {
            actionYielder(action)
        }
    }

    override fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesNextScope<T, A>.(E) -> Unit) {
        val evaluated = options(context)
        if (chosen.isNotEmpty()) {
            val nextChosen = chosen[0] as E
            val nextChosenList = chosen.subList(1, chosen.size)

            val nextScope = RulesActionTypeComplexNextOnly<T, A>(context, nextChosenList,true, nextYielder, actionYielder)
            next.invoke(nextScope, nextChosen)
        } else {
            nextYielder(evaluated.map { null to it })
            if (!evaluateOptions) {
                return
            }
            evaluated.forEach {
                val nextScope = RulesActionTypeComplexNextOnly<T, A>(context, emptyList(), false, {}, actionYielder)
                next.invoke(nextScope, it)
            }
        }
    }

    override fun <E : Any> optionsWithIds(options: ActionOptionsScope<T>.() -> Iterable<Pair<String, E>>, next: ActionChoicesNextScope<T, A>.(E) -> Unit) {
        val evaluated = options(context)
        if (chosen.isNotEmpty()) {
            val nextChosen = evaluated.single { it.first == chosen[0] as String }
            val nextChosenList = chosen.subList(1, chosen.size)

            val nextScope = RulesActionTypeComplexNextOnly<T, A>(context, nextChosenList,true, nextYielder, actionYielder)
            next.invoke(nextScope, nextChosen.second)
        } else {
            nextYielder(evaluated.toList())
            if (!evaluateOptions) {
                return
            }
            evaluated.forEach {
                val nextScope = RulesActionTypeComplexNextOnly<T, A>(context, emptyList(), false, {}, actionYielder)
                next.invoke(nextScope, it.second)
            }
        }
    }
}

class ActionOptionsResult<A: Any>(val next: List<Pair<String?, Any>>, val parameters: List<A>)

class RulesActionTypeComplex<T : Any, A : Any>(
    val context: ActionOptionsContext<T>,
    val options: ActionChoicesStartScope<T, A>.() -> Unit
) {

    fun availableOptionsNext(chosen: List<Any>): ActionOptionsResult<A> {
        val nexts = mutableListOf<Pair<String?, Any>>()
        val actionParams = mutableListOf<A>()
        val yielder: (List<Pair<String?, Any>>) -> Unit = { nexts.addAll(it) }
        val actionYielder: (A) -> Unit = { actionParams.add(it) }

        val nextScope = RulesActionTypeComplexNextOnly<T, A>(context, chosen, true, yielder, actionYielder)
        this.options.invoke(nextScope)
        return ActionOptionsResult(nexts, actionParams)
    }

    fun availableActions(): Iterable<Action<T, A>> {
        val result = mutableListOf<A>()
        val yielder: (A) -> Unit = { result.add(it) }
        val nextScope = RulesActionTypeComplexNext<T, A>(context, yielder)
        this.options.invoke(nextScope)
        return result.map { createAction(it) }
    }

    fun createAction(parameter: A): Action<T, A>
        = Action(context.game, context.playerIndex, context.actionType, parameter)

}


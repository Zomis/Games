package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminationsRead
import net.zomis.games.dsl.ActionChoicesRecursiveScope
import net.zomis.games.dsl.ActionChoicesRecursiveSpecScope
import net.zomis.games.dsl.ActionChoicesScope
import net.zomis.games.dsl.ActionType

class ActionRecursiveContext<T: Any, C: Any>(
    val context: ActionOptionsContext<T>,
    val choices: C
): ActionChoicesRecursiveScope<T, C> {
    override val chosen: C get() = choices
    override val game: T get() = context.game
    override val eliminations: PlayerEliminationsRead get() = context.eliminations
    override val actionType: String get() = context.actionType
    override val playerIndex: Int get() = context.playerIndex
}

class ActionRecursiveImpl<T: Any, C: Any, P: Any>(
    val context: ActionOptionsContext<T>,
    val actionType: ActionType<T, P>,
    val chosen: C,
    val previousChoices: List<Any>,
    private val upcomingChoices: List<Any>,
    private val recursiveBlock: ActionChoicesRecursiveSpecScope<T, C, P>.() -> Unit
) : ActionChoicesRecursiveSpecScope<T, C, P> {
    override val game: T get() = context.game
    override val playerIndex: Int get() = context.playerIndex
    private val recursiveContext = ActionRecursiveContext(context, chosen)

    internal var blockRun: () -> ActionComplexNextImpl<T, P> = {
        ActionComplexNextImpl(actionType, context, chosen, previousChoices, choices.asSequence(), parameters.asSequence())
    }
    private val potentialChoices = mutableListOf<ActionNextChoice<T, P>>()
    private val choices = mutableListOf<ActionNextChoice<T, P>>()
    private val parameters = mutableListOf<ActionNextParameter<T, P>>()

    private var untilCondition: ActionChoicesRecursiveScope<T, C>.() -> Boolean = { false }
    private var parameterCreator: (ActionChoicesRecursiveScope<T, C>.() -> P)? = null
    private var intermediateParameter: ActionChoicesRecursiveScope<T, C>.() -> Boolean = { false }
    private var nextChoicesScope: (ActionChoicesScope<T, P>.() -> Unit)? = null

    override fun until(condition: ActionChoicesRecursiveScope<T, C>.() -> Boolean) {
        this.untilCondition = condition
    }

    private fun <E: Any> internalOptions(
        options: ActionChoicesRecursiveScope<T, C>.() -> List<Pair<Any, E>>,
        next: ActionChoicesRecursiveSpecScope<T, C, P>.(E) -> Unit
    ) {
        val evaluated = options(recursiveContext)
        if (upcomingChoices.isNotEmpty()) {
            val nextChosenKey = upcomingChoices.first()
            val nextChosenList = upcomingChoices.subList(1, upcomingChoices.size)
            val nextE = evaluated.singleOrNull { it.first == nextChosenKey || it.second == nextChosenKey }
                ?: throw NoSuchElementException("Evaluated contains $evaluated and we're looking for it.first == $nextChosenKey (${nextChosenKey::class})")

            val nextScope = ActionRecursiveImpl(context, actionType, chosen, previousChoices + nextE.second, nextChosenList, recursiveBlock)
            next.invoke(nextScope, nextE.second)
            nextScope.evaluate(false)
            blockRun = nextScope.blockRun
        } else {
            potentialChoices.addAll(evaluated.map { ActionNextChoice(actionType, previousChoices, it.first, it.second,
                recursiveBlock = recursiveBlock as ActionChoicesRecursiveSpecScope<T, Any, P>.() -> Unit,
                nextRecursive = next as ActionChoicesRecursiveSpecScope<T, Any, P>.(Any) -> Unit)
            })
        }
    }

    override fun <E : Any> options(
        options: ActionChoicesRecursiveScope<T, C>.() -> Iterable<E>,
        next: ActionChoicesRecursiveSpecScope<T, C, P>.(E) -> Unit
    ) {
        return this.internalOptions({ options(recursiveContext).map { it to it } }, next)
    }

    override fun <E : Any> optionsWithIds(
        options: ActionChoicesRecursiveScope<T, C>.() -> Iterable<Pair<String, E>>,
        next: ActionChoicesRecursiveSpecScope<T, C, P>.(E) -> Unit
    ) {
        return this.internalOptions({ options(recursiveContext).map { it.first to it.second } }, next)
    }

    override fun <E : Any> recursion(chosen: E, operation: (C, E) -> C) {
        val nextChosen = operation.invoke(this.chosen, chosen)
        val next = ActionRecursiveImpl(context, actionType, nextChosen, previousChoices, upcomingChoices, recursiveBlock)
        next.evaluate(true)
        blockRun = next.blockRun
    }

    override fun parameter(parameterCreator: ActionChoicesRecursiveScope<T, C>.() -> P) {
        this.parameterCreator = parameterCreator
    }

    override fun intermediateParameter(allowed: ActionChoicesRecursiveScope<T, C>.() -> Boolean) {
        this.intermediateParameter = allowed
    }

    override fun then(next: ActionChoicesScope<T, P>.() -> Unit) {
        this.nextChoicesScope = next
    }

    internal fun evaluate(invoke: Boolean) {
        if (invoke) {
            recursiveBlock.invoke(this)
        }
        if (this.untilCondition(recursiveContext)) {
            // Recursion done, either run next section, or create final parameter
            if (this.nextChoicesScope != null) {
                TODO("Continuing with other choices after a recursion is not implemented yet")
            } else {
                val parameter = this.parameterCreator!!.invoke(recursiveContext)
                parameters.add(ActionNextParameter(actionType, previousChoices, parameter))
            }
        } else {
            // Create intermediateParameter if allowed
            if (this.intermediateParameter.invoke(recursiveContext)) {
                this.parameters.add(ActionNextParameter(actionType, previousChoices, this.parameterCreator!!.invoke(recursiveContext)))
            }

            // Run internal options block
            this.choices.addAll(potentialChoices)
        }
    }


}
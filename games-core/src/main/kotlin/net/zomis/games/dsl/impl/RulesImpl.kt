package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminations
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameRulesContext<T : Any>(
    val model: T,
    val replayable: ReplayState,
    val eliminations: PlayerEliminations
): GameRules<T> {
    private val views = mutableListOf<Pair<String, ViewScope<T>.() -> Any?>>()
//    private val logger = KLoggers.logger(this)
    private val globalRules = GameRuleList<T>(model, replayable, eliminations)
    private val ruleList = mutableMapOf<String, GameActionRuleContext<T, Any>>()

    override val allActions: GameAllActionsRule<T>
        get() = globalRules

    override fun <A : Any> action(actionType: ActionType<T, A>): GameActionRule<T, A> {
        return ruleList.getOrPut(actionType.name) {
            GameActionRuleContext(model, replayable, eliminations, actionType, globalRules) as GameActionRuleContext<T, Any>
        } as GameActionRule<T, A>
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        this.views.add(key to value)
    }

    override fun gameStart(onStart: GameStartScope<T>.() -> Unit) {
        onStart.invoke(GameStartContext(model, replayable))
    }

    fun view(context: GameViewContext<T>) {
        views.forEach {
            val key = it.first
            val function = it.second
            val result = function(context)
            context.value(key) { result }
        }
    }

    fun actionTypes(): Set<String> {
        return this.ruleList.keys.toSet()
    }

    fun actionType(actionType: String): ActionTypeImplEntry<T, Any>? {
        return this.ruleList[actionType].let {
            if (it != null) { ActionTypeImplEntry(model, replayable, it.actionDefinition, it) } else null
        }
    }

    override fun <E : Any> trigger(triggerClass: KClass<E>): GameRuleTrigger<T, E> {
        return GameRuleTriggerImpl(model, replayable, eliminations)
    }

    override fun <A : Any> action(actionType: ActionType<T, A>, ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit) {
        return this.action(actionType).invoke(ruleSpec)
    }
}

class GameStartContext<T : Any>(override val game: T, override val replayable: ReplayableScope) : GameStartScope<T>

class GameRuleList<T : Any>(
    val model: T,
    val replayable: ReplayableScope,
    val eliminations: PlayerEliminations
): GameAllActionsRule<T> {
    val after = mutableListOf<ActionRuleScope<T, Any>.() -> Unit>()
    val preconditions = mutableListOf<ActionOptionsScope<T>.() -> Boolean>()

    override fun after(rule: ActionRuleScope<T, Any>.() -> Unit) { this.after.add(rule) }
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) { this.preconditions.add(rule) }
}

class ActionOptionsContext<T : Any>(
    override val game: T,
    override val actionType: String,
    override val playerIndex: Int
) : ActionOptionsScope<T>

class ActionRuleContext<T : Any, A : Any>(
    override val game: T,
    override val action: Actionable<T, A>,
    override val eliminations: PlayerEliminations,
    override val replayable: ReplayableScope
): ActionRuleScope<T, A> {
    internal val logs = mutableListOf<ActionLogEntry>()

    override val playerIndex: Int get() = action.playerIndex
    override val actionType: String get() = action.actionType

    override fun log(logging: LogActionScope<T, A>.() -> String) {
        logs.add(LogActionContext(game, action.playerIndex, action.parameter).log(logging))
    }
    override fun logSecret(player: PlayerIndex, logging: LogActionScope<T, A>.() -> String): SecretLogging<T, A> {
        val context = LogActionContext(game, player, action.parameter).secretLog(player, logging)
        logs.add(context)
        return context
    }
}

class GameActionRuleContext<T : Any, A : Any>(
    val model: T,
    val replayable: ReplayState,
    val eliminations: PlayerEliminations,
    val actionDefinition: ActionType<T, A>,
    val globalRules: GameRuleList<T>
): GameActionRule<T, A>, GameLogicActionType<T, A> {
    override val actionType: ActionType<T, A> = actionDefinition

    val effects = mutableListOf<ActionRuleScope<T, A>.() -> Unit>()
    val after = mutableListOf<ActionRuleScope<T, A>.() -> Unit>()
    val preconditions = mutableListOf<ActionOptionsScope<T>.() -> Boolean>()
    val allowed = mutableListOf<ActionRuleScope<T, A>.() -> Boolean>()
    private var choices: (ActionChoicesStartScope<T, A>.() -> Unit)? = null
    private var availableActionsEvaluator: (ActionOptionsScope<T>.() -> Iterable<A>)? = null

    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) { this.after.add(rule) }
    override fun effect(rule: ActionRuleScope<T, A>.() -> Unit) { this.effects.add(rule) }
    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) { this.allowed.add(rule) }
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) { this.preconditions.add(rule) }

    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
        this.availableActionsEvaluator = rule
    }

    override fun choose(options: ActionChoicesStartScope<T, A>.() -> Unit) {
        require(choices == null) { "Choices can only be set once" }
        this.choices = options
    }

    override fun forceUntil(rule: ActionOptionsScope<T>.() -> Boolean) {
        val myActionType = actionType
        globalRules.preconditions.add {
            actionType == myActionType.name || rule(this)
        }
    }

    override fun forceWhen(rule: ActionOptionsScope<T>.() -> Boolean) {
        val myActionType = actionType
        globalRules.preconditions.add {
            actionType == myActionType.name || !rule(this)
        }
        this.preconditions.add(rule)
    }

    // GameLogicActionType implementation below

    override fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, A>> {
        val context = ActionOptionsContext(model, actionType.name, playerIndex)
        if (!checkPreconditions(context)) {
            return emptyList()
        }

        val evaluator = this.availableActionsEvaluator
        return if (this.choices == null) {
            if (evaluator == null) {
                require(this.actionDefinition.parameterType == Unit::class) {
                    "Action type ${this.actionDefinition.name} with parameter ${actionDefinition.parameterType} needs to specify a list of allowed parameters"
                }
                listOf(createAction(playerIndex, Unit as A)).filter { actionAllowed(it) }
            } else evaluator(context).map { createAction(playerIndex, it) }.filter { this.actionAllowed(it) }
        } else {
            require(this.availableActionsEvaluator == null) { "An action must have only one rule for either choices or options" }
            val complex = RulesActionTypeComplex(context, actionType, this.choices!!)
            val actions = complex.availableActions(sampleSize)
            actions.toList().filter { actionAllowed(it) }
        }
    }

    override fun actionAllowed(action: Actionable<T, A>): Boolean {
        val context = ActionRuleContext(model, action, eliminations, replayable)
        return checkPreconditions(context) && allowed.all { it(context) }
    }

    override fun replayAction(action: Actionable<T, A>, state: Map<String, Any>?) {
        if (state != null) {
            replayable.setReplayState(state)
        }
        this.performAction(action)
    }

    override fun performAction(action: Actionable<T, A>) {
        if (!actionAllowed(action)) throw IllegalStateException("Action is not allowed: $action")
        val context = ActionRuleContext(model, action, eliminations, replayable)
        this.effects.forEach { it.invoke(context) }
        this.after.forEach { it.invoke(context) }
        this.globalRules.after.forEach { it.invoke(context as ActionRuleScope<T, Any>) }
        replayable.stateKeeper.addLogs(context.logs)
    }

    override fun createAction(playerIndex: Int, parameter: A): Action<T, A>
        = Action(model, playerIndex, actionType.name, parameter)

    fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey> {
        val context = ActionOptionsContext(model, actionType.name, playerIndex)
        if (!checkPreconditions(context)) {
            return emptyList()
        }
        val evaluator = this.availableActionsEvaluator
        return if (this.choices == null) {
            if (evaluator == null) {
                require(this.actionDefinition.parameterType == Unit::class) {
                    "Action type ${this.actionDefinition.name} with parameter ${actionDefinition.parameterType} needs to specify a list of allowed parameters"
                }
                listOf(createAction(playerIndex, Unit as A)).filter { actionAllowed(it) }.map(this::actionInfoKey)
            } else evaluator(context).map { createAction(playerIndex, it) }.filter { this.actionAllowed(it) }.map(this::actionInfoKey)
        } else {
            require(this.availableActionsEvaluator == null) { "An action must have only one rule for either choices or options" }
            val complex = RulesActionTypeComplex(context, actionType, this.choices!!)
            return complex.availableActionKeys(previouslySelected)
        }
    }

    private fun actionInfoKey(actionable: Actionable<T, A>)
        = ActionInfoKey(actionType.serialize(actionable.parameter), actionType.name, emptyList(), true)

    private fun checkPreconditions(context: ActionOptionsScope<T>): Boolean {
        return globalRules.preconditions.all { it.invoke(context) } && preconditions.all { it.invoke(context) }
    }

    override fun invoke(ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit) {
        ruleSpec(this)
    }

}

data class GameRuleTriggerContext<T : Any, E : Any>(
    override val game: T,
    override val trigger: E,
    override val replayable: ReplayableScope,
    override val eliminations: PlayerEliminations
): GameRuleTriggerScope<T, E>

class GameRuleTriggerImpl<T : Any, E : Any>(
    val model: T,
    val replayable: ReplayState,
    val eliminations: PlayerEliminations
) : GameRuleTrigger<T, E> {
    private val effects = mutableListOf<GameRuleTriggerScope<T, E>.() -> Unit>()
    private val mappings = mutableListOf<GameRuleTriggerScope<T, E>.() -> E>()
    private val ignoreConditions = mutableListOf<GameRuleTriggerScope<T, E>.() -> Boolean>()
    private val after = mutableListOf<GameRuleTriggerScope<T, E>.() -> Unit>()

    override fun effect(effect: GameRuleTriggerScope<T, E>.() -> Unit): GameRuleTrigger<T, E> {
        this.effects.add(effect)
        return this
    }

    override fun map(mapping: GameRuleTriggerScope<T, E>.() -> E): GameRuleTrigger<T, E> {
        this.mappings.add(mapping)
        return this
    }

    override fun after(effect: GameRuleTriggerScope<T, E>.() -> Unit): GameRuleTrigger<T, E> {
        this.after.add(effect)
        return this
    }

    override fun ignoreEffectIf(condition: GameRuleTriggerScope<T, E>.() -> Boolean): GameRuleTrigger<T, E> {
        this.ignoreConditions.add(condition)
        return this
    }

    override fun invoke(trigger: E): E? {
        val result = mappings.fold(GameRuleTriggerContext(model, trigger, replayable, eliminations)) {
            acc, next -> acc.copy(trigger = next.invoke(acc))
        }
        val process = ignoreConditions.none { it.invoke(result) }
        if (process) {
            effects.forEach { it.invoke(result) }
        }
        after.forEach { it.invoke(result) }
        return if (process) result.trigger else null
    }

}
package net.zomis.games.dsl.impl

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.common.GameEvents
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.rulebased.*
import kotlin.reflect.KClass

class GameRuleContext<T: Any>(
    override val game: T,
    override val eliminations: PlayerEliminationsWrite,
    override val replayable: ReplayState
): GameRuleScope<T>

class GameActionRulesContext<T : Any>(
    val model: T,
    val replayable: ReplayState,
    val eliminations: PlayerEliminationsWrite
): GameActionRules<T>, GameRules<T>, GameEventsExecutor {
    private val views = mutableListOf<Pair<String, ViewScope<T>.() -> Any?>>()
//    private val logger = KLoggers.logger(this)
    private val allActionRules = GameRuleList(model, replayable, eliminations)
    private val actionRules = mutableMapOf<String, GameActionRuleContext<T, Any>>()
    private val gameRules = mutableListOf<GameRuleImpl<T>>()
    init {
        allActionRules.after { stateCheck() }
    }

    override val allActions: GameAllActionsRule<T>
        get() = allActionRules

    override fun <A : Any> action(actionType: ActionType<T, A>): GameActionRule<T, A> {
        return actionRules.getOrPut(actionType.name) {
            GameActionRuleContext(model, replayable, eliminations, actionType, allActionRules) as GameActionRuleContext<T, Any>
        } as GameActionRule<T, A>
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        this.views.add(key to value)
    }

    override fun gameStart(onStart: GameStartScope<T>.() -> Unit) {
        onStart.invoke(GameStartContext(model, replayable, eliminations.playerCount))
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
        return this.actionRules.keys.toSet()
    }

    fun actionType(actionType: String): ActionTypeImplEntry<T, Any>? {
        return this.actionRules[actionType].let {
            if (it != null) { ActionTypeImplEntry(model, replayable, eliminations, it.actionDefinition, it) } else null
        }
    }

    override fun <E : Any> trigger(triggerClass: KClass<E>): GameRuleTrigger<T, E> {
        return GameRuleTriggerImpl(model, replayable, eliminations)
    }

    override fun <A : Any> action(actionType: ActionType<T, A>, ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit) {
        return this.action(actionType).invoke(ruleSpec)
    }

    override fun rule(name: String, rule: GameRule<T>.() -> Any?): GameRule<T> {
        val ruleImpl = GameRuleImpl(this, null, name)
        rule.invoke(ruleImpl)
        gameRules.add(ruleImpl)
        return ruleImpl
    }

    private val ruleContext = GameRuleContext(model, eliminations, replayable)
    private fun determineActiveRules(list: List<GameRuleImpl<T>>): GameRulesActive<T> {
        val rulesActive = mutableListOf<GameRuleImpl<T>>()
        for (rule in list) {
            if (rule.isActive(ruleContext)) {
                rulesActive.add(rule)
                rulesActive.addAll(determineActiveRules(rule.subRules()).rules)
            }
        }
        return GameRulesActive(rulesActive)
    }

    fun gameStart(): List<GameRule<T>> {
        val activeRules = determineActiveRules(gameRules)
        return activeRules.runGameStart(ruleContext) + stateCheck()
    }

    fun stateCheck(): List<GameRule<T>> {
        var loop = 0
        val rulesTriggered = mutableListOf<GameRule<T>>()
        while (loop < 100_000) {
            val activeRules = determineActiveRules(gameRules)
            val result = activeRules.stateCheck(ruleContext)
            rulesTriggered.addAll(result)
            if (result.isEmpty()) {
                return rulesTriggered
            }
            loop++
        }
        throw IllegalStateException("Stuck in a loop, most recent rules are " +
            rulesTriggered.drop(rulesTriggered.size - 10)
        )
    }

    override fun <E> fire(executor: GameEvents<E>, event: E) {
        this.gameRules.forEach { it.fire(ruleContext, executor as GameEvents<Any?>, event) }
    }

}

class GameStartContext<T : Any>(
    override val game: T,
    override val replayable: ReplayableScope,
    override val playerCount: Int
) : GameStartScope<T>

class GameRuleList<T : Any>(
    val model: T,
    val replayable: ReplayableScope,
    val eliminations: PlayerEliminationsWrite
): GameAllActionsRule<T> {
    val after = mutableListOf<ActionRuleScope<T, Any>.() -> Unit>()
    val preconditions = mutableListOf<ActionOptionsScope<T>.() -> Boolean>()

    override fun after(rule: ActionRuleScope<T, Any>.() -> Unit) { this.after.add(rule) }
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) { this.preconditions.add(rule) }
}

class ActionOptionsContext<T : Any>(
    override val game: T,
    override val actionType: String,
    override val playerIndex: Int,
    override val eliminations: PlayerEliminationsWrite,
    override val replayable: ReplayableScope
) : ActionOptionsScope<T>, GameRuleScope<T> {
    fun <A: Any> createAction(parameter: A): Actionable<T, A> = Action(game, playerIndex, actionType, parameter)
}

class ActionRuleContext<T : Any, A : Any>(
    override val game: T,
    override val action: Actionable<T, A>,
    override val eliminations: PlayerEliminationsWrite,
    override val replayable: ReplayState
): ActionRuleScope<T, A>, GameRuleScope<T> {
    override val playerIndex: Int get() = action.playerIndex
    override val actionType: String get() = action.actionType

    override fun log(logging: LogActionScope<T, A>.() -> String) {
        replayable.stateKeeper.log(LogActionContext(game, action.playerIndex, action.parameter).log(logging))
    }
    override fun logSecret(player: PlayerIndex, logging: LogActionScope<T, A>.() -> String): LogSecretActionScope<T, A> {
        val context = LogActionContext(game, player, action.parameter).secretLog(player, logging)
        replayable.stateKeeper.log(context)
        return context
    }

    override fun <E : Any> config(gameConfig: GameConfig<E>): E = replayable.config(gameConfig)
}

class GameActionRuleContext<T : Any, A : Any>(
    val model: T,
    val replayable: ReplayState,
    val eliminations: PlayerEliminationsWrite,
    val actionDefinition: ActionType<T, A>,
    val globalRules: GameRuleList<T>
): GameActionRule<T, A>, GameLogicActionType<T, A> {
    override val actionType: ActionType<T, A> = actionDefinition

    val effects = mutableListOf<ActionRuleScope<T, A>.() -> Unit>()
    val after = mutableListOf<ActionRuleScope<T, A>.() -> Unit>()
    val preconditions = mutableListOf<ActionOptionsScope<T>.() -> Boolean>()
    val allowed = mutableListOf<ActionRuleScope<T, A>.() -> Boolean>()
    private var choices: (ActionChoicesScope<T, A>.() -> Unit)? = null
    private var availableActionsEvaluator: (ActionOptionsScope<T>.() -> Iterable<A>)? = null

    override fun after(rule: ActionRuleScope<T, A>.() -> Unit) { this.after.add(rule) }
    override fun effect(rule: ActionRuleScope<T, A>.() -> Unit) { this.effects.add(rule) }
    override fun requires(rule: ActionRuleScope<T, A>.() -> Boolean) { this.allowed.add(rule) }
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) { this.preconditions.add(rule) }

    override fun options(rule: ActionOptionsScope<T>.() -> Iterable<A>) {
        this.availableActionsEvaluator = rule
    }

    override fun choose(options: ActionChoicesScope<T, A>.() -> Unit) {
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
        val context = ActionOptionsContext(model, actionType.name, playerIndex, eliminations, replayable)
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
            val complex = ActionComplexImpl(actionType, context, this.choices!!)
            return complex.start().depthFirstActions(sampleSize).map { createAction(playerIndex, it.parameter) }.filter { actionAllowed(it) }.asIterable()
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
    }

    override fun createAction(playerIndex: Int, parameter: A): Action<T, A>
        = Action(model, playerIndex, actionType.name, parameter)

    override fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey> {
        val context = ActionOptionsContext(model, actionType.name, playerIndex, eliminations, replayable)
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
            val complex = ActionComplexImpl(actionType, context, this.choices!!)
            return complex.withChosen(previouslySelected).actionKeys()
        }
    }

    private fun actionInfoKey(actionable: Actionable<T, A>)
        = ActionInfoKey(actionType.serialize(actionable.parameter), actionType.name, emptyList(), true)

    private fun checkPreconditions(context: ActionOptionsScope<T>): Boolean {
        // TODO: Possibly re-work check by initializing to null and using mappers. To require at least one related rule to be active?
        return globalRules.preconditions.all { it.invoke(context) } && preconditions.all { it.invoke(context) }
    }

    override fun invoke(ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit) {
        ruleSpec(this)
    }

    override fun withChosen(playerIndex: Int, chosen: List<Any>): ActionComplexChosenStep<T, A> {
        require(this.choices != null) { "Cannot use withChosen on non-complex action type: ${this.actionDefinition.name}" }
        require(this.availableActionsEvaluator == null)
        val context = ActionOptionsContext(model, actionType.name, playerIndex, eliminations, replayable)
        return ActionComplexImpl(actionType, context, this.choices!!).withChosen(chosen)
    }

    override fun isComplex(): Boolean = this.choices != null

}

data class GameRuleTriggerContext<T : Any, E : Any>(
    override val game: T,
    override val trigger: E,
    override val replayable: ReplayableScope,
    override val eliminations: PlayerEliminationsWrite
): GameRuleTriggerScope<T, E>

class GameRuleTriggerImpl<T : Any, E : Any>(
    val model: T,
    val replayable: ReplayState,
    val eliminations: PlayerEliminationsWrite
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
package net.zomis.games.dsl.impl

import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.events.EventFactory
import net.zomis.games.dsl.events.MetaEventFactory
import net.zomis.games.dsl.flow.GameMetaScope
import net.zomis.games.dsl.rulebased.*
import kotlin.reflect.KClass

class GameActionRulesContext<T : Any>(
    val gameContext: GameMetaScope<T>
): GameActionRulesScope<T>, GameRulesScope<T> {
    private val views = mutableListOf<Pair<String, ViewScope<T>.() -> Any?>>()
    private val allActionRules = GameRuleList(gameContext)
    private val actionRules = mutableMapOf<String, GameActionRuleContext<T, Any>>()
    private val gameRules = mutableListOf<GameRuleImpl<T>>()
    override val meta: GameMetaScope<T> get() = gameContext
    init {
        allActionRules.after { this@GameActionRulesContext.stateCheck() }
    }

    override val allActions: GameAllActionsRule<T>
        get() = allActionRules

    override fun <A : Any> action(actionType: ActionType<T, A>): GameActionRule<T, A> {
        return actionRules.getOrPut(actionType.name) {
            GameActionRuleContext(gameContext, actionType, allActionRules) as GameActionRuleContext<T, Any>
        } as GameActionRule<T, A>
    }

    override fun view(key: String, value: ViewScope<T>.() -> Any?) {
        this.views.add(key to value)
    }

    override fun gameStart(onStart: GameStartScope<T>.() -> Unit) {
        onStart.invoke(GameStartContext(gameContext.configs, gameContext.game, gameContext.replayable, gameContext.eliminations.playerCount))
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
            if (it != null) { ActionTypeImplEntry(gameContext, it.actionDefinition, it) } else null
        }
    }

    override fun <E : Any> trigger(triggerClass: KClass<E>): EventFactory<E> = MetaEventFactory(meta)

    override fun <A : Any> action(actionType: ActionType<T, A>, ruleSpec: GameActionSpecificationScope<T, A>.() -> Unit) {
        return this.action(actionType).invoke(ruleSpec)
    }

    override fun rule(name: String, rule: GameRuleRuleScope<T>.() -> Any?): GameRuleRuleScope<T> {
        val ruleImpl = GameRuleImpl(this, null, name)
        rule.invoke(ruleImpl)
        gameRules.add(ruleImpl)
        return ruleImpl
    }

    private fun determineActiveRules(list: List<GameRuleImpl<T>>): GameRulesActive<T> {
        val rulesActive = mutableListOf<GameRuleImpl<T>>()
        for (rule in list) {
            if (rule.isActive(gameContext)) {
                rulesActive.add(rule)
                rulesActive.addAll(determineActiveRules(rule.subRules()).rules)
            }
        }
        return GameRulesActive(rulesActive)
    }

    fun gameStart(): List<GameRuleRuleScope<T>> {
        val activeRules = determineActiveRules(gameRules)
        return activeRules.runGameStart(gameContext) + stateCheck()
    }

    fun stateCheck(): List<GameRuleRuleScope<T>> {
        var loop = 0
        val rulesTriggered = mutableListOf<GameRuleRuleScope<T>>()
        while (loop < 100_000) {
            val activeRules = determineActiveRules(gameRules)
            val result = activeRules.stateCheck(gameContext)
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

}

class GameStartContext<T : Any>(
    val configs: GameConfigs,
    override val game: T,
    override val replayable: ReplayStateI,
    override val playerCount: Int
) : GameStartScope<T> {
    override fun <E : Any> config(config: GameConfig<E>): E = configs.get(config)
}

class GameRuleList<T : Any>(
    val gameContext: GameMetaScope<T>,
): GameAllActionsRule<T> {
    val after = mutableListOf<ActionRuleScope<T, Any>.() -> Unit>()
    val preconditions = mutableListOf<ActionOptionsScope<T>.() -> Boolean>()

    override fun after(rule: ActionRuleScope<T, Any>.() -> Unit) { this.after.add(rule) }
    override fun precondition(rule: ActionOptionsScope<T>.() -> Boolean) { this.preconditions.add(rule) }
}

class ActionOptionsContext<T : Any>(
    val gameContext: GameMetaScope<T>,
    override val actionType: String,
    override val playerIndex: Int,
) : ActionOptionsScope<T>, GameRuleScope<T> by gameContext {
    fun <A: Any> createAction(parameter: A): Actionable<T, A> = Action(game, playerIndex, actionType, parameter)
}

class ActionRuleContext<T : Any, A : Any>(
    val gameContext: GameMetaScope<T>,
    override val action: Actionable<T, A>,
): ActionRuleScope<T, A>, GameRuleScope<T> by gameContext {
    override val meta: GameMetaScope<T> get() = gameContext
    override val playerIndex: Int get() = action.playerIndex
    override val actionType: String get() = action.actionType

    override fun log(logging: LogActionScope<T, A>.() -> String) {
        gameContext.replayable.stateKeeper.log(LogActionContext(game, action.playerIndex, action.parameter).log(logging))
    }
    override fun logSecret(player: PlayerIndex, logging: LogActionScope<T, A>.() -> String): LogSecretActionScope<T, A> {
        val context = LogActionContext(game, player, action.parameter).secretLog(player, logging)
        gameContext.replayable.stateKeeper.log(context)
        return context
    }

    override fun toString(): String = "(ActionRuleContext:$action)"
}

@Deprecated("Replace with GameFlow and SmartAction")
class GameActionRuleContext<T : Any, A : Any>(
    val gameContext: GameMetaScope<T>,
    val actionDefinition: ActionType<T, A>,
    val globalRules: GameRuleList<T>,
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

    override fun forceWhen(rule: ActionOptionsScope<T>.() -> Boolean) {
        val myActionType = actionType
        globalRules.preconditions.add {
            actionType == myActionType.name || !rule(this)
        }
        this.preconditions.add(rule)
    }

    // GameLogicActionType implementation below

    override fun availableActions(playerIndex: Int, sampleSize: ActionSampleSize?): Iterable<Actionable<T, A>> {
        val context = ActionOptionsContext(gameContext, actionType.name, playerIndex)
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
        val context = ActionRuleContext(gameContext, action)
        return checkPreconditions(context) && allowed.all { it(context) }
    }

    override fun performAction(action: Actionable<T, A>): FlowStep.ActionResultStep {
        val result = checkAllowed(action)
        if (!result.allowed) {
            return FlowStep.IllegalAction(action, result)
        }
        val context = ActionRuleContext(gameContext, action)
        this.effects.forEach { it.invoke(context) }
        this.after.forEach { it.invoke(context) }
        this.globalRules.after.forEach { it.invoke(context as ActionRuleScope<T, Any>) }
        return FlowStep.ActionPerformed(action as Actionable<T, Any>, actionType as ActionType<T, Any>, gameContext.replayable.stateKeeper.lastMoveState())
    }

    override fun createAction(playerIndex: Int, parameter: A): Action<T, A>
        = Action(gameContext.game, playerIndex, actionType.name, parameter)

    override fun actionInfoKeys(playerIndex: Int, previouslySelected: List<Any>): List<ActionInfoKey> {
        val context = ActionOptionsContext(gameContext, actionType.name, playerIndex)
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
        val context = ActionOptionsContext(gameContext, actionType.name, playerIndex)
        return ActionComplexImpl(actionType, context, this.choices!!).withChosen(chosen)
    }

    override fun isComplex(): Boolean = this.choices != null

}

package net.zomis.games.dsl.flow

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.api.*
import net.zomis.games.dsl.ActionOptionsScope
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.GameConfig
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.events.EventFactory
import net.zomis.games.dsl.events.EventSource
import net.zomis.games.dsl.events.EventsHandling
import net.zomis.games.dsl.flow.actions.SmartActionBuilder
import net.zomis.games.dsl.flow.actions.SmartActionScope
import net.zomis.games.dsl.impl.Actions
import net.zomis.games.dsl.impl.ReplayState
import net.zomis.games.dsl.rulebased.GameRuleScope

interface GameMetaScope<GameModel: Any>
    : UsageScope,
    GameModelScope<GameModel>,
    MutableEliminationsScope,
    ReplayableScope,
    GameRuleScope<GameModel>,
    EventHandlingScope<GameModel>
{
    // TODO: Extract into a read version and a write version?
    // TODO: currentStep,
    //  available actions,
    //  action handlers
    //  view,
    //  events and handlers,
    //  rule modifiers


    fun <A: Any> addAction(actionType: ActionType<GameModel, A>, handler: SmartActionBuilder<GameModel, A>)
    fun <A: Any> addActionHandler(actionType: ActionType<GameModel, A>, dsl: SmartActionScope<GameModel, A>.() -> Unit)
    fun <A: Any> addAction(actionType: ActionType<GameModel, A>, actionDsl: GameFlowActionDsl<GameModel, A>)

    override val events: EventsHandling<GameModel>
    override val model: GameModel
    override val eliminations: PlayerEliminationsWrite
    override val replayable: ReplayState
    override fun <E: Any> config(config: GameConfig<E>): E = configs.get(config)
    val configs: GameConfigs

    fun injectStep(name: String, dsl: suspend GameFlowStepScope<GameModel>.() -> Unit)
    fun <Owner> addRule(owner: Owner, rule: GameModifierScope<GameModel, Owner>.() -> Unit)
    fun <E: Any> fireEvent(source: EventSource, event: E, performEvent: (E) -> Unit = {})
    fun <Owner> removeRule(rule: GameModifierScope<GameModel, Owner>)
    fun addGlobalActionPrecondition(rule: ActionOptionsScope<GameModel>.() -> Boolean)
}

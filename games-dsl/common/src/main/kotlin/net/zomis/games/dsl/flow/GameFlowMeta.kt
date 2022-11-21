package net.zomis.games.dsl.flow

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.api.*
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameConfig
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.dsl.impl.ReplayState
import net.zomis.games.dsl.rulebased.GameRuleScope

interface GameMetaScope<GameModel: Any>: UsageScope, GameModelScope<GameModel>, MutableEliminationsScope, ReplayableScope, GameRuleScope<GameModel> {
    // TODO: Let GameFlowImpl / GameImpl themselves implement this interface? That would make a lot of sense.


    // TODO: Extract into a read version and a write version?
    // TODO: currentStep,
    //  available actions,
    //  action handlers
    //  view,
    //  events and handlers,
    //  rule modifiers



    override val model: GameModel
    override val eliminations: PlayerEliminationsWrite
    override val replayable: ReplayState
    override fun <E: Any> config(config: GameConfig<E>): E = configs.get(config)
    val configs: GameConfigs

    fun injectStep(name: String, step: GameFlowStepScope<GameModel>.() -> Unit)
    fun <A: Any> forcePerformAction(action: Actionable<GameModel, A>, ruleModifiers: Nothing) { TODO() }
}

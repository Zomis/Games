package net.zomis.games.dsl.flow

import net.zomis.games.PlayerEliminationsWrite
import net.zomis.games.api.UsageScope
import net.zomis.games.dsl.ActionOptionsScope
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.LogScope
import net.zomis.games.dsl.LogSecretScope
import net.zomis.games.dsl.events.EventFactory
import net.zomis.games.dsl.events.EventPriority
import net.zomis.games.dsl.events.GameEventEffectScope
import net.zomis.games.rules.Rule
import net.zomis.games.rules.RuleSpec
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0

interface ActionRule<GameModel : Any, A : Any> {
    fun cost(property: KMutableProperty0<Int>, value: Int)
    fun deny()
}

/**
 * A system for allowing the game itself, and cards or other objects in games to manipulate rules.
 *
 * @param GameModel The game model type
 * @param Owner Parameter passed to the modifier
 */
interface GameModifierScope<GameModel: Any, Owner>: UsageScope {
    // TODO: Add `var name/description: String ?`
    // TODO: Unify ways to specify rules. So that even Action-based `actionRules { ... }` is a "RuleHolder" / "GameModifier"
    val ruleHolder: Owner
    val game: GameModel

    fun <T> state(initial: () -> T): PropertyDelegateProvider<GameModifierScope<GameModel, Owner>?, GameModifierImpl<GameModel, Owner>.Delegate<T>>

    fun conflictsWith(rule: Rule<GameModel, out Any>)
    fun overrides(rule: Rule<GameModel, out Any>)
    fun applyRule(condition: () -> Boolean, rule: RuleSpec<GameModel, out Any>): Rule<GameModel, out Any>

    fun onActivate(doSomething: GameModifierApplyScope<GameModel, Owner>.() -> Unit) // happens once when rule is added
    fun onState(condition: () -> Boolean, thenPerform: GameModifierApplyScope<GameModel, Owner>.() -> Unit)
    fun enableAction(actionDefinition: ActionDefinition<GameModel, out Any>) = enableAction(actionDefinition.actionType)
    fun enableAction(actionType: ActionType<GameModel, out Any>)
    fun stateCheckBeforeAction(doSomething: GameModifierApplyScope<GameModel, Owner>.() -> Unit)
    fun activeWhile(condition: GameModifierScope<GameModel, Owner>.() -> Boolean)
    fun removeWhen(condition: GameModifierScope<GameModel, Owner>.() -> Boolean)
    // TODO: Add/Change/Remove view or part of view (such as seeing other player's cards)
    fun <E: Any> on(event: EventFactory<E>, priority: EventPriority = EventPriority.NORMAL): EventModifierScope<GameModel, E>
    fun <E: Any> on(eventType: KClass<E>, priority: EventPriority = EventPriority.NORMAL): EventModifierScope<GameModel, E>
    fun <A: Any> action(action: ActionDefinition<GameModel, A>, definition: GameFlowActionScope<GameModel, A>.() -> Unit)
    fun <A: Any> action(action: ActionDefinition<GameModel, A>): ActionRule<GameModel, A>
    fun <A: Any> action(action: ActionType<GameModel, A>, definition: GameFlowActionScope<GameModel, A>.() -> Unit)
    // enable/disable entire rule, enable/disable part of rule, change some value - e.g. how much cost reduction is applied
    fun allActionsPrecondition(precondition: ActionOptionsScope<GameModel>.() -> Boolean)
    fun onNoActions(function: () -> Unit)
}

interface GameModifierApplyScope<GameModel: Any, Owner>: UsageScope {
    val ruleHolder: Owner
    val game: GameModel
    val meta: GameMetaScope<GameModel>
    val eliminations: PlayerEliminationsWrite get() = meta.eliminations
    fun log(logging: LogScope<GameModel>.() -> String)
    fun logSecret(playerIndex: Int, logging: LogScope<GameModel>.() -> String): LogSecretScope<GameModel>
}

/*
Game.addTemporaryRules() // next action only
Game.addPermanentRules()

This applies to all rule holders. (entities, steps, game)
Rule Holder knows both GameModel and T (entity-specific value, e.g. owner + card)

Game rules
Cards may change game rules
Cards may change how other cards behave/work

Rule kinds:
- View modifications (show, hide, changeValue?)
- Actions precond/require/perform
  - cost/modifier
- Event trigger/effect
  - Modifier
- Resource Map modifiers
- Static checks
  - For each X (for each player with health <= 0)
  - Apply on X if X fulfills some condition (winner in TTT/TTT3D)
  - Apply while (as long as there is any rule that has being executed, check again)
  - Apply once (if player has more than 10 money, force discard money?)
- Do once (game start / card play effects)
- Once per X -- save latest value or all values (both options may exist, but latest is more common)

gameFlowRules
- UR, Coup, Dixit, TTT Upgrade, King Domino, Backgammon,

step rules
- UR, Dixit, TTT Upgrade, King Domino, Backgammon, Wordle, Alchemists

context rules
- Alchemists

actionRules
- TTT, Connect4, Reversi, UTTT, TTT3D, Quixo, Artax, Hanabi, Splendor, Skull, Dungeon Mayhem, Liar's Dice,
  Avalon, Set, Spice Road,

old triggers
- Dungeon Mayhem

multiple rule sources
- Dungeon Mayhem, Avalon(?), Alchemists

(Once again a help for tagging games would be useful)
*/

// TODO: RuleHolder - game (permanent effects) / step (phase-specific actions etc.) / entity (card etc.)
// TODO: Add/Remove StepRuleHolders every time step changes

interface EventModifierScope<T: Any, E: Any>: UsageScope {
    fun require(condition: GameEventEffectScope<T, E>.() -> Boolean)

    fun modify(function: GameEventEffectScope<T, E>.() -> E)
    fun mutate(function: GameEventEffectScope<T, E>.() -> Unit)

    fun perform(listener: GameEventEffectScope<T, E>.() -> Unit)

}

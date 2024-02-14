package net.zomis.games.ecsmodel

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.context.*
import net.zomis.games.v4.ChoiceDef

object GameExamples {
    // TODO: Instead of creating yet-another-system from scratch, improve the existing Context-based system.

    class ObservableStateExample(override val ctx: Context) : Entity(ctx), ContextHolder {
        var value by component { 0 }.privateView(0)
        // TODO: Rules, apply phases/stack/... All current games work like that. GameFlow-games can be changed to apply rules based on phase/stack.
    }
    val damageChoice = ChoiceDef<Int>()

    private val conflictingFactory = GamesApi.gameCreator(ConflictingRules::class)
    class ConflictingRules(override val ctx: Context) : Entity(ctx), ContextHolder {
        var energy by component { 0 }
        var damage by component { 0 }
        val rest by action {
            perform { energy++ }
        }
        val other by action {
//            cost(::energy, 2).min(1)
            perform { damage++ }
//            damageChoice.choose { 1..energy }
//            perform { damage += damageChoice.chosen }
//            perform {
//                addRule {
//                     until(event) / state / value changes
//                }
//            }
        }
        /*
        * run after action rules
        * 1. check applicable rules
        * 2. add actions, event listeners
        * last: state checks
        *
        * switch rules
        *
        * run before action rules
        * action
        */


        val rule by rule<ConflictingRules, ConflictingRules>(this) {
            onState(condition = { damage >= 10 }) { eliminations.eliminateRemaining(WinResult.WIN) }
            enableAction(other)
            enableAction(rest)
            action(other).cost(::energy, -1)
            val rule1 = applyRule(condition = { energy <= 2 }) {
                action(other).deny()
                // force "rest" action
                allActionsPrecondition { actionType == rest.actionType.name }
            }
            val rule2 = applyRule(condition = { energy >= 2 }) {
                conflictsWith(rule1) // Don't enable this if rule1 is active
                overrides(rule1) // Disable rule1 in favor of this
                // prevent "rest" action
                action(rest) {
                    precondition { false }
                }
            }
            if (rule1.isActive() && rule2.isActive()) {
                rule2.disable() // Another way of preventing rule2 from being active
            }
        }
    }

    val observable = GamesApi.gameContext("observable-game", ObservableStateExample::class) {
        players(1..2)
        init { ObservableStateExample(ctx) }
    }
    val rules = GamesApi.gameContext("rules-example", ConflictingRules::class) {
        players(1..2)
        init { ConflictingRules(ctx) }
    }

}
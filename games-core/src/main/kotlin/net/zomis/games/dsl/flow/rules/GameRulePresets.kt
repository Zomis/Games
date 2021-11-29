package net.zomis.games.dsl.flow.rules

import net.zomis.games.WinResult
import net.zomis.games.common.next
import net.zomis.games.dsl.ActionRuleScope
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.flow.GameFlowRulesContext
import net.zomis.games.dsl.rulebased.GameRuleScope
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0

interface GameRulePresets<T: Any> {
    val players: Players<T>

    interface Players<T: Any> {
        fun singleWinner(winner: GameRuleScope<T>.() -> Int?)
        fun lastPlayerStanding()
        @Deprecated("non-optimal API, use 'losing' instead")
        fun losingPlayers(playerIndices: GameRuleScope<T>.() -> Iterable<Int>)
        fun losing(isLoss: GameRuleScope<T>.(Int) -> Boolean)
        fun skipEliminated(property: GameRuleScope<T>.() -> KMutableProperty<Int>)
    }

    interface Actions<T: Any, A: Any> {
        fun filtered(actionFilter: ActionRuleScope<T, A>.() -> Boolean): Actions<T, A>
        fun cost(
            cost: ActionRuleScope<T, A>.() -> Int,
            property: ActionRuleScope<T, A>.() -> KMutableProperty0<Int>
        )
    }

    fun <A: Any> action(actionType: ActionType<T, A>): Actions<T, A>
}

class GameRulePresetsActionsImpl<T: Any, A: Any>(
    private val context: GameFlowRulesContext<T>,
    private val actionType: ActionType<T, A>,
    private val filter: ActionRuleScope<T, A>.() -> Boolean
): GameRulePresets.Actions<T, A> {
    override fun filtered(actionFilter: ActionRuleScope<T, A>.() -> Boolean): GameRulePresets.Actions<T, A> {
        val previousFilter = this.filter
        return GameRulePresetsActionsImpl(context, actionType) {
            previousFilter.invoke(this) && actionFilter.invoke(this)
        }
    }

    override fun cost(cost: ActionRuleScope<T, A>.() -> Int, property: ActionRuleScope<T, A>.() -> KMutableProperty0<Int>) {
        context.afterActionRule("cost for ${actionType.name}") {
            action(actionType) {
                requires {
                    val filterMatch = filter.invoke(this)
                    !filterMatch || property.invoke(this).get() >= cost.invoke(this)
                }
                perform {
                    if (filter.invoke(this)) {
                        val value = property.invoke(this)
                        value.set(value.get() - cost.invoke(this))
                    }
                }
            }
        }
    }

}

class GameRulePresetsImpl<T: Any>(private val context: GameFlowRulesContext<T>): GameRulePresets<T>, GameRulePresets.Players<T> {
    override val players: GameRulePresets.Players<T> = this

    override fun singleWinner(winner: GameRuleScope<T>.() -> Int?) {
        context.rule("declare winner") {
            appliesWhen { winner(this) != null && !eliminations.isGameOver() }
            effect { eliminations.singleWinner(winner(this)!!) }
        }
    }

    override fun lastPlayerStanding() {
        context.rule("last player standing wins") {
            appliesWhen { eliminations.remainingPlayers().size == 1 }
            effect { eliminations.eliminateRemaining(WinResult.WIN) }
        }
    }

    override fun losingPlayers(playerIndices: GameRuleScope<T>.() -> Iterable<Int>) {
        context.rule("eliminate losing players") {
            appliesWhen { playerIndices().filter { eliminations.isAlive(it) }.any() }
            effect {
                val eliminating = playerIndices().filter { eliminations.isAlive(it) }
                println("Eliminate losing players: $eliminating")
                eliminations.eliminateMany(eliminating, WinResult.LOSS)
            }
        }
    }

    override fun losing(isLoss: GameRuleScope<T>.(Int) -> Boolean) {
        context.rule("eliminate losing players") {
            appliesWhen {
                eliminations.remainingPlayers().any { isLoss.invoke(this, it) }
            }
            effect {
                val losing = eliminations.remainingPlayers().filter { isLoss.invoke(this, it) }
                eliminations.eliminateMany(losing, WinResult.LOSS)
            }
        }
    }

    override fun <A : Any> action(actionType: ActionType<T, A>): GameRulePresets.Actions<T, A>
        = GameRulePresetsActionsImpl(this.context, actionType) { true }

    override fun skipEliminated(property: GameRuleScope<T>.() -> KMutableProperty<Int>) {
        context.rule("skip eliminated players") {
            // TODO: This should be `appliesWhile` or something, or try to execute (some) rules multiple times
            appliesWhen {
                val prop = property.invoke(this)
                !eliminations.isGameOver() && !eliminations.remainingPlayers().contains(prop.getter.call())
            }
            effect {
                val prop = property.invoke(this)
                while (!eliminations.remainingPlayers().contains(prop.getter.call())) {
                    prop.setter.call(prop.getter.call().next(eliminations.playerCount))
                }
            }
        }
    }

}

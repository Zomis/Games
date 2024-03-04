package net.zomis.games.rules

import net.zomis.games.dsl.flow.GameModifierImpl
import net.zomis.games.dsl.flow.GameModifierScope

typealias RuleSpec<GameModel, Owner> = GameModifierScope<GameModel, Owner>.() -> Unit
typealias Rule<GameModel, Owner> = GameModifierImpl<GameModel, Owner>

fun <GameModel : Any, Owner> RuleSpec(name: String, lambda: RuleSpec<GameModel, Owner>): RuleSpec<GameModel, Owner> = {
    this.name = name
    lambda.invoke(this)
}

class StatefulRule<GameModel : Any, Owner>(val ruleSpec: RuleSpec<GameModel, Owner>, val state: Map<String, Any>)


/*
* Rules can be...
* - owned
* - stateful
* - dynamically owned (i.e. associated with anyone based on game state)
* - unowned, and instead pass owner while executing rule!
*
* - aware of meta-object
* */


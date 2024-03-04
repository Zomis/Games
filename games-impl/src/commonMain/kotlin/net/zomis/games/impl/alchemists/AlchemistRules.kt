package net.zomis.games.impl.alchemists

import net.zomis.games.rules.NoState
import net.zomis.games.rules.RuleSpec

object AlchemistRules {

    val baseRule: RuleSpec<AlchemistsDelegationGame.Model, Unit> = RuleSpec("base rule") {
        subRule(assertions, Unit, NoState)
        if (game.stack.isNotEmpty()) {
            game.stack.peek()!!.ruleSpec.invoke(this)
        } else {
            val phaseRule = subRule(game.phase.current.ruleSpec, Unit, NoState)
            subRule(Favors.herbalistRule(phaseRule), Unit, NoState)
        }

        game.players.forEach { player ->
            player.artifacts.cards.forEach {  artifact ->
                subRule(artifact.rule, ArtifactActions.OwnedArtifact(player, artifact), player.stateOwner)
            }
        }

        onNoActions {
            // println("NO ACTIONS: ${game.stack} // ${game.phase}")
            game.stack.popOrNull() ?: game.phase.next()
        }
    }

    private val assertions = RuleSpec<AlchemistsDelegationGame.Model, Unit>("assertions") {
        stateCheckBeforeAction {
            val favors = game.favors.discardPile.size + game.favors.favorsPlayed.size + game.favors.deck.size + game.players.sumOf { it.favors.size }
            val ingredients = game.ingredients.discardPile.size + game.ingredients.slots.size + game.ingredients.deck.size + game.players.sumOf { it.ingredients.size }
            check(favors == 22) { "Wrong amount of favors: $favors" }
            check(ingredients == 40) { "Wrong amount of ingredients: $ingredients" }
        }
    }

}
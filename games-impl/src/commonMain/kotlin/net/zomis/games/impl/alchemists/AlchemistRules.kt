package net.zomis.games.impl.alchemists

import net.zomis.games.rules.NoState
import net.zomis.games.rules.RuleSpec

object AlchemistRules {

    val baseRule: RuleSpec<AlchemistsDelegationGame.Model, Unit> = {
        if (game.stack.isNotEmpty()) {
            game.stack.peek()!!.ruleSpec.invoke(this)
        }
        subRule(Favors.herbalistRule, Unit, NoState)

        game.players.forEach { player ->
            player.artifacts.cards.forEach {  artifact ->
                subRule(artifact.rule, ArtifactActions.OwnedArtifact(player, artifact), player.stateOwner)
            }
        }

        onNoActions { game.stack.popOrNull() ?: game.phase.next() }
    }

}
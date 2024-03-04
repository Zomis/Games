package net.zomis.games.impl.alchemists

import net.zomis.games.rules.RuleSpec

object Phases {

    sealed class Phase(val ruleSpec: RuleSpec<AlchemistsDelegationGame.Model, Unit>) {
        object Setup : Phase({
            name = "setup phase"
            println("Run setup rule")
            // Discard favors step. Next step self - discarding player, until none are left.
            game.stack.add(Favors.FavorDiscard(game.players.toMutableList()))
            game.stack.add(Favors.FavorDiscard(game.players.toMutableList()))
            stateCheckBeforeAction {
                println("SOLUTION: " + game.alchemySolution)
                game.gameInit.invoke(Unit)
                game.phase.next()
            }
        })
        data class PrepareRound(val round: Int) : Phase({
            name = "prepare round phase $round"
            stateCheckBeforeAction {
                game.newRound(round)
                game.sellPotion.reset()
                log { "Round $round" }
            }
        })
        data class ChooseTurnOrder(val round: Int) : Phase({
            name = "turn order phase $round"
            action(game.turnPicker.action)
//            .loopUntil { game.players.indices.all { player -> game.turnPicker.options.any { it.chosenBy == player } } }
        })
        data class PlaceActions(val round: Int) : Phase({
            name = "place actions phase $round"
            // Sequential nested step, place actions. Next step self(with nextPlayer) until all players placed.
            action(game.actionPlacement)
            action(game.favors.assistant)
//            .loopUntil { game.turnPicker.options.all { it.chosenBy == null } }
        })
        data class ResolveActions(val round: Int, val space: AlchemistsDelegationGame.HasAction) : Phase({
            name = "resolve actions $round ${space.actionSpace.name}"
            // Sequential nested step, resolve spaces. Next step self until all spaces are done.
            game.currentActionSpace = space
            action(space.action)
            action(game.cancelAction(space))
            space.extraActions().forEach { action(it) }
            space.extraHandlers().forEach {
                actionHandler(it.first, it.second)
            }
//            .loopUntil {
//                action?.parameter !is Favors.FavorType
//                        && game.stack.isEmpty()
//                        && space.actionSpace.rows.all { it == null || it.cubes.all { cubes -> cubes.used } }
//            }
            game.spaceDone.invoke(space)
        })

        object BigRevelation : Phase({
            name = "big revelation phase"
            stateCheckBeforeAction {
                game.players.forEach {  player ->
                    player.resources[AlchemistsDelegationGame.Resources.VictoryPoints] =
                        player.artifacts.cards.sumOf { it.victoryPoints ?: 0 } +
                                player.reputation +
                                player.gold / 3
                }

                /*
                  VP = Reputation + Magic Mirror + Artifacts + Feather in Cap + Crystal Cabinet +
                    Grants + Gold + Big Revelation + Wisdom Idol
                */
                val playerScores = game.players.map { it.playerIndex to (it.resources[AlchemistsDelegationGame.Resources.VictoryPoints] ?: 0) }
                eliminations.eliminateBy(playerScores, compareBy { it })
            }
        })
    }

    fun phases(game: AlchemistsDelegationGame.Model): Sequence<Phase> = sequence {
        yield(Phase.Setup)
        for (it in 1 until 6) {
            yield(Phase.PrepareRound(it))
            yield(Phase.ChooseTurnOrder(it))
            yield(Phase.PlaceActions(it))
            for (space in game.actionSpaces) {
                yield(Phase.ResolveActions(it, space))
            }
        }
        yield(Phase.BigRevelation)
    }

}
package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi

class GameStack {
    val stack = mutableListOf<Any>()
    // Stack in model, use rules as normal (either action-based or rule-based)
    // Choice, Triggers, Effects... player decisions (changable until cleared?)
}

object CoupRuleBased {

    val factory = GamesApi.gameCreator(Coup::class)
    val perform = factory.action("perform", CoupAction::class).serializer { it.action.name + "-" + it.target?.playerIndex }
    val counter = factory.action("counteract", CoupCharacter::class).serializer { it.name }
    val approve = factory.action("approve", Unit::class)
    val reveal = factory.action("reveal", Unit::class)
    val challenge = factory.action("challenge", Unit::class)
    val ambassadorPutBack = factory.action("putBack", CoupCharacter::class).serializer { it.name }
    val loseInfluence = factory.action("lose", CoupCharacter::class).serializer { it.name }

    val model = Coup(3)
    val game = factory.game("Coup") {
        setup {
            players(2..6)
            init {
                Coup(playerCount)
            }
        }
        rules {
            allActions.after { eliminations.eliminateRemaining() }
            rule("last player standing") {
                stateTrigger {
                    eliminations.remainingPlayers().size == 1
                }.effect {
                    eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
            rule("eliminate player") {
                appliesForEach {
                    game.players.filter { it.influence.size == 0 && eliminations.remainingContains(it.playerIndex) }
                }.effect {
                    eliminations.eliminate(player, WinResult.LOSS)
                }
            }
            rule("setup") {
                gameSetup {
                    model.players.forEach { player ->
                        val influence = model.deck.random(replayable, 2, "start-" + player.playerIndex) { it.name }
                        influence.forEach { it.moveTo(player.influence) }
                    }
                }
                gameSetup {
                    model.players.forEach { it.coins = 2 }
                }
            }
            rule("lose influence") {
                triggerr(loseInfluenceTrigger).effect {
                    forceChoice {
                        val triggerPlayer = CoupPlayer(0) // TODO get trigger value
                        player(triggerPlayer.playerIndex)
                        choice(triggerPlayer.influence.cards) { // TODO: Doing this should be considered an action, and recorded properly
                            triggerPlayer.influence.card(choice).moveTo(triggerPlayer.previousInfluence)
                        }
                    }
                }
            }
            rule("coup requires money") {
            }
            rule("assassinate requires money") {
            }
            rule("must perform coup") {
                appliesWhen { game.currentPlayer.coins >= 10 }
                action(perform) {
                    // TODO: Need to modify available options somehow...
                    denyOptions { CoupActionType.values().toList().minus(CoupActionType.COUP) }
                }
            }
            rule("choose action") {
                appliesWhen { game.stack.isEmpty() }
                action(perform) {
                    enforce()
                    precondition { game.currentPlayerIndex == playerIndex }
                    options {
                        choose {
                            optionsWithIds({ CoupActionType.values().asIterable().map { it.name to it } }) {type ->
                                options({ game.players }) {target -> // TODO: Only pick targets for types needing it
                                    parameter(CoupAction(type, target))
                                }
                            }
                        }
                    }
                    effect {
                        game.stack.add(action.parameter)
                        game.stack.add(CoupChallengeOrCounteract(game, action.parameter, null))
                    }
                }
            }
            rule("allow challenge") {
                appliesWhen { game.stack.peek() is CoupChallengeOrCounteract }
                action(approve) {
                    allow()
                }
                action(challenge) {
                    allow()
                }

            }
            rule("resolve challenge") {
                appliesWhen {
                    val decision = game.stack.peek() as CoupChallengeOrCounteract?
                    decision.isDecided()
                }
                effect {
                    // pop stack, potentially triggering a CoupRevealOrDie, or a CoupCounterAction
                }
            }
            rule("reveal or lose influence") {
                appliesWhen {
                    game.stack.peek() is CoupRevealOrDie
                }
                action(reveal) { allow() }
                action(loseInfluence) { allow() }
            }
            // counteract should still allow challenges
            // challenging returns coins, counteraction does not (Assassination)
        }
    }

}
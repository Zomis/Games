package net.zomis.games.ecsmodel

import kotlinx.coroutines.flow.MutableStateFlow

object EcsModelExample {

    val game: EcsGameSpec<EcsModelRoot> = EcsGameApi.create("test") {
        playerCount(1..4)
        create {
            EcsModelRoot().apply {
                val card1 = EcsModelCard(4, Rule()).apply {
                    energyCost = 4
//                    ruleInPlay = rule {
//                        rest.deny()
//                    }.activeWhen { energy == 1 }
                }
                active.add(card1)
                active.add(EcsModelCard(1, Rule()).apply {
                    energyCost = 1
//                    ruleInPlay = rule {
//                        allActions.precondition { actionType == rest }
//                        disableRule(card1.ruleInPlay)
//                    }.activeWhen { energy <= 3 }
                })
            }
        }
    }

    context(Game)
    class EcsModelCard(energyCost: Int, ruleInPlay: Rule) : GameModelEntity() {
        var energyCost by property<Int> { energyCost }
        var ruleInPlay by property<Rule> { ruleInPlay }
    }

    context(Game)
    class EcsModelRoot : GameModelEntity() {

        val active by cardZone<EcsModelCard>()
        val hand by cardZone<EcsModelCard>()
        var energy by property { 0 }
        val rest by action {
//            onPerform {
//                energy += 5
//            }
        }

        val rule by rule {
            action(rest)
            applyRules(active.cards.map { it.ruleInPlay })
        }
//        val cardChoice = actionChoice { hand.cards }
        val action by action {
//            choice(cardChoice)
//            requireChosen(cardChoice) {
//                cost(::energy) { cardChoice.chosen.energyCost }
//            }
//            onPerform {
//                hand.card(cardChoice.chosen).moveTo(active)
//            }

            // specify choices
            // specify what happens when it is performed (but have a way to intercept/cancel)

            /*
            * Card Examples:
            * - rule with state: After three turns, you lose the game
            * - cost: Cards you play cost 1 less energy
            * - choice/cost: Pay X energy, draw X cards
            * - choice: You may play two copies of this card
            * - alter: If card X is in play, this enters with the rule ...
            * - choice: Before/When you play a card, you may destroy a card
            * - alter: After you play a card, you may destroy a card
            * - alter: "Card X cannot be played"
            */
        }
    }

}
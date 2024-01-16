package net.zomis.games.ecsmodel

import net.zomis.games.dsl.ActionType
import org.junit.jupiter.api.Test

class AdvancedRulesTest {
    interface AdvancedRule {
        fun modifyActionChoice(choice: AdvancedActionChoice, change: (List<Any>) -> List<Any>)
        fun onPerform(action: ActionType<*, *>)
    }
    interface AdvancedActionChoice {

    }
    interface AdvancedEffect {
        class SequentialEffect(val effects: List<AdvancedEffect>)
        class ConditionalEffect(val condition: () -> Boolean, val ifTrue: AdvancedEffect, val otherwise: AdvancedEffect)
        class ChoiceEffect(val choices: List<AdvancedEffect>)
        class EventEffect(val event: () -> Any)
    }

    interface GameLookup
    class Ref(val entity: RPath) {
        context(GameLookup)
        fun entity(): GameModelEntity = TODO()
    }
    class Refs(val entities: List<GameModelEntity>)

    interface AdvancedActionRule {
        /*
        * change allowed or not
        * add/modify choices (pay an energy to target...)
        * remove other action rules or give possibility to remove other action rules (discard card to ignore...)
        * requires some state (use up to X times...)
        *
        * allow other players to affect things (determine X may count as Y)
        *
        */
    }

    @Test
    fun `games should be customizable during setup`() {
        TODO("change a card, or deck, while setting up the game. ban cards, add card, add new rules...")
    }

    @Test
    fun `games may be customizable while playing`() {
        TODO("change player properties, change what a card does, ban cards, add card, add new rules...")
    }

    @Test
    fun `games must have a way to resolve conflicting rules`() {
        TODO("e.g. Grizzled: Fearful + Prideful/Fragile/Hardheaded")

        // Make a simple card game: one card saying "If A, you have to rest" another saying "If B, you cannot rest"
    }

    @Test
    fun `some rules may ignore other rules`() {
        TODO("e.g. ignore one defensive trait")
    }

    @Test
    fun `when choosing an action a rule may change what you can choose`() {
        TODO("e.g. may only attack Climbing if you have Climbing. Spirit Island increased range, add destroyed presence during growth")
    }

    @Test
    fun `when choosing an action a rule may allow you to do X instead`() {
        TODO("e.g. you may discard a card instead of paying two energy, or pay an energy to target something else")
    }

    @Test
    fun `x may count as y - tokens`() {
        // FindEvent? pause it on the stack then decide.
        TODO("e.g. sacred site counts as beasts + Teeth Gleam from Darkness: If target land has both Beasts and Invaders: 3 Fear.")
    }

    class FindEvent(val token: Any, val searchLands: List<Any>)

    @Test
    fun `x may count as y - switch property on action`() {
        // A: "This creature may only attack creatures with defense 6 or less."
        // B: "The defense value of this creature is whatever you want it to be"
        // A: I want to attack with my creature against your creature
        // B: My creature has defense 10. You can't do that.

        // A: What can I attack with this creature?
        // Game: Let me fire an event and check...
        TODO("e.g. fast is slow or slow is fast")
    }

    @Test
    fun `rule prevents something from happening`() {
        TODO("e.g. prevent destruction of pieces/cards")
    }

    @Test
    fun `rule allows something to be repeated`() {
        TODO("e.g. Boots of Speed in Alchemists, or repeat powers in Spirit Island")
    }

    @Test
    fun `rule allows doing something during event, possibly to alter event`() {
        TODO("e.g. destroy presence to prevent ravage/build")
    }

    @Test
    fun `rule allows dynamic ResourceMap change`() {
        TODO("e.g. +1 health for every X, or +1 health until end of turn")
    }

    @Test
    fun `a rule may change what something else does or has`() {
        TODO("e.g. the card 'test' has '4 damage', or 'all creatures has +1 health'")
    }

    @Test
    fun `rule modifies what an action does`() {
        TODO("e.g. *when* (not after) X would be destroyed, also do Y")
        // See https://querki.net/u/darker/spirit-island-faq/#!.7w4geev
    }

    @Test
    fun `rule changes what another action does`() {
        // option change
        TODO("e.g. instead of forgetting a card, you may discard it instead")
    }

}
## 1. Dedicated server handler

First seen in commit 95964c8b4cf427a8cdf09abe42f709e2d6669477

This approach was based on implementing a dedicated class for making moves, initializing the game, firing events to inform client about state change.

Source:
- https://github.com/Zomis/Games/tree/0121ee2eed61640c12aff0cdde03cde1e82c0493/games-server/src/main/kotlin/net/zomis/games/server2/games/impl
- https://github.com/Zomis/Games/blob/0121ee2eed61640c12aff0cdde03cde1e82c0493/games-server/src/main/kotlin/net/zomis/games/server2/clients/ur/RandomUrBot.kt
- https://github.com/Zomis/Games/blob/4322a1f30eb7fa81213ca8a28d87482e2032369f/games-server/src/main/kotlin/net/zomis/games/server2/games/impl/TTControllerSystem.kt

Advantages:
- Full control over each game. Unlimited adaptibility.

Disadvantages:
- Incredibly much copy-paste. A lot of similarities.
- Very low-level, directly impacting which messages are sent to clients
- No view abstraction
- No database abstraction
- No automatic replayability support

## 2. DSL with Actions (`logic`)

AI Random for all DSL games: ca73207638c90a08d679f45ba9b53b1629029967

TT-games: 7c2fd4ed08f3bcedc5a35327f352a65e5ae24565

Client refactoring to put all supported games in one file: 3345bd4969566ce0aad1534a68971ce7e36c17a1

Advantages:
+ View abstraction, database abstraction, and replayability support
+ No low-level events, making it easier to have several different clients (e.g. Console / Server / ...)

Disadvantages:
- Some games get very complicated and still has lots of repetition in the rules, about what phase it is or similar
- Can be hard to find what went wrong, or where to change, or which thing is responsible for something

## 3. DSL with Rules (`rules/actionRules`)

This was a refactoring that focused on making the code more easy to read and write,
so that instead of having one giant `allowed` section with ALL the conditions for whether an action was allowed,
it allowed specifying multiple statements that would be combined with the AND operator by the framework.

First seen in Skull: 790b399ccb8a2d4ffea507f4816717adc50d0e62 (parent commit implemented feature itself)

Hanabi rewrite: 38639f39f9bfcec2b91c04de4d1f27e3b9ed3687

Advantages:
+ Some separations of concerns, having individual rules be responsible for actions, or when to eliminate players, etc.

Disadvantages:
- No easy interaction between rules
- Can be hard to find what went wrong, or where to change, or which thing is responsible for something

## 4. DSL with GameFlow

This change focused on making the progress of a game easier to read.
So that instead of defining rules action-per-action,
the game can be written as if it was a synchronous method stepping through the game, e.g.:

    gameFlow {
        for (i in 1..10) { // Game has ten rounds in total
            step("roll some die") {
                yieldAction(roll) {
                    ...
                }
            }
            step("move piece") {
                yieldAction(move) {
                    ...
                }
            }
        }
    }

First seen in Royal game of Ur: https://github.com/Zomis/Games/commit/68b2e22ddefba28fdd3ceb13efc374f013e1d023#diff-d82d218271facf9c8e208f744a215b85a068cb4658dbcaa55c35daf66a6ddd49R28

Advantages:
+ Makes the flow of the game much clearer

Disadvantages:
- For many games, it's just a `loop` with the same step over and over again, which doesn't provide any value
- Manipulation and interaction between different aspects of the game becomes complicated

## 5. DSL with Context Delegation

This focused on centralizing the functionality related to one component, such as a deck of cards,
to one place instead of spreading things out over several places such as:

- What it _is_ (the model)
- What players should see (the view)
- Setup (e.g. getting a random set of cards)
- How it should change (by an action or an event)

First seen in Alchemists:
https://github.com/Zomis/Games/blob/057509d67de275d65d4feb00aa91dc48f93eb999/games-core/src/main/kotlin/net/zomis/games/impl/alchemists/AlchemistsDelegationGame.kt

Advantages:
+ Fully type-safe
+ All control over a component (e.g. a card zone) can be placed directly at the definition of the cards. Including setup, randomness, end of turn effects...

Disadvantages:
- Not easily moddable or configurable to change different aspects, such as making a variant with a color property on all players and pieces

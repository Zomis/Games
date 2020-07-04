# Zomis' Games

One of the goals with this project is to easily create implementations of several turn-based multiplayer games, both logic and GUI.

This project is a flexible server meant for games. The main idea is to re-use a lot of things between games, so that you don't have to implement these things for every game:

- Online Multiplayer
- AIs
- Replay feature to watch old games
- Statistics (games played, opponents, wins and losses...)

To accomplish this, I realized that a game consists of three different parts:

- The game model
- Which actions are allowed and what they do
- What each player is allowed to see

These three things: Model, Actions, View, are specified in a DSL (Domain Specific Language). The DSL only works as a wrapper around your game implementation, so no matter how complex or simple your game is, it's possible to build it in this DSL.

For this project I decided to use Kotlin in order to be able to re-use code between the server and the client.

## If you just want to play...

[Play Zomis' Games](https://games.zomis.net)

[**Miss a game?**](https://github.com/Zomis/Games/issues/5)

Existing features include:

- Database which stores all games
- Online Multiplayer
- AIs
- 8+ different games implemented

## Building and running locally

### Running the server locally

To run the server:

    ./gradlew :games-server:run

OR

    ./gradlew :games-server:assemble
    java -jar games-server/build/libs/games-server-1.0-SNAPSHOT-all.jar

### Running the client locally:

    ./gradlew :games-js:assemble # this compiles Kotlin code to JavaScript
    cd games-vue-client
    npm run serve

## Adding a new game

One of the main goals of this project is that it should be easy to implement a new game in it.

To implement a new game you'll need to add:

- Kotlin code written in the Game-DSL specifying the game model, the game actions, and the game view.
- Mapping in [ServerGames.kt](https://github.com/Zomis/Games/blob/master/games-server/src/main/kotlin/net/zomis/games/server2/ServerGames.kt)
- Vue component to display the game state and fire actions
- Mapping in [supportedGames.js](https://github.com/Zomis/Games/blob/master/games-vue-client/src/supportedGames.js)

## Lessons learned from previous projects

See [Lessons learned documentation](https://github.com/Zomis/Games/blob/master/documentation/LESSONS_LEARNED.md)

## Some decisions made

See [Decisions made documentation](https://github.com/Zomis/Games/blob/master/documentation/DECISIONS_MADE.md)

### Documentation and test-cases at the same time

I wrote my own tool to generate documentation for how the client and server communicates. As I am sometimes not motivated to write test-cases and even less motivated to write documentation, I figured that I might as well generate the documentation from the test-cases. I have integrated a check in my build pipeline to make sure that the documentation is not outdated.

## TODO-list / Roadmap / Future features

- As Kotlin can be run dynamically using the JVM scripting engine, it would be possible to give advanced players access to this so that they can customize the starting state, or adjust stuff along the way.
- Undo button (opponents needs to agree, naturally)
- Reinforcement Learning AI: Given a game state, learn what the best move is.
- More games!

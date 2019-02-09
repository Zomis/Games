# Server2

## If you just want to play...

[Play Zomis' Games](http://games.zomis.net)

## Building and running locally

To run the server:

    ./gradlew :games-server:run

OR

    ./gradlew :games-server:assemble
    java -jar games-server/build/libs/games-server-1.0-SNAPSHOT-all.jar

### To run the client locally:

    ./gradlew :games-js:assemble # this compiles Kotlin code to JavaScript
    cd games-vue-client
    npm run dev

## What is this?

This project is a flexible server meant for games. It uses an event-based approach which is possible to use for any other application. The main idea is that it should be easy to add new features or new game implementations without changing in any other existing files, except the "Server2.kt" which is what bootstraps everything.

One of the goals with this project is to easily create implementations of several turn-based 2d multiplayer games, both logic and GUI.

I am using Kotlin which allows me to also compile game-logic into JavaScript and use it in the games-vue-client project.

## Why the event-based approach?

It allows me to dynamically inject new functionality (hopefully) without disturbing existing functionality.
This is especially benificial for tests as you can dynamically insert functionality that is useful for automatic tests, and you can choose at what level you want your tests to run at - you can execute any events in your tests and the implementation you specified will handle them.
It is also possible that in the future I will with the event-based functionality re-use more logic between frontend and backend.

## Documentation and test-cases at the same time

I wrote my own tool to generate documentation for how the client and server communicates. As I am sometimes not motivated to write test-cases and even less motivated to write documentation, I figured that I might as well generate the documentation from the test-cases. I have integrated a check in my build pipeline to make sure that the documentation is not outdated.

## Future possibilities and random notes

* As each listener has a description, it would be possible to check if there is a `System.getProperty`
  for a cleaned version of that description and if it has a specific value then don't add the listener (automatic feature-toggling)
* Make more of the Server-code be shared between clients, such as the InviteSystem, GamesSystem, etc.
  Then it would be possible to construct a Client in JavaScript and the server as well, and use more of the real server-code in JavaScript (to avoid making local games special cases)
* Use an Entity Component System approach in both the server-server code and in the games code
  If ECS would be used throughout, then a WebSocketClient or CommunicationClient could just be added to the Player Entity
  A Game could be the same Entity in both player-logic and server-logic. Would there be any downside to this?

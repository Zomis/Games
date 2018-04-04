# Server2

## If you just want to run it...

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

# Server2

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
- Mapping in [ServerGames.kt](https://github.com/Zomis/Server2/blob/master/games-server/src/main/kotlin/net/zomis/games/server2/ServerGames.kt)
- Vue component to display the game state and fire actions
- Mapping in [supportedGames.js](https://github.com/Zomis/Server2/blob/master/games-vue-client/src/supportedGames.js)

## Lessons learned from previous projects

As I have done many implementations of Servers, and many implementations of especially Tic-Tac-Toe and Ultimate Tic-Tac-Toe, here are some lessons learned from earlier projects:

- Use JSON for communication between client and server. Trying to compress the messages to save bandwidth makes it a lot more difficult to troubleshoot and adds an extra layer of complexity.
 Sometimes the JSON messages can be quite big (especially for complex games), but deal with that issue when it becomes a problem. A possible solution to that would be to only send the data that changes, which has different solutions. (Compressing of some sort has been used in [Minesweeper Flags Extreme](https://github.com/Zomis/minesweeper-flags-client/) and [Cardshifter](https://github.com/Cardshifter/Cardshifter/))
- For each type of action, handle it separately. Don't have a big if-else containing all the action types. (This mistake was made in [flexgame-server](https://github.com/Zomis/flexgame-server/blob/first/flexgame-server/src/main/java/net/zomis/spring/games/impls/ur/RoyalGameOfUrHelper.java#L101))
- Separate game logic from server logic
  - Example: Don't put the burden on a specific game handler to return "Game is full" error (Mistake done in [flexgame-server](https://github.com/Zomis/flexgame-server/blob/first/flexgame-server/src/main/java/net/zomis/spring/games/impls/ur/RoyalGameOfUrHelper.java#L34))
- Java is very verbose and Groovy is too dynamic. Kotlin is amazing. (Groovy was used in [flexgame-server](https://github.com/Zomis/flexgame-server/) and in [Cardshifter](https://github.com/Cardshifter/Cardshifter/))
- Using a dynamic event-driven approach for *everything*... (This was initially done in this project, but the approach has since changed but some traces of this might still remain)
  - makes code very unreadable and confusing
  - makes it difficult to figure out or troubleshoot "when A happens, B will happen"
  - is still an interesting concept, somehow specifying "business requirements" in code is still intriguing to me
    - No I don't mean Cucumber. In the actual application, not just in tests.
    - But no, I have no plans on going back to that approach for this project.
- Using an Entity-Component-System approach... (This was considered for this project - also for non-game-specific things, but is also something used in [Cardshifter](https://github.com/Cardshifter/Cardshifter/))
- Storing all games in a database is good (This is one of the main features of [Minesweeper Flags Extreme](https://play.minesweeperflags.net))
  - Server might need an update every now and then
  - Replays are nice
    - Version-controlled game logic has not yet been tried, but is a potential plan for this project
- To test a game, you shouldn't need to create game clients and send data to test the game
- Moddability is awesome, but also a pain (and nothing I have really considered for this project for the near future)
  - DSLs are cool though
- Invite using URL is a cool feature
- Long-running games needs to be supported
- Using user names as unique ID is a bad ID (Lesson learned from [Minesweeper Flags Extreme](https://play.minesweeperflags.net))
- Using WebSockets is good
- Maintaining a database of usernames and passwords is annoying. OAuth is good. (Lesson learned from [Minesweeper Flags Extreme](https://play.minesweeperflags.net))
- Observing games is a cool feature
  - But people can also get creeped out if they know they are observed (has happened in [Minesweeper Flags Extreme](https://play.minesweeperflags.net))
- Balancing a TCG is *really* difficult (See [Cardshifter](https://github.com/Cardshifter/Cardshifter/))

## Some decisions made

- Player count: Each game should specify how many players can play the game, with a lower limit of 1 and then up to practical infinity. All board games you see in real life will have a specification that says how many players can play the game.
- Setting up a game: To setup a game the game basically needs to know how many players should play, and also potentially some additional configuration for the game (difficulty level, which rules to use, and so on)
- Inviting players: While inviting players they should be able to choose some player-specific stuff, such as "Be on this team" or "Use this deck to play" or "Use this character". This is done after setting up the general game-specific settings.
- Winning/Losing a game: Everything is just "Eliminations". A player can be eliminated and be considered either as a winner or loser (or draw). Multiple players can reach 1st place (or any place). Eliminating a player basically means "You can't play anymore", so it can also be used to determine the result of a one-player game.
- Viewing a game: Because of Vue.js, and to easily support observing games, and for potentially other reasons, the game state is sent as JSON and automatically handled by Vue.js to setup the current game state. So implementing support for a new game in frontend is as easy as binding state to components - which is the very basics of Vue.js.
- Making moves in a game - Backend: As games vary and as the complexity of the type of action you perform varies, there are a few different ways to specify an action. For example, "End Turn" is a very simple action - it does not have any parameters. "Choose a number" has a parameter - The number. "Click on a tile in the grid" has a parameter - the tile that is clicked. Then there's more complex ones, like giving a clue to a player in the game of Hanabi: You need to both choose a player, choose if you want to give a clue for color or number, and then choose the value for the color/number. But breaking the give clue action down into the three smaller steps (player, color/number, value) makes it possible to handle this action and at every step show the player which possible options exist.
- Making moves in a game - Frontend: In order to highlight possible actions, each action (or part of an action) maps to a string which allows for easy lookup to determine if a specific action is allowed. Then clicking that corresponding component should trigger the corresponding action.
- Frontend re-use: Because of how the "viewing a game" and "Making moves in a game - Frontend" is handled, frontend uses the same code for both playing a game locally - without connecting to a server - and online - with connecting to a server.
- Saving game in database: As a game can contain random events (rolling a die, shuffling cards) the game code needs to tell the server "I did a random thing and this was the result", and it also needs to handle the server telling the game code "You should do this action and use this state for the random things" (for loading or replaying games).
- Database design: Amazon DynamoDB is used for games that are in progress, and to store the moves for each game. But PostgresSQL is used for statistics, as it allows more complex queries that would be cumbersome to make in DynamoDB ("Get the summary of which players I have played Tic-Tac-Toe against and sort by the highest win percentage" can be made in PostgresSQL and while it's probably possible to make it in DynamoDB as well, I concluded that it would be cumbersome to design that)

### Documentation and test-cases at the same time

I wrote my own tool to generate documentation for how the client and server communicates. As I am sometimes not motivated to write test-cases and even less motivated to write documentation, I figured that I might as well generate the documentation from the test-cases. I have integrated a check in my build pipeline to make sure that the documentation is not outdated.

## TODO-list / Roadmap / Future features

- Possibly separate "Some decisions made" and "Lessons learned" to their own files in the future.
- As Kotlin can be run dynamically using the JVM scripting engine, it would be possible to give advanced players access to this so that they can customize the starting state, or adjust stuff along the way.
- Undo button (opponents needs to agree, naturally)
- Reinforcement Learning AI: Given a game state, learn what the best move is.
- More games!

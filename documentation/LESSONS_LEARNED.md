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

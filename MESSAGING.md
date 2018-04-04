### Login

To login as a guest, send:

    { "type": "Auth", "provider": "guest" }

You should receive:

    {"type":"Auth","name":"guest-679"}

### Start a game

Currently only simple match-making is available, which means that you say "I want to play a game of XYZ" and then the server will pair you up with someone else who also wants to play XYZ.

Send:

    { "game": "XYZ", "type": "matchMake" }

Replace "XYZ" with the name of the game you really want to play, such as "UR" or "Connect4"

When the game is started the server will send:

    {"type":"GameStarted","gameType":"UR","gameId":"2","yourIndex":0,"players":["guest-679","some-other-player"]}

### Make moves within a game

How to make moves depends a bit on the game you are playing, but some things are common for all games.

Send:

    { "game": "UR", "gameId": "2", "type": "move", "moveType": "roll", "move": -1 }

You may receive information from the server about the result of your move, such as:

    {"type":"GameState","gameType":"UR","gameId":"2","roll":1}

After the move is done you will get confirmation from the server that the move has been made:

    {"type":"GameMove","gameType":"UR","gameId":"2","player":0,"moveType":"roll","move":""}

---

Send:

    { "game": "UR", "gameId": "2", "type": "move", "moveType": "move", "move": 0 }

Receive:

    {"type":"GameMove","gameType":"UR","gameId":"2","player":0,"moveType":"move","move":0}
    {"type":"GameState","gameType":"UR","gameId":"2","player":1}
    {"type":"GameState","gameType":"UR","gameId":"2","roll":1}
    {"type":"GameMove","gameType":"UR","gameId":"2","player":1,"moveType":"roll","move":""}
    {"type":"GameMove","gameType":"UR","gameId":"2","player":1,"moveType":"move","move":0}
    {"type":"GameState","gameType":"UR","gameId":"2","player":0}

---

For The Royal Game of Ur ("UR"):

Available move types are `roll` and `move`. For the moveType `roll` you can send any integer as `move`, it doesn't matter.

For moveType `move` you can send which piece you want to move (0 - 14 inclusive). 0 means "enter with a new piece"

For Connect Four ("Connect4"):

Available move type is `move` and for the value of `move` you can send 0--6 (inclusive), 0 for the leftmost column, 6 for the rightmost.

### Game ended

Whenever a player is no longer able to play, no matter if the player wins or loses, the server will send:

    {"type":"PlayerEliminated","gameType":"UR","gameId":"2","player":0,"winner":true,"position":1}
    {"type":"PlayerEliminated","gameType":"UR","gameId":"2","player":1,"winner":false,"position":2}

When all players are no longer able to play the game is ending and the server informs about this with:

    {"type":"GameEnded","gameType":"UR","gameId":"2"}

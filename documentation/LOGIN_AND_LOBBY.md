### Authentication

"Use `"route": "auth/guest"` and don't send a `name`. You will be randomly given a guest name.

When you have not yet been given a name you can send

    { "route": "auth/guest" }

From now on you will be known as `guest-12345`

guest-12345 will receive:

    {"type":"Auth","name":"guest-12345"}

### Entering a lobby

guest-12345 sends:

    { "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }

### Listing available players

guest-12345 sends:

    { "route": "lobby/list" }

guest-12345 will receive:

    {"type":"Lobby","users":{"TestGameType":["Client B"],"OtherGameType":["Client B"]}}

### When someone disconnects

Whenever you are in a lobby and another client in the same lobby disconnects, you will be notified instantly.

TODO: This is not implemented yet.

### When a new client joins

Whenever you are in a lobby and another client joins the same lobby, you will be notified instantly.

TODO: This is not implemented yet.


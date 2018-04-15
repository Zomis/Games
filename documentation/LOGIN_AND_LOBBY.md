### Authentication

"Use `"provider": "guest"` and don't send a `name`. You will be randomly given a guest name.

When you have not yet been given a name you can send

    { "type": "Auth", "name": "Client A", "provider": "test" }

From now on you will be known as `Client A`

Client A will receive:

    {"type":"Auth","name":"Client A"}

### Entering a lobby

Client A sends:

    { "type": "ClientGames", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }

### Listing available players

Client A sends:

    { "type": "ListRequest" }

Client A will receive:

    {"type":"Lobby","users":{"TestGameType":["Client B"],"OtherGameType":["Client B"]}}

### When someone disconnects

Whenever you are in a lobby and another client in the same lobby disconnects, you will be notified instantly.

TODO: This is not implemented yet.

### When a new client joins

Whenever you are in a lobby and another client joins the same lobby, you will be notified instantly.

TODO: This is not implemented yet.


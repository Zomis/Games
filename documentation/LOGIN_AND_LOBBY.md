### Authentication

"Use `"route": "auth/guest"` and don't send a `name`. You will be randomly given a guest name.

When you have not yet been given a name you can send

    { "route": "auth/guest" }

You will receive your name and your playerId

guest-12345 will receive:

    {"type":"Auth","playerId":"00000000-0000-0000-0000-000000000000","name":"guest-12345"}

### Entering a lobby

guest-12345 sends:

    { "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }

### Listing available players

guest-12345 sends:

    { "route": "lobby/list" }

guest-12345 will receive:

    {"type":"Lobby","users":{"TestGameType":[{"id":"11111111-1111-1111-1111-111111111111","name":"Client B"}],"OtherGameType":[{"id":"11111111-1111-1111-1111-111111111111","name":"Client B"}]}}

### When someone disconnects

Whenever you are in a lobby and another client in the same lobby disconnects, you will be notified instantly.

guest-12345 will receive:

    {"type":"LobbyChange","player":{"id":"11111111-1111-1111-1111-111111111111","name":"Client B"},"action":"left"}

### When a new client joins

Whenever you are in a lobby and another client joins the same lobby, you will be notified instantly.

guest-12345 will receive:

    {"type":"LobbyChange","player":{"id":"22222222-2222-2222-2222-222222222222","name":"Client C"},"action":"joined","gameTypes":["TestGameType","OtherGameType"]}


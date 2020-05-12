### Authentication

"Use `"route": "auth/guest"` and don't send a `name`. You will be randomly given a guest name.

When you have not yet been given a name you can send

    { "route": "auth/guest" }

You will receive your name, playerId and picture URL

guest-12345 will receive:

    {"type":"Auth","playerId":"00000000-0000-0000-0000-000000000000","name":"guest-12345","picture":"https://www.gravatar.com/avatar/9f89c84a559f573636a47ff8daed0d33?s=128&d=identicon"}

### Entering a lobby

guest-12345 sends:

    { "route": "lobby/join", "gameTypes": ["TestGameType", "OtherGameType"], "maxGames": 1 }

### Listing available players

guest-12345 sends:

    { "route": "lobby/list" }

guest-12345 will receive:

    {"type":"Lobby","users":{"TestGameType":[{"id":"11111111-1111-1111-1111-111111111111","name":"Client B","picture":"https://www.gravatar.com/avatar/38c6cbd28bf165070d070980dd1fb595?s=128&d=identicon"}],"OtherGameType":[{"id":"11111111-1111-1111-1111-111111111111","name":"Client B","picture":"https://www.gravatar.com/avatar/38c6cbd28bf165070d070980dd1fb595?s=128&d=identicon"}]}}

### When someone disconnects

Whenever you are in a lobby and another client in the same lobby disconnects, you will be notified instantly.

guest-12345 will receive:

    {"type":"LobbyChange","player":{"id":"11111111-1111-1111-1111-111111111111","name":"Client B","picture":"https://www.gravatar.com/avatar/38c6cbd28bf165070d070980dd1fb595?s=128&d=identicon"},"action":"left"}

### When a new client joins

Whenever you are in a lobby and another client joins the same lobby, you will be notified instantly.

guest-12345 will receive:

    {"type":"LobbyChange","player":{"id":"22222222-2222-2222-2222-222222222222","name":"Client C","picture":"https://www.gravatar.com/avatar/1c27ba90c11014f014be250818fd3443?s=128&d=identicon"},"action":"joined","gameTypes":["TestGameType","OtherGameType"]}


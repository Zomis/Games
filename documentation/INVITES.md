### Inviting someone to play a game

Inviting players is done by inviting their playerId, which will be unique

TestClientA sends:

    { "route": "invites/invite", "gameType": "TestGameType", "invite": ["11111111-1111-1111-1111-111111111111"] }

TestClientA will receive:

    {"type":"InviteWaiting","inviteId":"12345678-1234-1234-1234-123456789abc","playersMin":2,"playersMax":2}

TestClientB will receive:

    {"type":"Invite","host":"TestClientA","game":"TestGameType","inviteId":"12345678-1234-1234-1234-123456789abc"}

TestClientA will receive:

    {"type":"InviteStatus","playerId":"11111111-1111-1111-1111-111111111111","status":"pending","inviteId":"12345678-1234-1234-1234-123456789abc"}

### Accepting an invite

TestClientB sends:

    { "route": "invites/12345678-1234-1234-1234-123456789abc/respond", "accepted": true }

TestClientA will receive:

    {"type":"InviteView","...":"..."}

TestClientA will receive:

    {"type":"InviteResponse","inviteId":"12345678-1234-1234-1234-123456789abc","playerId":"11111111-1111-1111-1111-111111111111","accepted":true}

When a user accepts an invite the game is started automatically and both players will receive a `GameStarted` message.

TestClientB will receive:

    {"type":"InviteView","...":"..."}

TestClientB will receive:

    {"type":"InviteView","...":"..."}

TestClientB will receive:

    {"type":"GameStarted","gameType":"TestGameType","gameId":"1","yourIndex":1,"players":[{"id":"00000000-0000-0000-0000-000000000000","name":"TestClientA","picture":"https://www.gravatar.com/avatar/9f89c84a559f573636a47ff8daed0d33?s=128&d=identicon"},{"id":"11111111-1111-1111-1111-111111111111","name":"TestClientB","picture":"https://www.gravatar.com/avatar/38c6cbd28bf165070d070980dd1fb595?s=128&d=identicon"}]}

### Declining an invite

TestClientB sends:

    { "route": "invites/12345678-1234-1234-1234-123456789abc/respond", "accepted": false }

TestClientA will receive:

    {"type":"InviteResponse","inviteId":"12345678-1234-1234-1234-123456789abc","playerId":"11111111-1111-1111-1111-111111111111","accepted":false}


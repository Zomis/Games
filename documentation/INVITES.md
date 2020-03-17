### Inviting someone to play a game

Inviting players is done by inviting their playerId, which will be unique

TestClientA sends:

    { "route": "invites/invite", "gameType": "TestGameType", "invite": ["11111111-1111-1111-1111-111111111111"] }

TestClientA will receive:

    {"type":"InviteWaiting","inviteId":"TestGameType-TestClientA-0","playersMin":2,"playersMax":2}

TestClientB will receive:

    {"type":"Invite","host":"TestClientA","game":"TestGameType","inviteId":"TestGameType-TestClientA-0"}

TestClientA will receive:

    {"type":"InviteStatus","playerId":"11111111-1111-1111-1111-111111111111","status":"pending","inviteId":"TestGameType-TestClientA-0"}

### Accepting an invite

TestClientB sends:

    { "route": "invites/TestGameType-TestClientA-0/respond", "accepted": true }

TestClientA will receive:

    {"type":"InviteResponse","inviteId":"TestGameType-TestClientA-0","playerId":"11111111-1111-1111-1111-111111111111","accepted":true}

When a user accepts an invite the game is started automatically and both players will receive a `GameStarted` message.

TestClientB will receive:

    {"type":"GameStarted","gameType":"TestGameType","gameId":"1","yourIndex":1,"players":[{"id":"00000000-0000-0000-0000-000000000000","name":"TestClientA"},{"id":"11111111-1111-1111-1111-111111111111","name":"TestClientB"}]}

### Declining an invite

TestClientB sends:

    { "route": "invites/TestGameType-TestClientA-0/respond", "accepted": false }

TestClientA will receive:

    {"type":"InviteResponse","inviteId":"TestGameType-TestClientA-0","playerId":"11111111-1111-1111-1111-111111111111","accepted":false}


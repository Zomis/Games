### Inviting someone to play a game

TestClientA sends:

    { "route": "invites/invite", "gameType": "TestGameType", "invite": ["TestClientB"] }

TestClientA will receive:

    {"type":"InviteWaiting","inviteId":"TestGameType-TestClientA-0","waitingFor":["TestClientB"]}

TestClientB will receive:

    {"type":"Invite","host":"TestClientA","game":"TestGameType","inviteId":"TestGameType-TestClientA-0"}

### Accepting an invite

TestClientB sends:

    { "route": "invites/TestGameType-TestClientA-0/respond", "accepted": true }

TestClientA will receive:

    {"type":"InviteResponse","user":"TestClientB","accepted":true,"inviteId":"TestGameType-TestClientA-0"}

When a user accepts an invite the game is started automatically and both players will receive a `GameStarted` message.

TestClientB will receive:

    {"type":"GameStarted","gameType":"TestGameType","gameId":"1","yourIndex":1,"players":["TestClientA","TestClientB"]}

### Declining an invite

TestClientB sends:

    { "route": "invites/TestGameType-TestClientA-0/respond", "accepted": false }

TestClientA will receive:

    {"type":"InviteResponse","user":"TestClientB","accepted":false,"inviteId":"TestGameType-TestClientA-0"}


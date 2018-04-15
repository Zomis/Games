### Inviting someone to play a game

TestClientA sends:

    { "type": "Invite", "gameType": "TestGameType", "invite": ["TestClientB"] }

TestClientA will receive:

    {"type":"InviteWaiting","inviteId":"TestGameType-TestClientA-0","waitingFor":["TestClientB"]}

TestClientB will receive:

    {"type":"Invite","host":"TestClientA","game":"TestGameType","inviteId":"TestGameType-TestClientA-0"}


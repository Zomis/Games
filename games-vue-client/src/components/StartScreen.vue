<template>
  <div class="start-screen">
    <h1 class="login-name">Welcome, {{ loginName }}</h1>

    <div class="current-games">
      <div class="active-game" v-for="game in myComponents">
        <component :key="game.gameId" :is="game.component" v-bind="game.props"></component>
      </div>
    </div>
    <v-btn @click="newRandomGame()">New game</v-btn>

    <v-card v-for="(users, gameType) in availableUsers" :key="gameType" class="games">
      <v-toolbar color="cyan" dark>
        <v-toolbar-title>{{ gameType }}</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn round :disabled="waiting" @click="matchMake(gameType)">Play anyone</v-btn>
        <v-btn round :disabled="waiting" @click="inviteLink(gameType)">Invite with link</v-btn>
      </v-toolbar>
      <v-list two-line>
        <template v-for="name in users">
          <v-btn class="username" :class="'user-' + gameType" @click="invite(gameType, name)">{{ name }}</v-btn>
        </template>
      </v-list>
    </v-card>

    <Invites />

    <button @click="requestGameList()">Request game list</button>
    <v-list class="gamelist">
      <template v-for="(game, index) in gameList">
        <v-list-tile :key="game.gameId">
          <v-list-tile-content>
            <v-list-tile-title v-html="game.gameType + ' Game ' + game.gameId"></v-list-tile-title>
            <v-list-tile-sub-title v-html="vsify(game.players)"></v-list-tile-sub-title>
          </v-list-tile-content>
          <v-list-tile-action>
            <v-btn color="info" @click="observe(game)">Observe</v-btn>
          </v-list-tile-action>
        </v-list-tile>
      </template>
    </v-list>

    <p>If you want to play Royal game of UR against an AI, click "UR" and then click "Create Bot"</p>
    <p>You can observe existing games by clicking "Request game list" and then click on the game you want to observe.</p>
  </div>
</template>

<script>
import Socket from "../socket";
import Invites from "./Invites";

import RoyalGameOfUR from "@/components/RoyalGameOfUR";
import Connect4 from "@/components/games/Connect4";
import UTTT from "@/components/games/UTTT";

let gameTypes = {
  UR: "RoyalGameOfUR",
  UTTT: "UTTT",
  "UTTT-ECS": "ECSGame",
  Connect4: "Connect4"
};

export default {
  name: "StartScreen",
  data() {
    return {
      myComponents: [],
      availableUsers: {},
      gameList: [],
      waiting: false,
      waitingGame: null
    };
  },
  components: {
    RoyalGameOfUR,
    Connect4,
    UTTT,
    Invites
  },
  methods: {
    vsify(players) {
      let result = "";
      for (var i = 0; i < players.length; i++) {
        result += players[i];
        if (i < players.length - 1) {
          result += " vs. ";
        }
      }
      return result;
    },
    observe: function(game) {
      let matchingGame = this.myComponents.find(
        e => e.props.game == game.gameType && e.props.gameId == game.gameId
      );
      if (matchingGame) {
        return;
      }
      this.myComponents.push({
        component: gameTypes[game.gameType],
        props: {
          game: game.gameType,
          gameId: game.gameId,
          players: game.players,
          yourIndex: -42
        }
      });
    },
    requestGameList: function() {
      Socket.send(`v1:{ "type": "GameList" }`);
    },
    gameListMessage: function(message) {
      this.gameList = message.list;
    },
    matchMake: function(game) {
      this.waiting = true;
      this.waitingGame = game;
      Socket.send(`v1:{ "game": "${game}", "type": "matchMake" }`);
    },
    invite: function(gameType, username) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": ["${username}"] }`
      );
    },
    inviteLink(gameType, username) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": [] }`
      );
    },
    lobbyChangeMessage: function(e) {
      // client, action, gameTypes
      let user = e.client;
      if (e.action === "joined") {
        let gameTypes = e.gameTypes;
        gameTypes.forEach(gt => {
          let list = this.availableUsers[gt];
          if (list == null) throw "No list for " + gt;
        });
        gameTypes
          .map(gt => this.availableUsers[gt])
          .forEach(list => list.push(user));
      } else if (e.action === "left") {
        let gameTypes = Object.keys(this.availableUsers);
        gameTypes.map(gt => this.availableUsers[gt]).forEach(list => {
          let index = list.indexOf(user);
          if (index >= 0) {
            list.splice(index, 1);
          }
        });
      } else {
        throw "Unknown action: " + e.action;
      }
    },
    lobbyMessage: function(e) {
      this.availableUsers = e.users;
    }
  },
  created() {
    //    {"type":"Lobby","users":{"UR":["guest-44522"],"Connect4":["guest-44522"]}}
    Socket.$on("type:Lobby", this.lobbyMessage);
    Socket.$on("type:LobbyChange", this.lobbyChangeMessage);
    Socket.$on("type:GameList", this.gameListMessage);
    Socket.send(
      `{ "type": "ClientGames", "gameTypes": ["UR", "Connect4", "UTTT", "UTTT-ECS"], "maxGames": 1 }`
    );
    Socket.send(`{ "type": "ListRequest" }`);
  },
  computed: {
    loginName() {
      return Socket.loginName;
    }
  },
  beforeDestroy() {
    Socket.$off("type:Lobby", this.lobbyMessage);
    Socket.$off("type:LobbyChange", this.lobbyChangeMessage);
    Socket.$off("type:GameList", this.gameListMessage);
  }
};
</script>

<style scoped>
h1,
h2 {
  font-weight: normal;
}

.gametypes {
  margin-top: 24px;
  margin-bottom: 24px;
}

.games,
.gamelist {
  margin: 32px;
  width: 25%;
  margin: 32px auto 32px auto;
}
</style>

<template>
  <div class="start-screen">
    <h1 class="login-name">Welcome, {{ loginName }}</h1>

    <v-btn @click="newRandomGame()">New game</v-btn>

    <v-card v-for="(users, gameType) in lobby" :key="gameType" class="games">
      <v-toolbar color="cyan" dark>
        <v-toolbar-title>{{ gameType }}</v-toolbar-title>
        <v-spacer></v-spacer>
<!--        <v-btn round :disabled="waiting" @click="matchMake(gameType)">Play anyone</v-btn> -->
        <v-btn round :disabled="waiting" @click="inviteLink(gameType)">Invite with link</v-btn>
      </v-toolbar>
      <v-card-title>
        Users
      </v-card-title>
      <v-list light>
        <template v-for="(name, index) in users">
          <v-divider v-if="index > 0"></v-divider>
          <v-list-tile :key="index">
            <v-list-tile-content>
              <v-list-tile-title v-html="name"></v-list-tile-title>
            </v-list-tile-content>

            <v-list-tile-action>
              <v-btn color="info" @click="invite(gameType, name)" v-if="name !== loginName">Invite</v-btn>
            </v-list-tile-action>
          </v-list-tile>
        </template>
      </v-list>

      <v-divider></v-divider>

      <v-card-title>Your Games</v-card-title>
      <template v-for="game in activeGames" v-if="game.props.gameType === gameType && game.props.yourIndex >= 0">
        <div class="active-game">
          <component :key="gameType + game.props.gameId" :is="game.component" v-bind="game.props"></component>
        </div>
      </template>

      <v-divider></v-divider>

      <v-card-title>Other Games</v-card-title>
      <template v-for="game in activeGames" v-if="game.props.gameType === gameType && game.props.yourIndex < 0">
        <div class="active-game">
          <component :key="gameType + game.props.gameId" :is="game.component" v-bind="game.props"></component>
        </div>
      </template>
    </v-card>

    <Invites />

    <button @click="requestGameList()">Request game list</button>
    <v-list class="gamelist">
      <template v-for="(game, index) in gameList">
        <v-list-tile :key="game.gameType + game.gameId">
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
import { mapState } from "vuex";

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
      let matchingGame = this.activeGames.find(
        e => e.props.game == game.gameType && e.props.gameId == game.gameId
      );
      if (matchingGame) {
        return;
      }
      this.$store.dispatch("observe", {
        gameType: game.gameType,
        gameId: game.gameId,
        players: game.players,
        yourIndex: -42
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
    }
  },
  created() {
    if (!Socket.isConnected()) {
      this.$router.push("/login");
      return;
    }
    Socket.$on("type:LobbyChange", this.lobbyChangeMessage);
    Socket.$on("type:GameList", this.gameListMessage);
    Socket.send(
      `{ "type": "ClientGames", "gameTypes": ["UR", "Connect4", "UTTT", "UTTT-ECS"], "maxGames": 1 }`
    );
    Socket.send(`{ "type": "ListRequest" }`);
  },
  computed: {
    activeGames() {
      return this.$store.getters.activeGames;
    },
    ...mapState(["loginName", "lobby"])
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
  width: 42%;
  margin: 32px auto 32px auto;
}
</style>

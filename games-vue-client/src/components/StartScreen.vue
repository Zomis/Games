<template>
  <v-container fluid>
  <v-row>
    <Invites />
    <v-col cols="12">
      <h1 class="login-name">Welcome, {{ loginName }}</h1>
    </v-col>

    <v-col cols="12" md="6" lg="4" v-for="(users, gameType) in lobby" :key="gameType">
    <v-card class="games">
      <v-toolbar color="cyan" dark>
        <v-toolbar-title>{{ displayNames[gameType] }}</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn rounded @click="createInvite(gameType)">New Game</v-btn>
      </v-toolbar>
      <v-card-title>
        Users
      </v-card-title>
      <v-list light>
        <template v-for="(player, index) in users">
          <v-divider :key="`divider-${player.id}`" v-if="index > 0"></v-divider>
          <v-list-item :key="`player-${player.id}`">
            <v-list-item-content>
              <v-list-item-title v-html="player.name"></v-list-item-title>
            </v-list-item-content>

            <v-list-item-action>
              <v-btn color="info" @click="invite(gameType, player.id)" v-if="player.name !== loginName">Invite</v-btn>
            </v-list-item-action>
          </v-list-item>
        </template>
      </v-list>

      <v-divider></v-divider>

      <v-card-title>Your Games</v-card-title>
      <template v-for="game in activeGames" v-if="game.gameInfo.gameType === gameType && game.gameInfo.yourIndex >= 0">
        <div class="active-game" :key="game.gameId">
          <component :key="gameType + game.gameInfo.gameId" :is="game.component" :gameInfo="game.gameInfo"></component>
        </div>
      </template>

      <v-divider></v-divider>

      <v-card-title>Other Games</v-card-title>
      <template v-for="game in activeGames" v-if="game.gameInfo.gameType === gameType && game.gameInfo.yourIndex < 0">
        <div class="active-game" :key="game.gameId">
          <component :key="gameType + game.gameInfo.gameId" :is="game.component" :gameInfo="game.gameInfo"></component>
        </div>
      </template>
    </v-card>
    </v-col>

    <v-col cols="12">
    <v-btn @click="requestGameList()">Request game list</v-btn>
    <v-list class="gamelist">
      <template v-for="game in gameList">
        <v-list-item :key="game.gameType + game.gameId">
          <v-list-item-content>
            <v-list-item-title v-html="game.gameType + ' Game ' + game.gameId"></v-list-item-title>
            <v-list-item-sub-title v-html="vsify(game.players)"></v-list-item-sub-title>
          </v-list-item-content>
          <v-list-item-action>
            <v-btn color="info" @click="observe(game)">Observe</v-btn>
          </v-list-item-action>
        </v-list-item>
      </template>
    </v-list>
    <v-list class="unfinished">
      <template v-for="(gameList, gameType) in unfinishedGames">
      <template v-for="game in gameList">
        <v-list-item :key="game.gameId">
          <v-list-item-content>
            <v-list-item-title v-html="gameType + ' Game ' + game.GameId"></v-list-item-title>
<!--            <v-list-item-sub-title v-html="vsify(game.players)"></v-list-item-sub-title> -->
          </v-list-item-content>
          <v-list-item-action>
            <v-btn color="info" @click="resumeGame(gameType, game.GameId)">Resume</v-btn>
          </v-list-item-action>
        </v-list-item>
      </template>
      </template>
    </v-list>
    </v-col>
  </v-row>
  </v-container>
</template>

<script>
import Socket from "../socket";
import Invites from "./Invites";

import { mapState } from "vuex";
import supportedGames from "@/supportedGames";

export default {
  name: "StartScreen",
  data() {
    return {
      gameList: [],
      unfinishedGames: []
    };
  },
  components: {
    ...supportedGames.components(),
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
        e =>
          e.gameInfo.game == game.gameType && e.gameInfo.gameId == game.gameId
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
      Socket.send(`{ "type": "GameList" }`);
    },
    gameListMessage: function(message) {
      this.gameList = message.list;
    },
    createInvite(gameType) {
      this.$store.commit("lobby/createInvite", gameType);
    },
    unfinishedGameListMessage(message) {
      this.unfinishedGames = message.games
    },
    resumeGame(gameType, gameId) {
      Socket.send(`{ "type": "LoadGame", "gameType": "${gameType}", "gameId": "${gameId}" }`);
    },
    invite(gameType, playerId) {
      this.createInvite(gameType)
      Socket.route("invites/invite", { gameType: gameType, invite: [playerId] });
    }
  },
  created() {
    if (!Socket.isConnected()) {
      this.$router.push("/login");
      return;
    }
    Socket.$on("type:GameList", this.gameListMessage);
    Socket.$on("type:LobbyUnfinished", this.unfinishedGameListMessage);
    Socket.route(`lobby/join`, { gameTypes: supportedGames.enabledGameKeys(), maxGames: 1 })
    Socket.route(`lobby/list`, {});
  },
  computed: {
    displayNames() {
      let games = {};
      supportedGames.enabledGameKeys().forEach(gameType => {
        games[gameType] = supportedGames.games[gameType].displayName ? supportedGames.games[gameType].displayName : gameType;
      });
      return games
    },
    activeGames() {
      return this.$store.getters.activeGames;
    },
    ...mapState('lobby', {
      loginName: state => state.yourPlayer.name,
      lobby: state => state.lobby
    })
  },
  beforeDestroy() {
    Socket.$off("type:GameList", this.gameListMessage);
    Socket.$off("type:LobbyUnfinished", this.unfinishedGameListMessage);
  }
};
</script>

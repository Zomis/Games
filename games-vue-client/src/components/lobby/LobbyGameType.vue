<template>
  <v-card class="game-type">
    <v-toolbar color="cyan" dark>
      <v-toolbar-title>{{ displayName }}</v-toolbar-title>
      <v-icon :color="orange">{{ mdi-account-multiple }}</v-icon>
      {{supportedPlayers}}
      <v-icon :color="orange">{{ mdi-clock-time-four-outline }}</v-icon>
      {{playTime}}
      <v-spacer></v-spacer>
      <v-btn rounded @click="createInvite(gameType)">New Game</v-btn>
    </v-toolbar>
    <v-card-title>Users</v-card-title>
    <v-list light>
      <template v-for="(player, index) in users">
        <v-divider :key="`divider-${player.id}`" v-if="index > 0"></v-divider>
        <v-list-item :key="`player-${player.id}`">
          <v-list-item-content>
            <v-list-item-title v-html="player.name"></v-list-item-title>
          </v-list-item-content>

          <v-list-item-action>
            <v-btn
              color="info"
              @click="invite(gameType, player.id)"
              v-if="player.name !== yourPlayer.name"
            >Invite</v-btn>
          </v-list-item-action>
        </v-list-item>
      </template>
    </v-list>

    <v-divider></v-divider>

    <v-card-title>Your Games</v-card-title>
    <div v-for="game in yourGames" :key="game.gameId" class="active-game">
      <component
        :key="gameType + game.gameInfo.gameId"
        :is="game.component"
        :gameInfo="game.gameInfo"
      ></component>
    </div>

    <v-divider></v-divider>

    <v-card-title>Other Games</v-card-title>
    <div v-for="game in otherGames" :key="game.gameId" class="active-game">
      <component
        :key="gameType + game.gameInfo.gameId"
        :is="game.component"
        :gameInfo="game.gameInfo"
      ></component>
    </div>
  </v-card>
</template>
<script>
import Socket from "@/socket";
import supportedGames from "@/supportedGames";

export default {
  name: "LobbyGameType",
  props: ["gameType", "users", "yourPlayer"],
  methods: {
    createInvite(gameType) {
      Socket.route("invites/prepare", { gameType: gameType });
    },
    invite(gameType, playerId) {
      Socket.route("invites/invite", {
        gameType: gameType,
        invite: [playerId]
      });
    }
  },
  computed: {
    activeGames() {
      return this.$store.getters.activeGames.filter(
        game => game.gameInfo.gameType === this.gameType
      );
    },
    supportedGame() {
      return supportedGames.games[this.gameType];
    },
    displayName() {
      return this.supportedGame.displayName
        ? this.supportedGame.displayName
        : this.gameType;
    },
    yourGames() {
      return this.activeGames.filter(game => game.gameInfo.yourIndex >= 0);
    },
    otherGames() {
      return this.activeGames.filter(game => game.gameInfo.yourIndex < 0);
    }
  }
};
</script>
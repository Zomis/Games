<template>
  <v-card class="game-type" :class="gameTypeCssName">
    <v-toolbar
      color="cyan"
      dark
    >
      <v-toolbar-title>{{ displayName }}</v-toolbar-title>
      <v-spacer />
      <v-btn
        rounded
        @click="testGame(gameType)"
      >
        Try it
      </v-btn>
      <v-btn
        rounded
        @click="createInvite(gameType)"
      >
        New Game
      </v-btn>
    </v-toolbar>
    <v-card-title>
      Users
    </v-card-title>
    <v-list light>
      <template v-for="(player, index) in users">
        <v-divider
          v-if="index > 0"
          :key="`divider-${player.id}`"
        />
        <v-list-item :key="`player-${player.id}`">
          <v-list-item-content>
            <v-list-item-title v-html="player.name" />
          </v-list-item-content>

          <v-list-item-action>
            <v-btn
              v-if="player.name !== yourPlayer.name"
              color="info"
              @click="invite(gameType, player.id)"
            >
              Invite
            </v-btn>
          </v-list-item-action>
        </v-list-item>
      </template>
    </v-list>

    <v-divider />

    <v-card-title>Your Games</v-card-title>
    <div
      v-for="game in yourGames"
      :key="game.gameId"
      class="active-game"
    >
      <component
        :is="game.component"
        :key="gameType + game.gameInfo.gameId"
        :game-info="game.gameInfo"
      />
    </div>

    <v-divider />

    <v-card-title>Other Games</v-card-title>
    <div
      v-for="game in otherGames"
      :key="game.gameId"
      class="active-game"
    >
      <component
        :is="game.component"
        :key="gameType + game.gameInfo.gameId"
        :game-info="game.gameInfo"
      />
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
        testGame(gameType) {
          Socket.route("testGames/game", { gameType: gameType })
        },
        createInvite(gameType) {
            Socket.route("invites/prepare", { gameType: gameType })
        },
        invite(gameType, playerId) {
            Socket.route("invites/invite", { gameType: gameType, invite: [playerId] });
        }
    },
    computed: {
        gameTypeCssName() {
            return 'game-' + this.gameType.toLowerCase().replace(" ", "-");
        },
        activeGames() {
            return this.$store.getters.activeGames.filter(game => game.gameInfo.gameType === this.gameType);
        },
        supportedGame() {
            return supportedGames.games[this.gameType];
        },
        displayName() {
            return this.supportedGame.displayName ? this.supportedGame.displayName : this.gameType;
        },
        yourGames() {
            return this.activeGames.filter(game => game.gameInfo.yourIndex >= 0)
        },
        otherGames() {
            return this.activeGames.filter(game => game.gameInfo.yourIndex < 0)
        }
    }
}
</script>
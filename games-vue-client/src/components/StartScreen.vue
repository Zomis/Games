<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(users, gameType) in lobby"
        :key="gameType"
        cols="12"
        md="6"
        lg="4"
      >
        <LobbyGameType
          :game-type="gameType"
          :users="users"
          :your-player="yourPlayer"
        />
      </v-col>

      <v-col cols="12">
        <v-btn @click="requestGameList()">
          Request game list
        </v-btn>
        <v-list class="gamelist">
          <template v-for="game in gameList">
            <v-list-item :key="game.gameType + game.gameId">
              <v-list-item-content>
                <v-list-item-title v-html="game.gameType + ' Game ' + game.gameId" />
                <v-list-item-sub-title v-html="vsify(game.players)" />
              </v-list-item-content>
              <v-list-item-action>
                <v-btn
                  color="info"
                  @click="observe(game)"
                >
                  Observe
                </v-btn>
              </v-list-item-action>
            </v-list-item>
          </template>
        </v-list>
        <v-list class="unfinished">
          <template v-for="(gameList, gameType) in unfinishedGames">
            <template v-for="game in gameList">
              <v-list-item :key="game.gameId">
                <v-list-item-content>
                  <v-list-item-title v-html="gameType + ' Game ' + game.GameId" />
                  <!--            <v-list-item-sub-title v-html="vsify(game.players)"></v-list-item-sub-title> -->
                </v-list-item-content>
                <v-list-item-action>
                  <v-btn
                    color="info"
                    @click="resumeGame(gameType, game.GameId)"
                  >
                    Resume
                  </v-btn>
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
import Socket from "@/socket";
import LobbyGameType from "@/components/lobby/LobbyGameType";

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
    LobbyGameType,
    ...supportedGames.components()
  },
  mounted() {
    this.$store.dispatch("setTitle", '')
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
    observe(game) {
      this.$router.push(`/games/${game.gameType}/${game.gameId}`)
    },
    requestGameList() {
      Socket.send(`{ "type": "GameList" }`);
    },
    gameListMessage(message) {
      this.gameList = message.list;
    },
    unfinishedGameListMessage(message) {
      this.unfinishedGames = message.games
    },
    resumeGame(gameType, gameId) {
      this.$router.push(`/games/${gameType}/${gameId}`)
    }
  },
  created() {
    if (!Socket.isConnected()) {
      this.$router.push("/login");
      return;
    }
    Socket.$on("type:GameList", this.gameListMessage);
    Socket.$on("type:LobbyUnfinished", this.unfinishedGameListMessage);
    this.$store.dispatch("lobby/joinAndList");
  },
  computed: {
    ...mapState('lobby', {
      yourPlayer: state => state.yourPlayer,
      lobby: state => state.lobby
    })
  },
  beforeDestroy() {
    Socket.$off("type:GameList", this.gameListMessage);
    Socket.$off("type:LobbyUnfinished", this.unfinishedGameListMessage);
  }
};
</script>

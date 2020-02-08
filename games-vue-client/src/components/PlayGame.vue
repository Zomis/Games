<template>
  <div :class="['game', 'player-' + gameInfo.yourIndex]">
    <GameHead :gameInfo="gameInfo"></GameHead>
    <component :is="viewComponent" :view="view" :onAction="action" :actions="actions" />
    <GameResult :gameInfo="gameInfo"></GameResult>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import Socket from "@/socket";
import { mapState } from "vuex";
import GameHead from "@/components/games/common/GameHead";
import GameResult from "@/components/games/common/GameResult";

export default {
  name: "PlayGame",
  props: ["gameInfo", "showRules"],
  components: {
    GameHead,
    GameResult
  },
  data() {
    return {
      supportedGames: supportedGames,
      supportedGame: supportedGames.games[this.gameInfo.gameType],
      views: []
    }
  },
  mounted() {
    this.$store.dispatch("DslGameState/requestView", this.gameInfo);
    this.$store.dispatch("DslGameState/requestActions", this.gameInfo);
  },
  methods: {
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `{ "gameType": "${this.gameInfo.gameType}", "gameId": "${
          this.gameInfo.gameId
        }", "type": "move", "moveType": "${name}", "move": ${JSON.stringify(
          data
        )} }`;
        Socket.send(json);
      }
    }
  },
  computed: {
    viewComponent() {
      return this.supportedGame.component
    },
    ...mapState("DslGameState", {
      view(state) {
        return state.games[this.gameInfo.gameId].gameData.view;
      },
      actions(state) {
        return state.games[this.gameInfo.gameId].gameData.actions;
      }
    })
  }
}
</script>
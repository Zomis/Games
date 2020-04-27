<template>
  <div :class="['game', 'player-' + gameInfo.yourIndex]">
    <GameHead :gameInfo="gameInfo" :playerCount="gameInfo.players.length" :view="view" :eliminations="eliminations" />
    <component :is="viewComponent" :view="view" :onAction="action" :actions="actions" :actionChoice="actionChoice" :players="gameInfo.players" />
    <v-btn @click="resetActions()" :disabled="actionChoice === null">Reset Action</v-btn>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
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
    this.resetActions();
  },
  methods: {
    resetActions() {
      this.$store.dispatch("DslGameState/requestView", this.gameInfo);
      this.$store.dispatch("DslGameState/resetActions", { gameInfo: this.gameInfo });
    },
    action(name, data) {
      console.log("ACTION CHOICE", name, data)
      let action = this.actions[name][data]
      console.log("ACTION CHOICE", name, data, action)
      if (action === undefined) {
        console.log("NO ACTION FOR", data)
        this.resetActions();
        return
      }
      if (action.direct) {
        // Perform direct
        this.$store.dispatch("DslGameState/action", { gameInfo: this.gameInfo, name: name, data: action.value });
        return
      }

      this.$store.dispatch("DslGameState/nextAction", { gameInfo: this.gameInfo, name: name, action: action.value });
      return;
    }
  },
  computed: {
    viewComponent() {
      return this.supportedGame.component
    },
    ...mapState("DslGameState", {
      actionChoice(state) {
        return state.games[this.gameInfo.gameId].gameData.actionChoice;
      },
      eliminations(state) {
        return state.games[this.gameInfo.gameId].gameData.eliminations;
      },
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
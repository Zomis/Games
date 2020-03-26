<template>
  <div :class="['game', 'player-' + gameInfo.yourIndex]">
    <GameHead :gameInfo="gameInfo" :playerCount="gameInfo.players.length" :view="view" :eliminations="eliminations" />
    <component :is="viewComponent" :view="view" :onAction="action" :actions="actions" />
    <v-btn @click="resetActions()" :disabled="actionPrevious.length == 0">Reset Action</v-btn>
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
      actionChoosing: null,
      actionPrevious: [],
      supportedGames: supportedGames,
      supportedGame: supportedGames.games[this.gameInfo.gameType],
      views: []
    }
  },
  mounted() {
    this.$store.dispatch("DslGameState/requestView", this.gameInfo);
    this.$store.dispatch("DslGameState/requestActions", { gameInfo: this.gameInfo, chosen: [] });
  },
  methods: {
    resetActions() {
      this.actionChoosing = null;
      this.actionPrevious = [];
      this.$store.dispatch("DslGameState/requestView", this.gameInfo);
      this.$store.dispatch("DslGameState/requestActions", { gameInfo: this.gameInfo, actionType: name, chosen: this.actionPrevious });
    },
    action(name, data) {
      console.log("ACTION CHOICE", name, data)
      let action = this.actions[name][data]
      if (action === undefined) {
        console.log("NO ACTION FOR", data)
        this.resetActions();
        return
      }
      if (action.next !== undefined) {
        this.actionChoosing = name
        this.actionPrevious.push(action.next)
        this.$store.dispatch("DslGameState/requestActions", { gameInfo: this.gameInfo, actionType: name, chosen: this.actionPrevious });
        return
      }
      if (action.parameter !== undefined) {
        this.$store.dispatch("DslGameState/action", { gameInfo: this.gameInfo, name: name, data: action.parameter });
        this.actionChoosing = null
        this.actionPrevious = [];
        return
      }
      console.log("UNKNOWN ACTION", action)
    }
  },
  computed: {
    viewComponent() {
      return this.supportedGame.component
    },
    ...mapState("DslGameState", {
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
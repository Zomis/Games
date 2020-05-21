<template>
  <div class="game">
    <GameHead v-if="gameInfo" :gameInfo="gameInfo" :playerCount="playerCount" :view="view" :eliminations="eliminations" />
    <component v-if="view" :is="viewComponent" :view="view" :actions2="actions2" :onAction="action" :actions="actions" :actionChoice="actionChoice" :players="players" />
    <v-btn v-if="!isObserver" @click="clearActions()" :disabled="actionChoice === null">Reset Action</v-btn>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import { mapState } from "vuex";
import GameHead from "@/components/games/common/GameHead";

export default {
  name: "PlayGame",
  props: ["gameType", "gameId", "showRules"],
  components: {
    GameHead
  },
  data() {
    return {
      supportedGames: supportedGames,
      supportedGame: supportedGames.games[this.gameType],
      views: []
    }
  },
  mounted() {
    console.log("PlayGame mounted")
    this.$store.dispatch('wall').then(() => {
      this.$store.dispatch("DslGameState/joinGame", { gameType: this.gameType, gameId: this.gameId })
    })
  },
  methods: {
    clearActions() {
      this.$store.dispatch("DslGameState/requestView", this.gameInfo);
      this.$store.dispatch("DslGameState/resetActions", { gameInfo: this.gameInfo });
    },
    action(name, data) {
      console.log("ACTION CHOICE", name, data)
      let action = this.actions[name][data]
      console.log("ACTION CHOICE", name, data, action)
      if (action === undefined) {
        console.log("NO ACTION FOR", data)
        this.clearActions();
        return
      }
      if (action.direct) {
        // Perform direct
        this.$store.dispatch("DslGameState/action", { gameInfo: this.gameInfo, name: name, data: action.value });
        return
      }

      this.$store.dispatch("DslGameState/nextAction", { gameInfo: this.gameInfo, name: name, action: action.value });
      return;
    },
    resetActionsTo(actionName, actionValue) {
      this.$store.dispatch("DslGameState/resetActionsTo", { gameInfo: this.gameInfo, name: actionName, action: actionValue });
    },
  },
  computed: {
    viewComponent() {
      return this.supportedGame.component
    },
    isObserver() {
      if (!this.gameInfo) { return true }
      return this.gameInfo.yourIndex < 0;
    },
    actions2() {
      return {
        chosen: this.actionChoice,
        perform: this.action,
        available: this.actions,
        clear: this.clearActions,
        resetTo: this.resetActionsTo
      }
    },
    ...mapState("DslGameState", {
      gameInfo(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameId].gameInfo;
      },
      actionChoice(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameInfo.gameId].gameData.actionChoice;
      },
      eliminations(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameInfo.gameId].gameData.eliminations;
      },
      view(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameInfo.gameId].gameData.view;
      },
      playerCount(state) {
        if (!state.games[this.gameId]) { return 0 }
        return state.games[this.gameInfo.gameId].gameInfo.players.length;
      },
      players(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameInfo.gameId].gameInfo.players;
      },
      actions(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameInfo.gameId].gameData.actions;
      }
    })
  }
}
</script>
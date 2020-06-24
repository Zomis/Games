<template>
  <div class="game">
    <GameHead v-if="gameInfo" :gameInfo="gameInfo" :playerCount="playerCount" :view="view" :eliminations="eliminations" />
    <component v-if="view" :is="viewComponent" :view="view" :actions="actions" :players="players" :context="context" />
    <v-btn v-if="!isObserver" @click="clearActions()" :disabled="actionChoice === null">Reset Action</v-btn>
    <ActionLog :logEntries="actionLogEntries" :onHighlight="highlight" :context="context" />
    <v-snackbar v-model="snackbar">
      {{snackbarText}}
    </v-snackbar>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import { mapState } from "vuex";
import GameHead from "@/components/games/common/GameHead";
import ActionLog from "@/components/games/ActionLog"

export default {
  name: "PlayGame",
  props: ["gameType", "gameId", "showRules"],
  components: {
    GameHead, ActionLog
  },
  data() {
    return {
      supportedGames: supportedGames,
      supportedGame: supportedGames.games[this.gameType],
      views: [],
      snackbar: false,
      snackbarText: 'Welcome'
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
    performChosenAction() {
      this.$store.dispatch("DslGameState/performChosenAction", { gameInfo: this.gameInfo });
    },
    highlight(highlight) {
      this.$store.dispatch("DslGameState/highlight", { gameInfo: this.gameInfo, highlights: highlight });
    },
    action(_, data) {
      let action = this.actionsAvailable[data]
      console.log("ACTION CHOICE", data, action)
      if (action === undefined) {
        console.log("NO ACTION FOR", data)
        this.clearActions();
        return
      }
      let name = action.actionType
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
  watch: {
    gameOver(val) {
      if(val){
        this.snackbarText = 'The game is over';
        this.snackbar = true;
      }
    }
  },
  computed: {
    viewComponent() {
      return this.supportedGame.component
    },
    isObserver() {
      if (!this.gameInfo) { return true }
      return this.gameInfo.yourIndex < 0;
    },
    lastActionLogEntry() {
      return this.actionLogEntries.length > 0 ? this.actionLogEntries[this.actionLogEntries.length - 1] : { parts: [], highlights: {} }
    },
    actions() {
      return {
        chosen: this.actionChoice,
        highlights: this.lastActionLogEntry.highlights,
        perform: this.action,
        performChosen: this.performChosenAction,
        available: this.actionsAvailable,
        actionTypes: this.actionTypes,
        clear: this.clearActions,
        resetTo: this.resetActionsTo
      }
    },
    context() {
      /* TODO:
       Players + add controllable property (true/false) or controllable int *array*.
        Also add eliminated property
       Viewer - Int. (Make it changable in local or when controlling multiple players)
       Scope/Context/View/yadayada: Replay/Game/Local/Lobby...
       Eliminations.
      */
      return {
        players: this.players.map((p, idx) => ({ ...p, controllable: this.gameInfo.yourIndex === idx })),
        gameType: this.gameInfo.gameType,
        gameId: this.gameInfo.gameId,
        viewer: this.gameInfo.yourIndex,
        scope: 'play'
      }
    },
    gameOver() {
      return this.eliminations.length == this.players.length
    },
    ...mapState("DslGameState", {
      actionLogEntries(state) {
        if (!state.games[this.gameId]) { return [] }
        return state.games[this.gameId].actionLog;
      },
      gameInfo(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameId].gameInfo;
      },
      actionChoice(state) {
        if (!state.games[this.gameId]) { return null }
        return state.games[this.gameInfo.gameId].gameData.actionChoice;
      },
      eliminations(state) {
        if (!state.games[this.gameId]) { return [] }
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
        if (!state.games[this.gameId]) { return [] }
        return state.games[this.gameInfo.gameId].gameInfo.players;
      },
      actionTypes(state) {
        if (!state.games[this.gameId]) { return [] }
        return state.games[this.gameInfo.gameId].gameData.actionTypes;
      },
      actionsAvailable(state) {
        if (!state.games[this.gameId]) { return {} }
        return state.games[this.gameInfo.gameId].gameData.actions;
      }
    })
  }
}
</script>
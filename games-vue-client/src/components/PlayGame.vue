<template>
  <div class="game">
    <v-btn
      v-for="(_, playerIndex) in gameInfo.access"
      :key="'switch-player-' + playerIndex"
      @click="switchPlayerIndex(playerIndex)"
    >
      Reset (player {{ playerIndex }})
    </v-btn>
    <component
      :is="viewComponent"
      v-if="view"
      :view="view"
      :actions="actions"
      :context="context"
    />
    <ActionLog
      :log-entries="actionLogEntries"
      :on-highlight="highlight"
      :context="context"
    />
    <v-snackbar v-model="snackbar">
      {{ snackbarText }}
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
  props: ["gameType", "gameId"],
  components: {
    GameHead, ActionLog
  },
  data() {
    return {
      supportedGames: supportedGames,
      supportedGame: supportedGames.games[this.gameType],
      views: [],
      snackbar: false,
      snackbarText: 'Welcome',
      whispered: false,
      audio: new Audio('https://actions.google.com/sounds/v1/cartoon/cartoon_cowbell.ogg')
    }
  },
  mounted() {
    console.log("PlayGame mounted")
    this.$store.dispatch('wall').then(() => {
      this.$store.dispatch("setTitle", this.supportedGames.displayName(this.gameType))
      this.$store.dispatch("DslGameState/joinGame", { gameType: this.gameType, gameId: this.gameId })
    })
  },
  methods: {
    switchPlayerIndex(playerIndex) {
      this.$store.dispatch("DslGameState/switchPlayerIndex", { gameInfo: this.gameInfo, activeIndex: parseInt(playerIndex, 10) });
    },
    performChosenAction() {
      this.$store.dispatch("DslGameState/performChosenAction", { gameInfo: this.gameInfo });
    },
    highlight(highlight) {
      console.log("highlight!!", highlight);
      this.$store.dispatch("DslGameState/highlight", { gameInfo: this.gameInfo, highlights: highlight });
    },
    actionParameter(actionType, serializedParameter) {
      this.$store.dispatch("DslGameState/action", { gameInfo: this.gameInfo, name: actionType, data: serializedParameter });
    },
    actionStep(actionType, choice) {
      this.$store.dispatch("DslGameState/nextAction", { gameInfo: this.gameInfo, name: actionType, action: choice });
    },
    action(_, data) {
      let action = this.actionsAvailable[data]
      console.log("ACTION CHOICE", data, action)
      if (action === undefined) {
        alert("Action undefined. Possibly a bug.");
        console.log("NO ACTION FOR", data)
        //this.clearActions();
        return
      }
      let name = action.actionType
      if (action.parameter) {
        // Perform direct
        this.$store.dispatch("DslGameState/action", { gameInfo: this.gameInfo, name: name, data: action.serialized });
        return
      }

      this.$store.dispatch("DslGameState/nextAction", { gameInfo: this.gameInfo, name: name, action: action.serialized });
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
    },
    actionsAvailable(state) {
      const { playSoundOnPlayerTurn } = localStorage;
      let actionsCount = Object.keys(state).length;
      console.log("actionsAvailable update:", state, "playSound", playSoundOnPlayerTurn, "actionsCount", actionsCount);
      if (!actionsCount) {
        this.whispered = false;
      }
      if (playSoundOnPlayerTurn === 'true' && actionsCount && !this.whispered) {
        this.audio.volume = 0.2;
        this.audio.play();
        this.whispered = true;
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
        actionParameter: this.actionParameter,
        choose: this.actionStep,
        clear: this.clearActions,
        resetTo: this.resetActionsTo
      }
    },
    context() {
      // TODO: Add eliminated property for each player?
      // Determine the access you have to each player (NONE / READ / WRITE / ADMIN)
      let access = this.players.map((_, idx) => this.gameInfo.access[idx] || "NONE");
      return {
        players: this.players.map((p, idx) => ({ ...p, controllable: access[idx] === "WRITE" || access[idx] === "ADMIN", elimination: this.eliminations.find(e => e.player == idx) })),
        gameType: this.gameInfo.gameType,
        gameId: this.gameInfo.gameId,
        viewer: this.gameInfo.activeIndex,
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
        if (!state.games[this.gameId]) { return {} }
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
      actionsAvailable(state) {
        if (!state.games[this.gameId]) { return {} }
        return state.games[this.gameInfo.gameId].gameData.actions;
      }
    })
  }
}
</script>
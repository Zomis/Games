<template>
  <div :class="['game', 'player-' + currentPlayer]">
    <GameHead :gameInfo="gameInfo" :playerCount="playerCount" :view="view" :eliminations="eliminations" />
    <component :is="viewComponent" :view="view" :onAction="action" :actions2="actions2" :actions="actions" :actionChoice="actionChoice" :players="gameInfo.players" />
    <v-btn @click="cancelAction()" :disabled="actionChoice === null">Reset Action</v-btn>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import GameHead from "@/components/games/common/GameHead";
import GameResult from "@/components/games/common/GameResult";
import kotlin from "kotlin"

function valueToJS(value, path) {
//    let constructorName = (typeof value === 'object' && value) ? value.constructor.name : 'not-an-object';
//    console.log(`valueToJS: ${typeof value} ${value} ${constructorName}`)
    if (typeof value === 'number') { return value }
    if (typeof value === 'boolean') { return value }
    if (typeof value === 'string') { return value }
    if (value === null) return null;
    if (value.constructor.name === "ArrayList") return value.toArray().map((e, index) => valueToJS(e, path + '/' + index))
    if (value.constructor.name === "WinResult") return value.toString()
    let results = {}

    if (value.size === 0) {
      return []
    }
    if (value.entries) {
      console.log("entries", value.entries)
      let entries = value.entries.toArray()
      entries.forEach(e => results[e.key] = valueToJS(e.value, path + '/' + e.key))
    } else {
      console.error("Unknown value for valueToJS: ", typeof value, ". Is it perhaps a Kotlin class? Those needs to be replaced with a pure map.", value, path)
    }
    return results
}

let gamejs = supportedGames.gamejs

export default {
  name: "PlayLocalGame",
  props: ["gameInfo", "showRules"],
  components: {
    GameHead,
    GameResult
  },
  data() {
    return {
      supportedGames: supportedGames,
      supportedGame: supportedGames.games[this.gameInfo.gameType],
      actionChoice: null,
      playerCount: 0,
      eliminations: [],
      views: [],
      game: null,
      actions: {},
      view: {},
      viewer: 0
    }
  },
  mounted() {
    let dsl = this.supportedGame.dsl
    let gameSetup = new gamejs.net.zomis.games.dsl.impl.GameSetupImpl(dsl)
    this.game = gameSetup.createGame_vux3hl$(this.gameInfo.players.length, gameSetup.getDefaultConfig())
    this.updateView(0)
  },
  methods: {
    cancelAction() {
      this.actionChoice = null;
      this.updateActions();
    },
    resetActionTo(actionName, actionValue) {
      this.actionChoice = { actionName: actionName, choices: [actionValue] }
      this.updateActions();
    },
    updateView() {
      console.log("UPDATE VIEW", this.viewer)
      let v = this.game.view_s8ev37$(this.viewer)
      this.view = valueToJS(v, '/')
      if (this.view.currentPlayer !== undefined) {
        this.viewer = this.view.currentPlayer
      }
      this.eliminations = this.game.eliminationCallback.eliminations_0.toArray();
      this.updateActions()
    },
    updateActions() {
      console.log("CALLING UPDATE ACTIONS", this.actionChoice, this.viewer, this.view)
      let supportedGame = this.supportedGame
      let actions = {}
      let choices = this.actionChoice ? this.actionChoice.choices : []
      let autoPerform = false
      this.game.actions.types().toArray().forEach(e => {
        if (this.actionChoice && this.actionChoice.actionName !== e.name) {
          return;
        }
        let actionInfo = e.availableParameters_okoyba$(this.viewer, kotlin.kotlin.collections.listOf_i5x0yv$(choices))
        let mappedInfo = {
          nextOptions: actionInfo.nextOptions.size > 0 ? actionInfo.nextOptions.toArray() : [],
          parameters: actionInfo.parameters.size > 0 ? actionInfo.parameters.toArray() : []
        }
        if (this.actionChoice && mappedInfo.nextOptions.length === 0 && mappedInfo.parameters.length === 1) {
          // If we're choosing something, there is only one action to choose, and it is a final step, then perform action
          console.log("AUTO PERFORM", this.actionChoice, mappedInfo.parameters[0])
          this.actionChoice = null
          autoPerform = true

          let gameActionType = this.game.actions.type_61zpoe$(e.name)
          gameActionType.perform_y5fo13$(this.viewer, mappedInfo.parameters[0])
          console.log("AUTO PERFORM WILL UPDATE VIEW")
          this.updateView()
          console.log("AUTO PERFORM DONE")
          return
        }
        actions[e.name] = supportedGames.actionInfo(supportedGame, e.name, mappedInfo, this.actionChoice);
      });
      if (autoPerform) return
      this.actions = actions;
      console.log("ACTIONS FOR", this.viewer, actions)
    },
    action(name, data) {
      if (this.view.winner !== undefined && this.view.winner !== null) { // TODO: Replace with this.game.isGameOver
        console.log("GAME OVER")
        return
      }

      console.log("ACTION CHOICE", name, data)
      let action = this.actions[name][data]
      console.log("ACTION CHOICE", name, data, action)
      if (action === undefined) {
        console.log("NO ACTION FOR", name, data, this.actions)
        return
      }
      if (action.direct) {
        console.log("DIRECT PERFORM")
        let gameActionType = this.game.actions.type_61zpoe$(name)
        gameActionType.perform_y5fo13$(this.viewer, action.value)
        this.updateView()
        return
      }
      if (this.actionChoice !== null && this.actionChoice.actionName === name) {
        this.actionChoice.choices.push(action.value);
      } else {
        this.actionChoice = { actionName: name, choices: [action.value] }
      }
      console.log("LAST UPDATE ACTIONS")
      this.updateActions()
    }
  },
  watch: {
    viewer(newValue, oldValue) {
      if (newValue != oldValue) {
        console.log("VIEWER CHANGING TO", newValue)
        this.updateView()
      }
    }
  },
  computed: {
    actions2() {
      return {
        chosen: this.actionChoice,
        perform: this.action,
        available: this.actions,
        clear: this.cancelAction,
        resetTo: this.resetActionTo
      }
    },
    currentPlayer() {
      return this.view.currentPlayer;
    },
    viewComponent() {
      return this.supportedGame.component
    }
  }
}
</script>
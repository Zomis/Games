<template>
  <div :class="['game', 'player-' + currentPlayer]">
    <component
      :is="viewComponent"
      :view="view"
      :actions="actions"
      :players="gameInfo.players"
      :context="context"
    />
    <v-btn
      :disabled="actionChoice === null"
      @click="cancelAction()"
    >
      Reset Action
    </v-btn>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import GameHead from "@/components/games/common/GameHead";
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
    GameHead
  },
  data() {
    return {
      supportedGames: supportedGames,
      supportedGame: supportedGames.games[this.gameInfo.gameType],
      actionChoice: null,
      eliminations: [],
      views: [],
      game: null,
      actionsAvailable: {},
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
      let actionTypes = []
      let choices = this.actionChoice ? this.actionChoice.choices : []

      let kotlinChosen = kotlin.kotlin.collections.listOf_i5x0yv$(choices)
      let actionInfoKeys
      if (this.actionChoice) {
        let actionType = this.actionChoice.actionName
        let actionTypeImpl = this.game.actions.type_61zpoe$(actionType)
        actionInfoKeys = actionTypeImpl.actionInfoKeys_okoyba$(this.viewer, kotlinChosen)
      } else {
        actionInfoKeys = this.game.actions.allActionInfo_okoyba$(this.viewer, kotlinChosen)
      }
      actionInfoKeys = actionInfoKeys.keys.entries.toArray()
      console.log(actionInfoKeys)

      let actions = {}

      if (this.actionChoice && actionInfoKeys.length === 1 && actionInfoKeys[0].value.size === 1 && actionInfoKeys[0].value.toArray()[0].isParameter) {
        let singleActionData = actionInfoKeys[0].value.toArray()[0]
        // If we're choosing something, there is only one action to choose, and it is a parameter, then perform action
        console.log("AUTO PERFORM", this.actionChoice, singleActionData)
        this.actionChoice = null

        let gameActionType = this.game.actions.type_61zpoe$(singleActionData.actionType)
        gameActionType.perform_y5fo13$(this.viewer, singleActionData.serialized)
        console.log("AUTO PERFORM WILL UPDATE VIEW")
        this.updateView()
        console.log("AUTO PERFORM DONE")
        return
      }
      actionInfoKeys.forEach(actionEntry => {
        actionEntry.value.toArray().forEach(actionData => {
          actions[supportedGames.resolveActionKey(supportedGame, actionData, this.actionChoice)] = actionData
          actionTypes.push(actionData.actionType);
        })
      });
      console.log("UPDATE ACTIONS RESULT", actions)

      this.actionsAvailable = actions;
      this.actionTypes = actionTypes;
      console.log("ACTIONS FOR", this.viewer, actions)
    },
    action(_, data) {
      if (this.game.isGameOver()) {
        console.log("GAME OVER")
        return
      }
      let action = this.actionsAvailable[data]
      console.log("ACTION CHOICE", data, action)
      if (action === undefined) {
        console.log("NO ACTION FOR", name, data, this.actionsAvailable)
        return
      }
      let name = action.actionType
      if (action.direct) {
        console.log("DIRECT PERFORM")
        let gameActionType = this.game.actions.type_61zpoe$(name)
        gameActionType.perform_y5fo13$(this.viewer, action.serialized)
        this.updateView()
        return
      }
      if (this.actionChoice !== null && this.actionChoice.actionName === name) {
        this.actionChoice.choices.push(action.serialized);
      } else {
        this.actionChoice = { actionName: name, choices: [action.serialized] }
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
    playerCount() {
      return this.gameInfo.players.length;
    },
    context() {
      return {
        players: this.gameInfo.players.map(p => ({ ...p, controllable: true, elimination: this.eliminations.find(e => e.playerIndex == p.index) })),
        gameType: this.gameInfo.gameType,
        gameId: "00000000-0000-0000-0000-000localgame",
        viewer: this.gameInfo.yourIndex,
        scope: 'local-play'
      }
    },
    actions() {
      return {
        chosen: this.actionChoice,
        perform: this.action,
        highlights: {},
        available: this.actionsAvailable,
        actionTypes: this.actionTypes,
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
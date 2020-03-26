<template>
  <div :class="['game', 'player-' + currentPlayer]">
    <GameHead :gameInfo="gameInfo" :playerCount="playerCount" :view="view" :eliminations="eliminations" />
    <component :is="viewComponent" :view="view" :onAction="action" :actions="actions" />
    <v-btn @click="cancelAction()" :disabled="actionPrevious.length == 0">Reset Action</v-btn>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import GameHead from "@/components/games/common/GameHead";
import GameResult from "@/components/games/common/GameResult";
import kotlin from "kotlin"

function valueToJS(value) {
//    let constructorName = (typeof value === 'object' && value) ? value.constructor.name : 'not-an-object';
//    console.log(`valueToJS: ${typeof value} ${value} ${constructorName}`)
    if (typeof value === 'number') { return value }
    if (typeof value === 'boolean') { return value }
    if (value === null) return null;
    if (value.constructor.name === "ArrayList") return value.toArray().map(e => valueToJS(e))
    if (value.constructor.name === "WinResult") return value.toString()
    let results = {}
    let entries = value.entries.toArray()
    entries.forEach(e => results[e.key] = valueToJS(e.value))
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
      actionPrevious: [],
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
    let playerCount = 2;
    if (this.gameInfo.gameType === 'Artax') {
      playerCount = 2 + Math.floor(Math.random() * 3);
    }
    this.playerCount = playerCount
    this.game = gameSetup.createGame_vux3hl$(playerCount, gameSetup.getDefaultConfig())
    this.updateView(0)
  },
  methods: {
    cancelAction() {
      this.actionPrevious = [];
      this.updateActions();
    },
    updateView() {
      let v = this.game.view_s8ev37$(this.viewer)
      this.view = valueToJS(v)
      if (this.view.currentPlayer !== undefined) {
        this.viewer = this.view.currentPlayer
      }
      this.eliminations = this.game.eliminationCallback.eliminations_0.toArray();
      this.updateActions()
    },
    resolveActionKey(game, actionName, type, value) {
       let actionKeys = game.actions[actionName]
       if (typeof actionKeys === 'object') {
         actionKeys = actionKeys[type]
       }
       return actionKeys(value)
    },
    updateActions() {
      let actions = {}
      this.game.actions.types().toArray().forEach(e => {
        let ca = {}
        actions[e.name] = ca
        console.log("ACTION INFO FOR", e.name, this.actionPrevious)
        let actionInfo = e.availableParameters_okoyba$(this.viewer, kotlin.kotlin.collections.listOf_i5x0yv$(this.actionPrevious))
        console.log("ACTION INFO", actionInfo)
        if (actionInfo.nextOptions.size > 0) {
        actionInfo.nextOptions.toArray().forEach(next => {
          let key = this.resolveActionKey(this.supportedGame, e.name, "next", next)
          console.log("POSSIBLE NEXT", next, key)
          ca[key] = { next: next }
        })
        }
        if (actionInfo.parameters.size > 0) {
        actionInfo.parameters.toArray().forEach(actionParam => {
          let key = this.resolveActionKey(this.supportedGame, e.name, "parameter", actionParam)
          console.log("POSSIBLE PARAM", actionParam, key)
          ca[key] = { parameter: actionParam }
        })
        }
      })
      this.actions = actions
      console.log("ACTIONS FOR", this.viewer, actions)
    },
    action(name, data) {
      if (this.view.winner !== undefined && this.view.winner !== null) {
        console.log("GAME OVER")
        return
      }

      console.log("ACTION CHOICE", name, data, "PERHAPS THIS SHOULD BE TREATED AS A PERFORM DIRECTLY ?")
      let action = this.actions[name][data]
      if (action === undefined) {
        console.log("NO ACTION FOR", name, data, this.actions)
        return
      }
      if (action.next !== undefined) {
        this.actionPrevious.push(action.next)
        this.updateActions()
        return
      }
      if (action.parameter !== undefined) {
        let gameActionType = this.game.actions.type_61zpoe$(name)
        gameActionType.perform_y5fo13$(this.viewer, action.parameter)
        this.actionPrevious = [];
        this.updateView()
        return
      }
      console.log("UNKNOWN ACTION", action)
    }
  },
  computed: {
    currentPlayer() {
      return this.view.currentPlayer;
    },
    viewComponent() {
      return this.supportedGame.component
    }
  }
}
</script>
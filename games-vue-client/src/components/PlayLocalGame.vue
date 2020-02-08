<template>
  <div :class="['game', 'player-' + currentPlayer]">
    <GameHead :gameInfo="gameInfo"></GameHead>
    <component :is="viewComponent" :view="view" :onAction="action" :actions="actions" />
    <GameResult :gameInfo="gameInfo"></GameResult>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import GameHead from "@/components/games/common/GameHead";
import GameResult from "@/components/games/common/GameResult";
import kotlin from "kotlin"

function valueToJS(value) {
//    console.log(`valueToJS: ${typeof value} ${value}`)
    if (typeof value === 'number') { return value }
    if (value === null) return null;
    if (value.constructor.name === "ArrayList") return value.toArray().map(e => valueToJS(e))
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
    this.game = gameSetup.createGame_s8jyv4$(gameSetup.getDefaultConfig())
    this.updateView(0)
  },
  methods: {
    updateView() {
      let v = this.game.view_s8ev37$(this.viewer)
      this.view = valueToJS(v)
      if (this.view.currentPlayer !== undefined) {
        this.viewer = this.view.currentPlayer
      }
      this.updateActions()
    },
    updateActions() {
      let actions = {}
      this.game.actions.types().toArray().forEach(e => {
        let ca = {}
        actions[e.name] = ca
        e.availableParameters_okoyba$(this.viewer, kotlin.kotlin.collections.emptyList_287e2$()).parameters.toArray().forEach(actionParam => {
          let key = this.supportedGame.actions[e.name](actionParam)
          ca[key] = true
        })
      })
      this.actions = actions
      console.log("ACTIONS FOR", this.viewer, actions)
    },
    action(name, data) {
      console.log("PERFORM ACTION", name, data)
      this.game.actions.type_61zpoe$(name).perform_y5fo13$(this.viewer, data)
      this.updateView()
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
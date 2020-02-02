<template>
  <div class="game">
    <GameHead :gameInfo="gameInfo"></GameHead>
    <component :is="viewComponent" :view="view" :onAction="action" />
    <GameResult :gameInfo="gameInfo"></GameResult>
  </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import GameHead from "@/components/games/common/GameHead";
import GameResult from "@/components/games/common/GameResult";

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
    },
    action(name, data) {
      console.log("ACTION", name, data)
      this.game.actions.type_61zpoe$(name).perform_y5fo13$(this.viewer, data)
      this.updateView()
      if (this.view.currentPlayer !== undefined) {
        this.viewer = this.view.currentPlayer
      }
    }
  },
  computed: {
    viewComponent() {
      return this.supportedGame.component
    }
  }
}
</script>
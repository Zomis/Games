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
    console.log(`valueToJS: ${typeof value} ${value}`)
    if (typeof value === 'number') { return value }
    if (value === null) return null;
    if (value.constructor.name === "ArrayList") return value.toArray().map(e => valueToJS(e))
    let results = {}
    let entries = value.entries.toArray()
    entries.forEach(e => results[e.key] = valueToJS(e.value))
    return results
}

let gamejs = require("../../../games-js/web/games-js");
if (typeof gamejs["games-js"] !== "undefined") {
  // This is needed when doing a production build, but is not used for `npm run dev` locally.
  gamejs = gamejs["games-js"];
}
// UR:
// $vm0.game.actions.type_61zpoe$("roll").perform_y5fo13$(0, null)
// $vm0.game.actions.type_61zpoe$("move").perform_y5fo13$(0, 0)

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
      view: null,
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
      this.updateView()
/*      if (0 == 1) {
        let json = `{ "gameType": "${this.gameInfo.gameType}", "gameId": "${
          this.gameInfo.gameId
        }", "type": "move", "moveType": "${name}", "move": ${JSON.stringify(
          data
        )} }`;
      }*/
    }
  },
  computed: {
    viewComponent() {
      return this.supportedGame.component
    }
  }
}
</script>
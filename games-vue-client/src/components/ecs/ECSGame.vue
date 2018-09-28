<template>
  <div class="game" :class="gameType | lowerCase">
    <GameHead :game="gameType" :gameId="gameId" :players="players"></GameHead>
    <Entity v-if="core" :entity="core" class="core" v-bind="{ game, click }">
    </Entity>
    <GameResult :yourIndex="yourIndex"></GameResult>
  </div>
</template>

<script>
import Socket from "../../socket";
import Entity from "./entity";
import GameHead from "../games/common/GameHead";
import GameResult from "../games/common/GameResult";

var games = require("../../../../games-js/web/games-js");
if (typeof games["games-js"] !== "undefined") {
  // This is needed when doing a production build, but is not used for `npm run dev` locally.
  games = games["games-js"];
}

export default {
  name: "ECSGame",
  props: ["yourIndex", "gameType", "gameId", "players"],
  data() {
    return {
      core: null,
      game: {
        entities: {}
      }
    };
  },
  filters: {
    lowerCase: function(value) {
      return value.toLowerCase();
    }
  },
  created() {
    if (this.yourIndex < 0) {
      Socket.send(
        `{ "type": "observer", "game": "${this.gameType}", "gameId": "${
          this.gameId
        }", "observer": "start" }`
      );
    }
    Socket.$on("type:GameData", this.messageGameData);
    Socket.$on("type:Update", this.messageUpdate);
    Socket.$on("type:IllegalMove", this.messageIllegal);
  },
  beforeDestroy() {
    Socket.$off("type:GameData", this.messageGameData);
    Socket.$off("type:Update", this.messageUpdate);
    Socket.$off("type:IllegalMove", this.messageIllegal);
  },
  components: {
    GameHead,
    GameResult,
    Entity
  },
  methods: {
    doNothing: function() {},
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `{ "game": "${this.gameType}", "gameId": "${
          this.gameId
        }", "type": "move", "moveType": "${name}", "move": ${data} }`;
        Socket.send(json);
      } else {
        console.log(
          "Before Action: " + name + ":" + data + " - " + this.ur.toString()
        );
      }
    },
    click: function(entity) {
      console.log("OnClick in ECSGame: " + entity.id);
      if (entity.actionable && Socket.isConnected()) {
        let performer = "";
        if (this.core.players[this.yourIndex]) {
          performer = this.core.players[this.yourIndex].id;
        }
        let json = `{ "game": "${this.gameType}", "gameId": "${
          this.gameId
        }", "type": "action", "performer": "${performer}", "action": "${
          entity.id
        }" }`;
        Socket.send(json);
      }
    },
    messageEliminated(e) {
      console.log(`Recieved eliminated: ${JSON.stringify(e)}`);
      if (this.yourIndex == e.player) {
        this.gameOverMessage = e;
      }
    },
    messageGameData(data) {
      console.log(`Recieved data: ${JSON.stringify(data)}`);
      this.core = data.game;
    },
    messageUpdate(updateInfo) {
      console.log(`MessageUpdate: ${JSON.stringify(updateInfo)}`);
      this.game.entities[updateInfo.id][updateInfo.component.type] =
        updateInfo.component.value;
    },
    messageIllegal(e) {
      console.log("IllegalMove: " + JSON.stringify(e));
    }
  },
  computed: {
    playerVs: function() {
      if (typeof this.players !== "object") {
        return "local game";
      }
      return this.players[0] + " vs. " + this.players[1];
    }
  }
};
</script>

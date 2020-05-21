<template>
  <div class="game" :class="gameType | lowerCase">
    <GameHead :gameInfo="gameInfo"></GameHead>
    <Entity v-if="core" :entity="core" class="core" v-bind="{ game, click }">
    </Entity>
  </div>
</template>

<script>
import Socket from "../../socket";
import Entity from "./Entity";
import GameHead from "../games/common/GameHead";

var games = require("../../../../games-js/web/games-js");
if (typeof games["games-js"] !== "undefined") {
  // This is needed when doing a production build, but is not used for `npm run dev` locally.
  games = games["games-js"];
}

export default {
  name: "ECSGame",
  props: ["gameInfo"],
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
    if (this.gameInfo.yourIndex < 0) {
      Socket.send(
        `{ "type": "observer", "game": "${
          this.gameInfo.gameType
        }", "gameId": "${this.gameInfo.gameId}", "observer": "start" }`
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
    Entity
  },
  methods: {
    doNothing: function() {},
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `{ "game": "${this.gameInfo.gameType}", "gameId": "${
          this.gameInfo.gameId
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
        if (this.core.players[this.gameInfo.yourIndex]) {
          performer = this.core.players[this.gameInfo.yourIndex].id;
        }
        let json = `{ "gameType": "${this.gameInfo.gameType}", "gameId": "${
          this.gameInfo.gameId
        }", "type": "action", "performer": "${performer}", "action": "${
          entity.id
        }" }`;
        Socket.send(json);
      }
    },
    messageEliminated(e) {
      console.log(`Recieved eliminated: ${JSON.stringify(e)}`);
      if (this.gameInfo.yourIndex == e.player) {
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
  }
};
</script>

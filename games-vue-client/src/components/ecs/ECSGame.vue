<template>
  <div class="game" :class="gameType | lowerCase">
    <GameHead :game="gameType" :gameId="gameId" :players="players"></GameHead>
    <Entity :entity="core" class="core">
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
      entities: {},
      core: {}
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
    onClick: function(entity) {
      console.log("OnClick in ECSGame: " + entity.id);
      this.action("click", entity.id);
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

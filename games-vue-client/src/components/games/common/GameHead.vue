<template>
  <v-card>
    <v-card-title>
      <h1>{{ gameDisplayName }}</h1>
    </v-card-title>
    <v-card-text>
          <template v-for="(player, playerIndex) in players">
            <span v-if="playerIndex > 0 && displayStyle === 'vs'" :key="'span' + playerIndex">
              vs.
            </span>
            <PlayerInGameInfo :key="'playerInfo-' + playerIndex"
             :player="player" :view="view"
             :eliminations="eliminations"
             :displayStyle="displayStyle"
            />
          </template>
    </v-card-text>
  </v-card>
</template>
<script>
import supportedGames from "@/supportedGames"
import PlayerInGameInfo from "./PlayerInGameInfo"
import md5 from 'md5';

export default {
  name: "GameHead",
  props: ["gameInfo", "playerCount", "view", "eliminations"],
  components: {
    PlayerInGameInfo
  },
  computed: {
    gameDisplayName() {
      let game = supportedGames.games[this.gameInfo.gameType]
      return game.displayName ? game.displayName : this.gameType.gameType
    },
    isLocalGame() {
      return (typeof this.gameInfo.players !== "object");
    },
    displayStyle() {
      if (this.playerCount <= 2) {
        return "vs";
      }
      if (this.playerCount <= 3) {
        // For testing avatars as well, later I think this could work with up to 5 players
        return "table";
      }
      return "avatars";
    },
    players() {
      if (this.isLocalGame) {
        return new Array(this.playerCount).fill(0).map((_, index) => index).map(i => ({
          index: i,
          name: "Player " + (i + 1),
          picture: `https://www.gravatar.com/avatar/${md5(i)}?s=128&d=identicon`
        }));
      } else {
        return this.gameInfo.players.map((player, i) => ({
          index: i,
          data: player,
          name: player.name,
          picture: `https://www.gravatar.com/avatar/${md5(player.name)}?s=128&d=identicon`
        }));
      }
    }
  }
};
</script>

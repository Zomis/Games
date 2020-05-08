<template>
  <v-card>
    <v-card-title>
      <h1>{{ gameDisplayName }}</h1>
    </v-card-title>
    <v-card-text>
      <v-container>
        <v-row align-content="center" justify="center" dense>
          <template v-for="(player, playerIndex) in players">
            <v-col cols="1" v-if="playerIndex > 0 && displayStyle === 'vs'" :key="'col-' + playerIndex">
              vs.
            </v-col>
            <PlayerInGameInfo :key="'player-in-game-' + playerIndex"
             :player="player" :view="view"
             :eliminations="eliminations"
             :displayStyle="displayStyle"
            />
          </template>
        </v-row>
      </v-container>
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
      return this.gameInfo.players.map((player, i) => ({
        index: i,
        id: player.id,
        name: player.name,
        picture: player.picture || `https://www.gravatar.com/avatar/${md5(player.id)}?s=128&d=identicon`
      }));
    }
  }
};
</script>

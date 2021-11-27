<template>
  <div class="king-domino">
      <p>
      Play order {{ view.playOrder }}
      Viewer {{ view.viewer }}
      Current player {{ view.currentPlayer }}
      </p>
      <p>
          {{ view.actions }}
      </p>

    <!-- Choosable tiles -->
    <v-card>
        <v-card-title>Upcoming dominoes</v-card-title>
        <v-card-text>
            <KingDominoPiece v-for="piece in view.dominoNextChoices" :key="piece.value"
                :domino="piece"
                :context="context"
                :show-owner="true"
                :on-click="dominoClick"
            />
        </v-card-text>
    </v-card>

    <!-- Chosen tiles -->
    <v-card>
        <v-card-title>Dominoes</v-card-title>
        <v-card-text>
            <v-btn v-for="(tile, index) in view.actions.place.tile" :key="index" @click="placeTileType(tile)">{{ tile }}</v-btn>
            <KingDominoPiece v-for="piece in view.dominoChoices" :key="piece.value"
                :domino="piece"
                :context="context"
                :show-owner="true"
                :on-click="dominoClick"
            />
        </v-card-text>
    </v-card>

    <v-card v-for="playerIndex in viewOrder" :key="playerIndex">
        <v-card-title>
            <PlayerProfile
              :context="context"
              :playerIndex="playerIndex"
              :size="32"
              show-name
              :post-fix="playerIndex === view.viewer ? '(You)' : ''"
            />
        </v-card-title>
        <v-card-text>
            <Map2D
                :width="view.width" :height="view.height"
                :grid="view.players[playerIndex].grid"
                :click-handler="onClick" :piece-exists="_ => true"
            >
                <template v-slot:default="slotProps">
                    <KingDominoTile :tile="slotProps.tile.tile" v-if="slotProps.tile.tile" />
                </template>
            </Map2D>
            <p>
                {{ view.players[playerIndex].points }} points. Biggest area is {{ view.players[playerIndex].biggestArea }}. {{ view.players[playerIndex].crowns }} crowns.
            </p>
        </v-card-text>
    </v-card>
  </div>
</template>
<script>
import KingDominoPiece from "./KingDominoPiece"
import KingDominoTile from "./KingDominoTile"
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Map2D from "@/components/common/Map2D";

function arrayRotate(arr, count) {
  count -= arr.length * Math.floor(count / arr.length);
  arr.push.apply(arr, arr.splice(0, count));
  return arr;
}

export default {
  name: "KingDomino",
  props: ["view", "actions", "onAction", "context"],
  components: {
    KingDominoPiece,
    KingDominoTile,
    PlayerProfile, Map2D
  },
  methods: {
    placeTileType(type) {
      this.actions.choose(type, "place");
    },
    dominoClick(domino) {
      let dominoSerialized = this.view.actions.dominoOptions[domino.value];
      this.actions.actionParameter("choose", dominoSerialized);
    },
    onClick(x, y) {
      // let actionName = this.view.actionName
      console.log("click", x, y)
      this.actions.choose({x,y}, "place")
      //this.actions.perform(actionName, `${x},${y}`);
    }
  },
  computed: {
    viewOrder() {
      if (!this.view.players) { return [] }
      let arr = [...Array(this.view.players.length).keys()]
      return arrayRotate(arr, this.view.viewer)
    }
  }
};
</script>
<style>
@import "../../../assets/games-style.css";
</style>
